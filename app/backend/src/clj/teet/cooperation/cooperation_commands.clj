(ns teet.cooperation.cooperation-commands
  "Commands for cooperation entities"
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.cooperation.cooperation-db :as cooperation-db]
            [teet.meta.meta-model :as meta-model]
            [teet.util.collection :as cu]
            [clojure.spec.alpha :as s]
            [teet.util.datomic :as du]))

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
     (merge (cu/without-nils
              (select-keys response-payload
                           [:cooperation.response/valid-months
                            :cooperation.response/valid-until
                            :cooperation.response/date
                            :cooperation.response/content
                            :cooperation.response/status]))
            {:db/id "new-application-response"}
            (meta-model/creation-meta user))}]})

(s/def ::application-id integer?)
(s/def ::opinion-form (s/keys :req [:cooperation.opinion/status]
                              :opt [:cooperation.opinion/comment
                                    :db/id]))

(defn- opinion-id-matches
  "Check that opinion id matches application's current opinion id.
  Either both are nil or they are the same entity id."
  [db application-id opinion-id]
  (let [application (du/entity db application-id)
        current-opinion-id (get-in application
                                    [:cooperation.application/opinion :db/id])]
    (= opinion-id current-opinion-id)))

(defcommand :cooperation/save-opinion
  {:doc "Save opinion for an application"
   :spec (s/keys :req-un [::application-id
                          ::opinion-form])
   :context {:keys [user db]}
   :payload {:keys [application-id opinion-form]}
   :project-id [:thk.project/id (cooperation-db/application-project-id db application-id)]
   :authorization {:cooperation/application-approval {}}
   :pre [(opinion-id-matches db application-id (:db/id opinion-form))]
   :transact [{:db/id application-id
               :cooperation.application/opinion
               (merge
                {:db/id (or (:db/id opinion-form) "new-opinion")}
                (select-keys opinion-form [:cooperation.opinion/status
                                           :cooperation.opinion/comment])
                (if (:db/id opinion-form)
                  (meta-model/modification-meta user)
                  (meta-model/creation-meta user)))}]})
