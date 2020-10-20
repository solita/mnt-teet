(ns teet.admin.admin-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [datomic.client.api :as d]
            [clojure.string :as str]
            [teet.util.datomic :as du]
            [teet.environment :as environment]
            [clojure.walk :as walk]))

(defquery :admin/list-users
  {:doc "List users who have been given access"
   :context {db :db user :user}
   :args _
   :project-id nil
   :authorization {:admin/add-user {}}}
  {:query '[:find (pull ?e [*
                            {:user/permissions
                             [*
                              {:permission/projects
                               [:thk.project/name
                                :thk.project/id]}]}])
            :where [?e :user/id _]]
   :args [db]
   :result-fn (partial mapv first)})

(defn- ref-attrs [db data]
  (into #{}
        (map first)
        (d/q '[:find ?attr
               :where [?attr :db/valueType :db.type/ref]
               :in $ [?attr ...]]
             db
             (keys (dissoc data :db/id)))))

(defn- linked-from [db id]
  (let [links (group-by :a (d/datoms db {:index :vaet :components [id]}))]
    (into {}
          (keep (fn [[attr datoms]]
                  (let [{:db/keys [ident valueType]}
                        (d/pull db [:db/ident :db/valueType] attr)]
                    (when (= :db.type/ref (:db/ident valueType))
                      [ident
                       (reduce (fn [links datom]
                                 (if (:added datom)
                                   (conj links (:e datom))
                                   (disj links (:e datom))))
                               #{} datoms)]))))
          links)))

(defn ->eid [id]
  (cond
    (str/starts-with? id "project-")
    [:thk.project/id (subs id 8)]

    (str/starts-with? id "user-")
    [:user/person-id (subs id 5)]

    (re-matches #"^\d+$" id)
    (Long/parseLong id)

    :else
    (throw (ex-info "Unrecognized entity id"
                    {:teet/error :unrecognized-entity-id
                     :id id}))))

(defn inspector-enabled? []
  (boolean
   (environment/when-feature :admin-inspector true)))

(def user-info [:user/person-id :user/given-name :user/family-name])

(def user-info-attrs
  #{:meta/creator
    :meta/modifier
    :notification/target
    :task/assignee
    :activity/manager
    :file-seen/user
    :meeting/organizer
    :meeting.agenda/responsible
    :participation/participant
    :review/reviewer
    :thk.project/owner
    :thk.project/manager})

(def extra-attrs
  (into {}
        (for [u user-info-attrs] [u user-info])))

(defn- expand-extra-attrs [db form]
  (walk/prewalk
   (fn [x]
     (if-let [attrs (and (map-entry? x)
                         (extra-attrs (first x)))]
       (let [[k v] x]
         [k
          (merge v
                 (d/pull db attrs (:db/id v)))])
       x))
   form))

(defquery :admin/entity-info
  {:doc "Pull all information about an entity"
   :context {:keys [user db]}
   :args {string-id :id}
   :project-id nil
   :authorization {:admin/inspector {}}
   :pre [^{:error :not-found}
         (inspector-enabled?)]}
  (let [id (:db/id (du/entity db (->eid string-id)))
        entity (d/pull db '[*] id)]
    {:entity (expand-extra-attrs db entity)
     :ref-attrs (ref-attrs db entity)
     :linked-from (linked-from db id)}))
