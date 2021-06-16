(ns teet.user.user-queries
  (:require [datomic.client.api :as d]
            [teet.db-api.core :as db-api :refer [defquery]]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]))

(def user-query-rules
  '[[(user-by-name ?u ?search)
     [?u :user/id _]
     [?u :user/given-name ?given]
     [(missing? $ ?u :user/deactivated?)]
     [?u :user/family-name ?family]
     [(str ?given " " ?family) ?full-name]
     [(.toLowerCase ?full-name) ?full-name-lower]
     [(.contains ^String ?full-name-lower ?search)]]])

(defn- find-user-by-name [db name]
  (d/q '[:find (pull ?u [:db/id :user/id :user/given-name :user/family-name :user/email :user/person-id])
         :where
         (user-by-name ?u ?name)
         :in $ % ?name]
       db
       user-query-rules
       (str/lower-case name)))

(defn- find-all-users [db]
  (d/q '[:find (pull ?e [:db/id :user/id :user/given-name :user/family-name :user/email :user/person-id])
         :where
         [(missing? $ ?e :user/deactivated?)]
         [?e :user/id _]
         [?e :user/family-name _]]
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
