(ns teet.user.user-queries
  (:require [datomic.client.api :as d]
            [teet.db-api.core :as db-api :refer [defquery]]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]))

(defn- find-user-by-name [db name]
  (d/q '[:find (pull ?e [:db/id :user/id :user/given-name :user/family-name :user/email :user/person-id])
         :where
         [?e :user/id _]
         [?e :user/given-name ?given]
         [?e :user/family-name ?family]
         [(str ?given " " ?family) ?full-name]
         [(.toLowerCase ?full-name) ?full-name-lower]
         [(.contains ^String ?full-name-lower ?name)]
         :in $ ?name]
       db (str/lower-case name)))

(defn- find-all-users [db]
  (d/q '[:find (pull ?e [:db/id :user/id :user/given-name :user/family-name :user/email :user/person-id])
         :where
         [(missing? ?e :user/deactivated?)]
         [?e :user/id _]]
       db))

(s/def ::search string?)
(defquery :user/list
  {:doc "List all users"
   :context {db :db}
   :args {search :search}
   :spec (s/keys :opt-un [::search])
   ;; FIXME: can any authenticated user list all other users?
   :project-id nil
   :authorization {}}
  (mapv first
        (if (str/blank? search)
          (find-all-users db)
          (find-user-by-name db search))))
