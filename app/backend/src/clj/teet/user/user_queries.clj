(ns teet.user.user-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [teet.user.user-db :as user-db]))

(s/def ::search string?)
(defquery :user/list
  {:doc "List all users"
   :context {db :db}
   :args {search :search}
   :spec (s/keys :opt-un [::search])
   ;; FIXME: can any authenticated user list all other users?
   :allowed-for-all-users? true}
  (mapv first
        (if (str/blank? search)
          (user-db/find-all-users db)
          (user-db/find-user-by-name db search))))
