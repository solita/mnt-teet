(ns teet.cooperation.cooperation-commands
  "Commands for cooperation entities"
  (:require [teet.db-api.core :as db-api :refer [defcommand tx]]
            [teet.cooperation.cooperation-db :as cooperation-db]
            [teet.cooperation.cooperation-model :as cooperation-model]
            [teet.meta.meta-model :as meta-model]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            [datomic.client.api :as d]
            [teet.link.link-db :as link-db]
            [teet.project.project-db :as project-db]
            [clojure.spec.alpha :as s]
            [teet.cooperation.cooperation-notifications :as cooperation-notifications]
            [teet.activity.activity-db :as activity-db]
            [teet.db-api.db-api-large-text :as db-api-large-text]
            [taoensso.timbre :as log]))

(defn- third-party-is-new-or-belongs-to-project?
  "Check :db/id of new 3rd party form data and check if it is
  new (string) or already belongs to the project."
  [db project-eid {id :db/id :as _third-party}]
  {:pre [(some? id)]}
  (or (string? id)
      (boolean
       (seq
        (d/q '[:find ?tp
               :where [?tp :cooperation.3rd-party/project ?p]
               :in $ ?tp ?p]
             db id project-eid)))))

(defn- third-party-belongs-to-project? [db third-party-teet-id project-eid]
  (seq (d/q '[:find ?tp :in $ ?id ?project
              :where
              [?tp :teet/id ?id]
              [?tp :cooperation.3rd-party/project ?project]]
            db third-party-teet-id project-eid)))

(defcommand :cooperation/save-3rd-party
  {:doc "Create or update a third party in project."
   :context {:keys [user db]}
   :payload {project-id :thk.project/id
             third-party :third-party}
   :project-id [:thk.project/id project-id]
   :pre [(third-party-is-new-or-belongs-to-project? db [:thk.project/id project-id] third-party)]
   :transact
   [(list 'teet.cooperation.cooperation-tx/save-3rd-party
          (meta-model/with-creation-or-modification-meta
            user
            (merge
             (select-keys third-party
                          [:db/id
                           :cooperation.3rd-party/name
                           :cooperation.3rd-party/id-code
                           :cooperation.3rd-party/email
                           :cooperation.3rd-party/phone])
             {:cooperation.3rd-party/project [:thk.project/id project-id]}
             (when (string? (:db/id third-party))
               {:teet/id (java.util.UUID/randomUUID)}))))]})

(defcommand :cooperation/create-application
  {:doc "Create new application in project for the given 3rd party."
   :context {:keys [user db]}
   :payload {project-id :thk.project/id
             third-party-teet-id :third-party-teet-id
             application :application}
   :project-id [:thk.project/id project-id]
   :pre [^{:error :application-outside-activities}
         (cooperation-db/application-matched-activity-id db project-id application)

         (third-party-belongs-to-project? db third-party-teet-id
                                          [:thk.project/id project-id])]}
  (if-let [tp-id (cooperation-db/third-party-by-teet-id db third-party-teet-id)]
    (let [teet-id (java.util.UUID/randomUUID)
          {tempids :tempids}
          (tx [{:db/id tp-id
                :cooperation.3rd-party/applications
                [(merge (select-keys application
                                     [:cooperation.application/type
                                      :cooperation.application/response-type
                                      :cooperation.application/date
                                      :cooperation.application/response-deadline
                                      :cooperation.application/comment])
                        {:db/id "new-application"
                         :teet/id teet-id
                         :cooperation.application/activity (cooperation-db/application-matched-activity-id db project-id application)}
                        (meta-model/creation-meta user))]}])]
      {:tempids tempids
       :third-party-teet-id third-party-teet-id
       :application-teet-id teet-id})
    (db-api/bad-request! "No such 3rd party")))

(defn- application-belongs-to-project? [db application-id thk-project-id]
  (= thk-project-id
     (get-in (du/entity db application-id)
             [:cooperation.3rd-party/_applications 0 :cooperation.3rd-party/project :thk.project/id])))

