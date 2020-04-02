(ns teet.authorization.authorization-queries
  "Fetch enumeration values"
  (:require [teet.db-api.core :as db-api :refer [defquery]]))


(defquery :authorization/permissions
  {:doc "Fetch required permissions for commands and queries"
   :args {}
   :unauthenticated? true}
  @db-api/request-permissions)
