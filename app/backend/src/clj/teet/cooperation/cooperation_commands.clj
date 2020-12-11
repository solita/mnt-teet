(ns teet.cooperation.cooperation-commands
  "Commands for cooperation entities"
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.cooperation.cooperation-db :as cooperation-db]
            [teet.meta.meta-model :as meta-model]))

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

(defcommand :cooperation/create-application-response
  {:doc "Create a new response to the application"
   :context {:keys [user db]}
   :payload {project-id :thk.project/id
             application-id :application-id
             response-payload :form-data}
   :project-id [:thk.project/id (cooperation-db/application-project-id db application-id)]
   :authorization {:cooperation/application-approval {}}
   :pre []
   :transact
   [{:db/id application-id
     :cooperation.application/response
     (merge (select-keys response-payload
                         [:cooperation.response/valid-months
                          :cooperation.response/valid-until
                          :cooperation.response/date
                          :cooperation.response/content
                          :cooperation.response/status])
            {:db/id "new-application-response"}
            (meta-model/creation-meta user))}]})
