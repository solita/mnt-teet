(ns teet.admin.admin-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [datomic.client.api :as d]
            [clojure.string :as str]
            [teet.util.datomic :as du]
            [teet.environment :as environment]
            [clojure.walk :as walk]))

(defmulti search-clause (fn [[field _value]] field))

(defmethod search-clause :user/family-name
  [[_ search-param]]
  (when (seq search-param)
    {:where '[[?u :user/family-name ?fname]
              [(.toLowerCase ^String ?fname) ?lower-fname]
              [(.contains ?lower-fname ?fname-search)]]
     :in {'?fname-search (str/lower-case search-param)}}))

(defmethod search-clause :user/given-name
  [[_ search-param]]
  (when (seq search-param)
    {:where '[[?u :user/given-name ?name]
              [(.toLowerCase ^String ?name) ?lower-name]
              [(.contains ?lower-name ?gname-search)]]
     :in {'?gname-search (str/lower-case search-param)}}))

(defmethod search-clause :user/person-id
  [[_ id-code]]
  (when (seq id-code)
    {:in {'?id-code (str/lower-case id-code)}
     :where '[[?u :user/person-id ?code]
              [(.toLowerCase ^String ?code) ?lower-code]
              [(.contains ?lower-code ?id-code)]]}))

(defmethod search-clause :user-group
  [[_ user-group]]
  (if (some? user-group)
    (if (= :deactivated user-group)
      {:where '[[?u :user/deactivated? true]]}

      {:where '[[?u :user/permissions ?p]
                [(missing? $ ?p :permission/projects)]
                [?p :permission/role ?role]
                [(missing? $ ?p :permission/valid-until)]
                [(missing? $ ?u :user/deactivated?)]]
       :in {'?role user-group}})
    {:where '[[(missing? $ ?u :user/deactivated?)]]}))

(defmethod search-clause :project [[_ project-string]]
  ;; Search by free text
  (when (seq project-string)
    {:in {'?project-string (str/lower-case project-string)}
     :where '[[?project :thk.project/id _]
              (or-join [?project ?project-string]
                       (and
                         (or [?project :thk.project/project-name ?name]
                             [?project :thk.project/name ?name])
                         [(.toLowerCase ^String ?name) ?lower-name]
                         [(.contains ?lower-name ?project-string)])
                       [?project :thk.project/id ?project-string])

              [?project :thk.project/lifecycles ?lc]
              (or-join [?u ?project ?lc]
                       (and
                         [?lc :thk.lifecycle/activities ?act]
                         [?act :activity/tasks ?task]
                         [?task :task/assignee ?u])
                       [?project :thk.project/owner ?u]
                       (and
                         [?lc :thk.lifecycle/activities ?act]
                         [?act :activity/manager ?u]))]}))

(defn user-list-query
  "Query users based on search criteria map

  ex:
  Given the criteria {:project \"foobar\"} invoke search-clause matching :project with search-param of foobar
  Return datomic where clauses and in clauses that are added to the final query
   "
  [db criteria]
  (let [{:keys [where in]}
        (reduce (fn [clauses-and-args search]
                  (let [{:keys [where in]} (search-clause search)]
                    (-> clauses-and-args
                        (update :where concat where)
                        (update :in merge in))))
                {:where []
                 :in {}}
                criteria)
        arglist (seq in)
        in (into '[$] (map first) arglist)
        args (into [db] (map second) arglist)]
    (->> (d/q {:query {:find '[(pull ?u [*
                                         {:user/permissions
                                          [*
                                           {:permission/projects
                                            [:thk.project/name
                                             :thk.project/project-name
                                             :thk.project/id]}]}])]
                       :where (into '[[?u :user/id _]] where)
                       :in in}
               :args args})
         (mapv first))))

(defquery :admin/list-users
  {:doc "List users who have been given access"
   :context {db :db user :user}
   :args {payload :payload}
   :project-id nil
   :authorization {:admin/add-user {}}}
  (user-list-query db payload))

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


(defn entity-tx-log
  "Return list in chronological order of transactions that touched the given
  entity specified by entity-id (:db/id).

  Returns information about the transaction (id, time and author info)
  and all the changes to attributes (both added and removed)."
  [db entity-id]
  (->>
   ;; Pull all datoms affecting entity
   (d/q '[:find ?tx ?e ?a ?v ?add
          :where
          [?e ?a ?v ?tx ?add]
          :in $ ?e]
        (d/history db) entity-id)

   ;; Group by transaction
   (group-by first)

   (map
    (fn [[_ tx-changes]]
      (let [tx-id (ffirst tx-changes)]
        ;; Fetch all information added to tx and the user information
        ;; of the :tx/author (if any)
        {:tx (update (d/pull db '[*] tx-id)
                     :tx/author (fn [author]
                                  (when author
                                    (d/pull db user-info [:user/id author]))))

         ;; group all changes by attribute
         :changes (reduce (fn [m [_tx _e a v add]]
                            (let [attr (:db/ident (d/pull db [:db/ident] a))]
                              (update m attr (fnil conj [])
                                      [add v])))
                          {}
                          tx-changes)})))

   ;; Sort transactions by time
   (sort-by (comp :db/txInstant :tx))))

(defquery :admin/entity-history
  {:doc "Pull information about an entity transaction history."
   :context {:keys [user db]}
   :args {string-id :id}
   :project-id nil
   :authorization {:admin/inspector {}}
   :pre [^{:error :not-found}
         (inspector-enabled?)]}
  (let [id (:db/id (du/entity db (->eid string-id)))]
    (entity-tx-log db id)))
