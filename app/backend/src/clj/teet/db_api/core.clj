(ns teet.db-api.core
  "TEET database API multimethods"
  (:require [teet.authorization.authorization-check :as authorization-check]
            [teet.permission.permission-db :as permission-db]
            [datomic.client.api :as d]
            [teet.meta.meta-query :as meta-query]
            [teet.log :as log]
            [teet.util.collection :as cu]))

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

(defn fail!
  "Throws ex-info of failed request. `error-map` may contain
  `:msg` exception message, default \"Request failed\"
  `:error` exception error keyword
  `:status` response status code, default 500"
  [error-map]
  (let [em (merge {:msg "Request failed"
                   :status 500}
                  error-map)]
    (throw (ex-info (:msg em) (dissoc em :msg)))))

(defn bad-request! [msg]
  (fail! {:status 400
          :error :bad-request
          :msg msg}))

;; Dynamically bound info used in tx to record info about transaction
(def ^:dynamic *request-name* nil)
(def ^:dynamic *request-ctx* nil)

(defn project-id->db-id [db project-id]
  (if (vector? project-id)
    (:db/id (d/pull db '[:db/id :thk.project/id]
                    project-id))
    project-id))

(def request-permissions (atom {}))

(defn- authorization->registrable-permissions [authorization]
  (cu/map-vals #(select-keys % [:link])
               (or authorization
                   {})))

(defn register-permissions! [request-name _request-type permissions]
  (swap! request-permissions
         assoc request-name
         permissions))

