(ns teet.land.owner-opinion-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            teet.land.owner-opinion-specs
            [teet.db-api.db-api-large-text :as db-api-large-text]
            [teet.meta.meta-model :as meta-model]
            [teet.project.project-db :as project-db]))


(def land-owner-opinion-rich-text-fields
  #{:land-owner-opinion/body :land-owner-opinion/authority-position})

(defn linked-activity-belongs-to-project
  [db activity-id project-id]
  (let [activity-project-id (project-db/activity-project-id db activity-id)]
    (= activity-project-id project-id)))

(defcommand :land-owner-opinion/save-opinion
  {:doc "Save a land owners opinion form"
   :context {user :user
             db :db}
   :payload {form-data :form-data
             project-id :project-id
             land-unit-id :land-unit-id}
   :project-id project-id
   :authorization {:land/save-land-owner-opinions {}}
   :pre [(linked-activity-belongs-to-project db (get-in form-data [:land-owner-opinion/activity :db/id]) project-id)]
   :transact
   (db-api-large-text/store-large-text!
     land-owner-opinion-rich-text-fields
     [(merge
        {:db/id (or (:db/id form-data) "new-land-owner-opinion")}
        (select-keys form-data [:land-owner-opinion/body :land-owner-opinion/type :land-owner-opinion/date
                                :land-owner-opinion/respondent-connection-to-land :land-owner-opinion/authority-position
                                :land-owner-opinion/respondent-name :land-owner-opinion/link-to-response])
        {:land-owner-opinion/activity (get-in form-data [:land-owner-opinion/activity :db/id])
         :land-owner-opinion/project project-id
         :land-owner-opinion/land-unit land-unit-id}
        (if (:db/id form-data)
          (meta-model/modification-meta user)
          (meta-model/creation-meta user)))])})
