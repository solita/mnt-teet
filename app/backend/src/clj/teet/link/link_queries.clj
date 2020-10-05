(ns teet.link.link-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [datomic.client.api :as d]
            [teet.link.link-model :as link-model]
            [teet.localization :refer [with-language tr-enum]]
            [teet.util.string :as string]
            [teet.util.datomic :as du]))

(defmulti search-link (fn [_db _user _project type _lang _text] type))

(defn search-task [db project lang text]
  (with-language lang
    (let [all-project-tasks
          (d/q '[:find (pull ?t [:task/type :db/id])
                 :where
                 [?p :thk.project/lifecycles ?l]
                 [?l :thk.lifecycle/activities ?a]
                 [?a :activity/tasks ?t]
                 [(missing? $ ?t :meta/deleted)]
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

(defmethod search-link :task
  [db _user project _ lang text]
  (search-task db project lang text))

(defquery :link/search
  {:doc "Search for items that can be linked"
   :context {:keys [db user]}
   :args {:keys [from text lang types]}
   :project-id nil
   :authorization {}
   :pre [(link-model/valid-from? from)]}
  (let [{:keys [path-to-project allowed-link-types]} (link-model/from-types (first from))
        project (get-in (du/entity db (nth from 1)) path-to-project)]
    (if-not project
      (throw (ex-info "Could not resolve project context"
                      {:teet/error :project-not-found}))
      (into []
            (mapcat (fn [type]
                      (when (allowed-link-types type)
                        (map #(assoc % :link/type type)
                             (search-link db user project type lang text)))))
            types))))