(defmacro defrequest*
  "Do not call directly. Use defcommand and defquery."
  [request-type request-name
   {:keys [spec payload args context unauthenticated? authorization project-id transact
           config] :as options}
   & body]

  (assert (or (and unauthenticated?
                   (not (contains? options :authorization))
                   (not (contains? options :project-id)))
              (and (not unauthenticated?)
                   (contains? options :authorization)
                   (contains? options :project-id)))
          "Specify :unauthenticated? true for unauthenticated access, or :project-id and :authorization")

  (assert (string? (:doc options)) "Specify :doc for request")
  (assert (and (keyword? request-name)
               (some? (namespace request-name)))
                          "Request name must be a namespaced keyword")
  (assert (map? options) "Options must be a map")
  (assert (or (not (contains? options :authorization))
              (map? authorization)) "Authorization option must be a map")
  (let [authz-rule-names (authorization-check/authorization-rule-names)]
    (doseq [[k _] authorization]
      (assert (authz-rule-names k)
              (str "Unknown authorization rule: " k))))


  (let [-ctx (gensym "CTX")
        -payload (gensym "PAYLOAD")
        -perms (gensym "PERMISSIONS")
        -db (gensym "DB")
        -user (gensym "USER")
        -proj-id (gensym "PID")
        prepared-permissions (authorization->registrable-permissions authorization)]
    `(do (register-permissions! ~request-name
                                ~request-type
                                ~prepared-permissions)
         ~(when spec
            `(clojure.spec.alpha/def ~request-name ~spec))
         (defmethod ~(case request-type
                       :command 'teet.db-api.core/command!
                       :query 'teet.db-api.core/query)
           ~request-name [~-ctx ~-payload]
           (binding [*request-name* ~request-name
                     *request-ctx* ~-ctx]
             (let [~(or context -ctx) ~-ctx
                   ~(or (case request-type
                          :command payload
                          :query args) -payload) ~-payload
                   ~-db (d/db (:conn ~-ctx))
                   ~-user (:user ~-ctx)
                   ~-perms (when (:user ~-ctx)
                             (permission-db/user-permissions ~-db [:user/id (:user/id (:user ~-ctx))]))
                   ~-proj-id (project-id->db-id ~-db ~project-id)
                   ~@(when config
                       (mapcat (fn [[symbol path]]
                                 [symbol `(teet.environment/config-value ~@path)])
                               config))]

               ;; Check user is logged in
               ~@(when-not unauthenticated?
                   [`(when (nil? ~-user)
                       (throw (ex-info "Unauthenticated access not allowed"
                                       {~request-type ~request-name
                                        :status 401
                                        :error :unauthorized})))

                    ;; Go through the declared authorization requirements
                    ;; and try to find user permissions that satisfy them
                    `(when-not (every? (fn [[functionality# {entity-id# :db/id
                                                             eid# :eid
                                                             access# :access
                                                             link# :link
                                                             :as options#}]]
                                         (authorization-check/authorized?
                                          ~-user functionality#
                                          (merge
                                           (when link#
                                             {:link link#})
                                           {:access access#
                                            :project-id ~-proj-id
                                            :entity (when (or entity-id# eid#)
                                                      (apply meta-query/entity-meta ~-db (or entity-id# eid#)
                                                             (when link#
                                                               [link#])))})))
                                       ~authorization)
                       (log/warn "Failed to authorize command " ~request-name " for user " ~-user)
                       (throw (ex-info "Request authorization failed"
                                       {:status 403
                                        :error :request-authorization-failed})))])

               ;; Go through pre checks
               ~@(for [pre (:pre options)
                       :let [error (or (:error (meta pre)) :request-pre-check-failed)]]
                   `(when-not ~pre
                      (throw (ex-info "Pre check failed"
                                      {:status 400
                                       :msg "Pre check failed"
                                       :error ~error
                                       :pre-check ~(str pre)}))))

               (let [~'%
                     ~(if (and (= :command request-type) transact)
                        `(select-keys (tx ~transact)
                                      [:tempids])
                        `(do ~@body))]

                 ~@(for [post (:post options)]
                     `(when-not ~post
                        (throw (ex-info "Post check failed"
                                        {:status 500
                                         :msg "Internal server error"
                                         :error :request-post-check-failed
                                         :post-check ~(str post)}))))
                 ;; Return result
                 ~'%)))))))

(defmacro defcommand
  "Define a command.


  Arguments:
  command-name   The namespaced keyword name of the command.
  options        Map of options (see below).
  body           Code that implements the command.

  Options:
  :doc            Docstring for the command

  :payload        Required binding form for command payload data

  :context        Optional binding form for the execution context
                  that always includes: db, user and conn.

  :config         Optional binding for configuration values.
                  If provided, it must be a map from local name to vector path
                  in configuration.

  :unauthenticated?
                  If true, allow unauthenticated requests. Skips authorization
                  and project id checks completely.

  :authorization  Required map of authorization rules to check (see below)

  :project-id     Required form to determine the project for which
                  user permissions are checked. May use the bindings
                  from payload or context.

  :pre            Optional vector of pre check forms. Can use bindings
                  from context and payload. If pre checks fail, the request
                  will return a bad request reponse without invoking the
                  body (or transact). If the check form has metadata key
                  :error, then that will be sent to the client, otherwise
                  a generic error code is sent.

  :post           Optional vector of post check forms. In addition to pre
                  check bindings, forms can also use % to refer to the
                  command result value. If post checks fail, the request
                  will return an internal server error response and log
                  an error.

  :transact       Optional form that generates data to transact to
                  Datomic. If specified, body must be omitted.
                  The command will automatically return the tempids
                  as the result when transact is used.


  Authorization rules:
  Each authorization rule key defines a rule that is in the authorization matrix.
  The value of the rule is an options map that defines the :db/id of entity that
  will be checked of ownership if the authorization matrix requires it.

  By default the permission requires :full access. To override use :permission
  key to specify the access (eg. :read for read-only).

  If command can have access to own items (with :link access type). The :db/id
  (or :eid) must be specified for the entity. The map may contain :link keyword which
  specifies which ref attribute is checked against the current user. The link
  attribute defaults to :meta/creator if omitted.
  "
  [command-name
   {:keys [transact] :as options}
   & body]

  (assert (or (and (seq body)
                   (nil? transact))
              (and (some? transact)
                   (empty? body)))
          "Specify :transact that yields tx data or body, not both.")

  `(defrequest* :command ~command-name ~options ~@body))

(defmacro defquery
  "Define a query handler.

  Similar to defcommand, except :transact is not supported and
  instead of payload, the query arguments are bound with :args."

  [query-name {:keys [query args] :as options} & body]
  (assert (or (and (seq body)
                   (nil? query))
              (and (some? query)
                   (empty? body)))
          "Specify :query that defines query or body, not both.")
  (assert (some? args) "Must specify :args binding")
  `(defrequest* :query ~query-name ~options ~@body))

(defn tx
  "Execute Datomic transaction inside defcommand. Automatically adds transaction info to the tx-data.

  tx-data must be a vector of datomic transaction data (maps or vectors).
  More tx data can vectors can be added and will be concatenated to the first.
  Nil entries in more-tx-data will be filtered out, making it more convenient
  to include optional transactions that depend on some condition with `when`.
  "
  [tx-data & more-tx-data]
  (assert (vector? tx-data) "tx-data must be a vector!")
  (let [{:keys [user conn]} *request-ctx*
        command *request-name*]
    (assert *request-name* "tx can only be called within defcommand")
    (log/info "tx  command: " command ", user: " user)
    (d/transact conn {:tx-data (conj (into tx-data
                                           (mapcat #(remove nil? %) more-tx-data))
                                     {:db/id "datomic.tx"
                                      :tx/author (:user/id user)})})))

(defn tx-ret
  "Call tx and return :tempids as command response."
  [& tx-args]
  (select-keys (apply tx tx-args) [:tempids]))


(defn audit
  "Log an audit message with user context"
  [event args]
  (let [user-id (get-in *request-ctx* [:user :user/id])]
    (assert user-id "Audit event requires a user!")
    (log/audit user-id event args)))
