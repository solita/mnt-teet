(ns teet.db-api.core
  "TEET database API multimethods")

(defmulti query-authorization
  "Check authorization for query. Should throw exception on authorization failure.
  Same arguments as query multimethod.

  Default implementation checks that :user is valid in ctx."
  (fn [ctx args] (:query/name ctx)))

(defmulti command-authorization
  "Check authorization for command. Should throw exception on authorization failure.
  Same arguments as command! multimethod.

  Default implementation checks that :user is valid in ctx."
  (fn [ctx args] (:command/name ctx)))

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
  (fn [ctx args] (:query/name ctx)))

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
  (fn [ctx payload] (:command/name ctx)))