(defcommand :cooperation/edit-application
  {:doc "Edit a given application in a given project"
   :context {:keys [user db]}
   :payload {project-id :thk.project/id
             application :application}
   :spec (s/keys :req [:thk.project/id]
                 :req-un [::application])
   :project-id [:thk.project/id project-id]
   :pre [^{:error :application-date-cannot-be-changed}
         (cooperation-db/application-has-same-date-as-db? db project-id application)
         (application-belongs-to-project? db (:db/id application) project-id)]
   :transact [(list 'teet.cooperation.cooperation-tx/edit-application user application)]})

(defcommand :cooperation/delete-application
  {:doc "Delete a given application in a given project"
   :context {:keys [user db]}
   :payload {project-id :thk.project/id
             application-id :db/id}
   :spec (s/keys :req [:thk.project/id :db/id])
   :project-id [:thk.project/id project-id]
   :pre [(application-belongs-to-project? db application-id project-id)]
   :transact [(list 'teet.cooperation.cooperation-tx/delete-application user application-id)]})

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
  {:doc "Save an application response form. Will edit existing response or create a new one."
   :context {:keys [user db]}
   :payload {project-id :thk.project/id
             application-id :application-id
             response-payload :form-data}
   :project-id (cooperation-db/application-project-id db application-id)
   :pre [(response-id-matches db application-id (:db/id response-payload))]
   :transact
   (db-api-large-text/store-large-text!
     cooperation-model/rich-text-fields
     (let [existing-id (:db/id response-payload)
           response-id (or existing-id "new-application-response")
           old-response (if existing-id
                          (d/pull db cooperation-model/response-application-keys
                                  existing-id)
                          {:db/id "new-application-response"})
           project-id (cooperation-db/application-project-id db application-id)
           activity-id (cooperation-db/application-activity-id db application-id)
           activity-manager-user-id (activity-db/activity-manager db activity-id)]
       (into [{:db/id application-id
               :cooperation.application/response response-id}]
             (concat (du/modify-entity-tx
                       old-response
                       (merge (cu/without-nils
                                (select-keys response-payload
                                             cooperation-model/response-application-keys))
                              {:db/id response-id}
                              (if (:db/id response-payload)
                                (meta-model/modification-meta user)
                                (meta-model/creation-meta user))))
                     [(cooperation-notifications/application-response-notification-tx db user activity-manager-user-id
                                                                                      project-id application-id cooperation-notifications/new-response-type)]))))})

(defcommand :cooperation/delete-application-response
  {:doc "Delete existing application response."
   :context {:keys [user db]}
   :payload {application-id :application-id}
   :project-id (cooperation-db/application-project-id db application-id)
   :pre [^{:error :application-has-opinion}
         (nil? (:cooperation.application/opinion (du/entity db application-id)))]
   :transact
   (let [response (:cooperation.application/response (du/entity db application-id))
         response-id (:db/id response)
         links (map :db/id (:link/_from response))]
     (into [[:db/retractEntity response-id]]
           (for [l links]
             [:db/retractEntity l])))})

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
   :project-id (cooperation-db/application-project-id db application-id)
   :authorization {:cooperation/edit-opinion {}}
   :contract-authorization {:action :cooperation/manage-opinions-of-competent-authority}
   :pre [(opinion-id-matches db application-id (:db/id opinion-form))]
   :transact
   (db-api-large-text/store-large-text!
     cooperation-model/rich-text-fields
     [{:db/id application-id
       :cooperation.application/opinion
       (merge
         {:db/id (or (:db/id opinion-form) "new-opinion")}
         (select-keys opinion-form [:cooperation.opinion/status
                                    :cooperation.opinion/comment])
         (if (:db/id opinion-form)
           (meta-model/modification-meta user)
           (meta-model/creation-meta user)))}])})

(s/def ::contact-form ::cooperation-model/contact-form)

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
   :project-id (cooperation-db/application-project-id db application-id)
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

(defcommand :cooperation/delete-contact-info
  {:doc "Delete contact info from the application"
   :spec (s/keys :req-un [::application-id])
   :context {:keys [user db]}
   :payload {:keys [application-id]}
   :project-id (cooperation-db/application-project-id db application-id)
   :transact (if-let [contact-id (:db/id
                                   (:cooperation.application/contact
                                     (du/entity db application-id)))]
               [(merge {:db/id application-id}
                       (meta-model/modification-meta user))
                [:db/retractEntity contact-id]]
               (throw (ex-info "Application has no contact info"
                               {:error :no-contact-info})))})

(defcommand :cooperation/delete-third-party
  {:doc "Delete a third party. It must not have any applications."
   :spec (s/keys :req [:db/id])
   :context {:keys [user db]}
   :payload {id :db/id}
   :project-id (cooperation-db/third-party-project-id db id)
   :pre [(not (cooperation-db/has-applications? db id))]
   :transact [(meta-model/deletion-tx user id)]})

(defcommand :cooperation/delete-opinion
  {:doc "Delete opinion from the application"
   :spec (s/keys :req-un [::application-id ::opinion-id])
   :context {:keys [user db]}
   :payload {:keys [application-id opinion-id]}
   :project-id (cooperation-db/application-project-id db application-id)
   :authorization {:cooperation/edit-opinion {}}
   :contract-authorization {:action :cooperation/manage-opinions-of-competent-authority}
   :transact [[:db/retractEntity opinion-id]]})
