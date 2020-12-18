(ns teet.cooperation.cooperation-commands
  "Commands for cooperation entities"
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.cooperation.cooperation-db :as cooperation-db]
            [teet.cooperation.cooperation-model :as cooperation-model]
            [teet.meta.meta-model :as meta-model]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            [datomic.client.api :as d]
            [teet.link.link-db :as link-db]
            [teet.project.project-db :as project-db]))

(defcommand :cooperation/create-3rd-party
  {:doc "Create a new third party in project."
   :context {:keys [user db]}
   :payload {project-id :thk.project/id
             third-party :third-party}
   :project-id [:thk.project/id project-id]
   :authorization {:cooperation/edit-3rd-party {}}
   :transact
   [(list 'teet.cooperation.cooperation-tx/create-3rd-party
          (merge
           (select-keys third-party
                        [:cooperation.3rd-party/name
                         :cooperation.3rd-party/id-code
                         :cooperation.3rd-party/email
                         :cooperation.3rd-party/phone])
           {:db/id "new-third-party"
            :cooperation.3rd-party/project [:thk.project/id project-id]}))]})

(defcommand :cooperation/create-application
  {:doc "Create new application in project for the given 3rd party."
   :context {:keys [user db]}
   :payload {project-id :thk.project/id
             third-party-name :cooperation.3rd-party/name
             application :application}
   :project-id [:thk.project/id project-id]
   :authorization {:cooperation/edit-application {}}
   :pre [^{:error :application-outside-activities}
         (cooperation-db/application-matched-activity-id db project-id application)]
   :transact
   (if-let [tp-id (cooperation-db/third-party-id-by-name
                    db [:thk.project/id project-id] third-party-name)]
     [{:db/id tp-id
       :cooperation.3rd-party/applications
       [(merge (select-keys application
                            [:cooperation.application/type
                             :cooperation.application/response-type
                             :cooperation.application/date
                             :cooperation.application/response-deadline
                             :cooperation.application/comment])
               {:db/id "new-application"
                :cooperation.application/activity (cooperation-db/application-matched-activity-id db project-id application)}
               (meta-model/creation-meta user))]}]
     (db-api/bad-request! "No such 3rd party"))})


(defmethod link-db/link-from [:cooperation.response :file]
  [db _user from _type to]
  ;; Checks that the uploaded file is in the same project the cooperation response is in
  (= (project-db/file-project-id db to)
     (cooperation-db/response-project-id db from)))

(defcommand :cooperation/save-application-response
  {:doc "Create a new response to the application"
   :context {:keys [user db]}
   :payload {project-id :thk.project/id
             application-id :application-id
             response-payload :form-data}
   :project-id (cooperation-db/application-project-id db application-id)
   :authorization {:cooperation/application-approval {}}
   :pre []                                                  ;; TODO add some checking
   :transact
   (let [existing-id (:db/id response-payload)
         response-id (or existing-id "new-application-response")
         old-response (if existing-id
                        (d/pull db cooperation-model/response-application-keys
                                existing-id)
                        {:db/id "new-application-response"})]
     (into [{:db/id application-id
             :cooperation.application/response response-id}]
           (du/modify-entity-tx
             old-response
             (merge (cu/without-nils
                      (select-keys response-payload
                                   cooperation-model/response-application-keys))
                    {:db/id response-id}
                    (if (:db/id response-payload)
                      (meta-model/modification-meta user)
                      (meta-model/creation-meta user))))))})
