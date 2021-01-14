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
            [teet.project.project-db :as project-db]
            [clojure.spec.alpha :as s]))


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
  [db _user [_ response-id] _type to]
  ;; Checks that the uploaded file is in the same project the cooperation response is in
  (= (project-db/file-project-id db to)
     (cooperation-db/response-project-id db response-id)))

(defn- response-id-matches
  "Check that response id matches application's current response id.
  Either both are nil or they are the same entity id."
  [db application-id response-id]
  (let [application (du/entity db application-id)
        current-response-id (get-in application
                                    [:cooperation.application/response :db/id])]
    (= response-id current-response-id)))

(defcommand :cooperation/save-application-response
  {:doc "Create a new response to the application"
   :context {:keys [user db]}
   :payload {project-id :thk.project/id
             application-id :application-id
             response-payload :form-data}
   :project-id (cooperation-db/application-project-id db application-id)
   :authorization {:cooperation/application-approval {}}
   :pre [(response-id-matches db application-id (:db/id response-payload))]
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

(s/def ::contact-form (s/keys :req [:cooperation.contact/name]
                              :opt [:cooperation.contact/company
                                    :cooperation.contact/id-code
                                    :cooperation.contact/email
                                    :cooperation.contact/phone]))

(defn- contact-id-matches
  "Check that contact id matches application's current contact id.
  Either both are nil or they are the same entity id."
  [db application-id opinion-id]
  (let [application (du/entity db application-id)
        current-contact-id (get-in application
                                   [:cooperation.application/contact :db/id])]
    (= opinion-id current-contact-id)))

(defcommand :cooperation/save-contact-info
  {:doc "Save contact info for an application"
   :spec (s/keys :req-un [::application-id
                          ::contact-form])
   :context {:keys [user db]}
   :payload {:keys [application-id contact-form]}
   :project-id [:thk.project/id (cooperation-db/application-project-id db application-id)]
   :authorization {:cooperation/edit-application {}}
   :pre [(contact-id-matches db application-id (:db/id contact-form))]
   :transact [{:db/id application-id
               :cooperation.application/contact
               (merge
                {:db/id (or (:db/id contact-form) "new-contact")}
                (select-keys contact-form [:cooperation.contact/name
                                           :cooperation.contact/company
                                           :cooperation.contact/id-code
                                           :cooperation.contact/email
                                           :cooperation.contact/phone])
                (if (:db/id contact-form)
                  (meta-model/modification-meta user)
                  (meta-model/creation-meta user)))}]})
