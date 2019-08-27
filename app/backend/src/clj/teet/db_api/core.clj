(ns teet.db-api.core
  "TEET database API multimethods")

(defmulti query
  "Execute a given named query.

  If the result is a map and contains `:query`
  and `:args` keys, it is interpreted to be a datomic
  query specification which is run with the datomic `q`
  query function.

  Any other result is returned as is.
  Dispatches on :query/name."
  (fn [db query] (:query/name query)))

(defmulti command!
  "Execute a given named command and return the results.
  Dispatches on :command/name.")
