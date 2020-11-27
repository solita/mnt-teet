(ns teet.cooperation.cooperation-commands
  "Commands for cooperation entities"
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.cooperation.cooperation-db :as cooperation-db]
            [teet.meta.meta-model :as meta-model]))

(defcommand :cooperation/create-application
  {:doc "Create new application in project for the given 3rd party."
   :context {:keys [user db]}
   :payload {project-id :thk.project/id
             third-party-name :cooperation.3rd-party/name
             application :application}
   :project-id [:thk.project/id project-id]
   :authorization {:cooperation/edit-application {}}
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
                {:db/id "new-application"}
                (meta-model/creation-meta user))]}]
     (db-api/bad-request! "No such 3rd party"))})
