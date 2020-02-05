(ns teet.db-api.core
  "TEET database API multimethods"
  (:require [teet.authorization.authorization-check :as authorization-check]
            [teet.permission.permission-db :as permission-db]
            [datomic.client.api :as d]
            [teet.util.collection :as cu]
            [teet.meta.meta-query :as meta-query]))

(defmulti query-authorization
  "Check authorization for query. Should throw exception on authorization failure.
  Same arguments as query multimethod.

  Default implementation checks that :user is valid in ctx."
  (fn [ctx _] (:query/name ctx)))

(defmulti command-authorization
  "Check authorization for command. Should throw exception on authorization failure.
  Same arguments as command! multimethod.

  Default implementation checks that :user is valid in ctx."
  (fn [ctx _] (:command/name ctx)))

(defmethod query-authorization :default [{user :user query :query/name} _]
  (when (nil? user)
    (throw (ex-info "Unauthenticated access not allowed"
                    {:query query}))))

(defmethod command-authorization :default [{user :user command :command/name} _]
  (when (nil? user)
    (throw (ex-info "Unauthenticated access not allowed"
                    {:command command}))))


(defmulti query
  "Execute a given named query.

  If the result is a map and contains `:query`
  and `:args` keys, it is interpreted to be a datomic
  query specification which is run with the datomic `q`
  query function.

  Any other result is returned as is.
  Dispatches on :query/name in ctx.

  ctx is a map containing
  :query/name  the name of the query to run
  :db          the current database value
  :user        the current user"
  (fn [ctx _] (:query/name ctx)))

(defmulti command!
  "Execute a given named command and return the results.
  Commands are expected to side-effect (usually by transacting
  new facts data).

  Commands should return ids for any newly created entities.

  Dispatches on :command/name in ctx.

  ctx is a map containing
  :command/name   name of the comand to execute
  :conn           (datomic connection)
  :user           the current user

  Payload is any data passed to the command.
  The payload is checked against the spec of the command name.
  "
  (fn [ctx _] (:command/name ctx)))

(defmacro fail!
  "Throws ex-info of failed request. `error-map` may contain
  `:msg` exception message, default \"Request failed\"
  `:error` exception error keyword
  `:status` response status code, default 500"
  [error-map]
  `(let [em# (merge {:msg "Request failed"
                     :status 500}
                    ~error-map)]
     (throw (ex-info (:msg em#) (dissoc em# :msg)))))

(defmacro defcommand
  "Define a command.


  Arguments:
  command-name   The namespaced keyword name of the command.
  options        Map of options (see below).
  body           Code that implements the command.

  Options:
  payload        Required binding form for command payload data
  context        Optional binding form for the execution context
                 that always includes: db, user and conn.
  authorization  Required map of authorization rules to check (see below)
  project-id     Required form to determine the project for which
                 user permissions are checked. May use the bindings
                 from payload or context.
  transact       Optional form that generates data to transact to
                 Datomic. If specified, body must be omitted.
                 The command will automatically return the tempids
                 as the result when transact is used.


  Authorization rules:
  Each authorization rule key defines a rule that is in the authorization matrix.
  The value of the rule is an options map that defines the ids of items that
  will be checked of ownership if the authorization matrix requires it.

  By default the permission requires :full access. To override use :permission
  key to specify the access (eg. :read for read-only).

  If command can have access to own items (with :link access type). The :db/id
  must be specified for the entity. The :meta/creator is checked and compared
  to the user id.
  "
  [command-name
   {:keys [payload context authorization project-id transact] :as options}
   & body]
  (assert (and (keyword? command-name)
               (some? (namespace command-name)))
          "Command name must be a namespaced keyword")
  (assert (map? options) "Options must be a map")
  (assert (map? authorization) "Authorization option must be a map")
  (let [authz-rule-names (authorization-check/authorization-rule-names)]
    (doseq [[k _] authorization]
      (assert (authz-rule-names k)
              (str "Unknown authorization rule: " k))))
  (assert (contains? options :project-id) "Specify project id. Use nil to skip project specific roles.")
  (assert (or (and (seq body)
                   (nil? transact))
              (and (some? transact)
                   (empty? body)))
          "Specify :transact that yields tx data or body, not both.")

  (let [-ctx (gensym "CTX")
        -payload (gensym "PAYLOAD")
        -perms (gensym "PERMISSIONS")
        -db (gensym "DB")
        -user (gensym "USER")
        -proj-id (gensym "PID")]
    `(defmethod teet.db-api.core/command! ~command-name [~-ctx ~-payload]
       (let [~(or context -ctx) ~-ctx
             ~(or payload -payload) ~-payload
             ~-db (d/db (:conn ~-ctx))
             ~-user (:user ~-ctx)
             ~-perms (when (:user ~-ctx)
                       (permission-db/user-permissions ~-db [:user/id (:user/id (:user ~-ctx))]))
             ~-proj-id ~project-id]

         ;; Go through the declared authorization requirements
         ;; and try to find user permissions that satisfy them
         (some (fn [[permission# {entity-id# :db/id :as options#}]]
                 (some (fn [{pid# :db/id :permission/keys [role# projects#]}]
                         (let [require# (get options# :permission :full)
                               access#
                               (and (if (seq projects#)
                                      ;; Project specific permission: check it is for this project
                                      (and ~-proj-id
                                           (cu/contains-value? projects# {:db/id ~-proj-id}))

                                      ;; Global permission
                                      true)

                                    ;; Get access defined for authorization rule and role
                                    (authorization-check/access-for permission# role#))]
                           (println "debug, got access: " access#)
                           (or
                            ;; full access is required and this permission gives it
                            (= require# access# :full)

                            ;; read access required and permission gives full or read
                            (and (= require# :read)
                                 (or (= access# :read)
                                     (= access# :full)))

                            ;; link access required check ownership
                            (and (= access# :link)
                                 entity-id#
                                 (meta-query/is-creator? ~-db entity-id# [:user/id (:user/id ~-user)])))))
                       ~-perms))
               ~authorization)

         ~(if transact
            `(select-keys (datomic.client.api/transact (:conn ~-ctx) {:tx-data ~transact})
                          [:tempids])
            `(do ~@body))))))
