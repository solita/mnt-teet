(ns teet.link.link-queries
  (:require [clojure.string :as str]
            [teet.db-api.core :as db-api :refer [defquery]]
            [datomic.client.api :as d]
            [teet.integration.postgrest :as postgrest]
            [teet.link.link-model :as link-model]
            [teet.localization :refer [with-language tr-enum]]
            [teet.util.string :as string]
            [teet.util.datomic :as du]
            [teet.link.link-db :as link-db]
            [teet.environment :as environment]
            [teet.file.file-db :as file-db]))

(defmulti search-link (fn [_db _user _config _project type _lang _text] type))

(defn search-task [db project lang text]
  (with-language lang
    (let [all-project-tasks
          (d/q '[:find (pull ?t [:task/type :db/id])
                 :where
                 [?p :thk.project/lifecycles ?l]
                 [?l :thk.lifecycle/activities ?a]
                 [?a :activity/tasks ?t]
                 [(missing? $ ?t :meta/deleted?)]
                 :in $ ?p]
               db project)

          matching-project-tasks
          (into []
                (comp
                 (map first)
                 (map #(assoc % :searchable-text (tr-enum (:task/type %))))
                 (filter #(string/contains-words? (:searchable-text %)
                                                  text))
                 (map :db/id))
                all-project-tasks)]

      (mapv
       first
       (d/q '[:find (pull ?t [:db/id :task/type
                              :task/estimated-start-date
                              :task/estimated-end-date
                              {:task/assignee [:user/given-name :user/family-name]}
                              {:activity/_tasks [:activity/name]}])
              :in $ [?t ...]]
            db matching-project-tasks)))))

(defn search-cadastral-unit [db {:keys [api-url api-secret]} project text]
  (let [related-cadastral-unit-ids (-> (d/q '[:find (pull ?p [:thk.project/related-cadastral-units])
                                              :in $ ?p]
                                            db project)
                                       ffirst
                                       :thk.project/related-cadastral-units)]
    (->> (postgrest/rpc {:api-url api-url :api-secret api-secret}
                        :select_feature_properties
                        {:ids related-cadastral-unit-ids
                         :properties ["KINNISTU" "L_AADRESS" "TUNNUS"]})
         (map (fn [[key properties]]
                (assoc properties :link/external-id (name key))))
         (filterv #(or (string/contains-words? (:TUNNUS %) text)
                       (string/contains-words? (:L_AADRESS %) text))))))

(defn search-estate [db {:keys [api-url api-secret]} project text]
  (let [related-cadastral-unit-ids (-> (d/q '[:find (pull ?p [:thk.project/related-cadastral-units])
                                              :in $ ?p]
                                            db project)
                                       ffirst
                                       :thk.project/related-cadastral-units)]
    (->> (postgrest/rpc {:api-url api-url :api-secret api-secret}
                        :select_feature_properties
                        {:ids related-cadastral-unit-ids
                         :properties ["KINNISTU"]})
         vals
         distinct
         (filterv #(and (not (str/blank? (:KINNISTU %)))
                        (string/contains-words? (:KINNISTU %) text)))
         (map #(assoc % :link/external-id (:KINNISTU %))))))

;; Estate id: fetch all units, distinct from properties

(defmethod search-link :task
  [db _user _config project _ lang text]
  (search-task db project lang text))

(defmethod search-link :cadastral-unit
  [db _user config project _ _lang text]
  (search-cadastral-unit db config project text))

(defmethod search-link :estate
  [db _user config project _ _lang text]
  (search-estate db config project text))

(defmethod search-link :file
  [db user _config project _ _lang text]
  (file-db/file-listing
   db user
   (file-db/search-files-in-project db project text)))

(defquery :link/search
  {:doc "Search for items that can be linked"
   :context {:keys [db user]}
   :args {:keys [from  ;; The referring entity
                 text  ;; Free text search
                 lang  ;; Selected session language
                 types ;; Set of link types, example elements: `:task`, `:cadastral-unit`
                 ]}
   :project-id nil
   :authorization {}
   :config {api-url [:api-url]
            api-secret [:auth :jwt-secret]}
   :pre [(link-model/valid-from? from)]}
  (let [existing-links (into #{}
                             (map first)
                             (d/q '[:find ?to
                                    :where
                                    [?link :link/from ?e]
                                    (or [?link :link/external-id ?to]
                                        [?link :link/to ?to])
                                    [(missing? $ ?link :meta/deleted?)]
                                    :in $ ?e]
                                  db (second from)))
        {:keys [path-to-project allowed-link-types]} (link-model/from-types (first from))
        project (get-in (du/entity db (nth from 1)) path-to-project)]
    (if-not project
      (throw (ex-info "Could not resolve project context"
                      {:teet/error :project-not-found}))
      (into []
            (comp
             (mapcat (fn [type]
                       (when (allowed-link-types type)
                         (map #(assoc % :link/type type)
                              (search-link db
                                           user
                                           {:api-url api-url :api-secret api-secret}
                                           project type lang text)))))
             (remove (fn [{id :db/id
                           ext-id :link/external-id}]
                       (existing-links (or id ext-id)))))
            types))))

(defmethod link-db/fetch-external-link-info :cadastral-unit [_user _ id]
  ;; PENDING: this should have a better place?
  (-> (postgrest/rpc (environment/api-context) :select_feature_properties
                     {:ids [id]
                      :properties ["TUNNUS" "L_AADRESS"]})
      vals
      first))

(defmethod link-db/fetch-link-info :file [db user _ file-id]
  (first (file-db/file-listing-info db user [file-id])))
