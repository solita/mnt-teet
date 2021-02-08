(ns teet.cooperation.cooperation-queries
  (:require [teet.db-api.core :refer [defquery]]
            [teet.cooperation.cooperation-db :as cooperation-db]
            [teet.project.project-db :as project-db]
            [teet.link.link-db :as link-db]
            [teet.util.datomic :as du]
            [datomic.client.api :as d]
            [teet.cooperation.cooperation-model :as cooperation-model]
            [teet.meta.meta-query :as meta-query]))

(defquery :cooperation/overview
  {:doc "Fetch project overview of cooperation: 3rd parties and their latest applications"
   :context {:keys [db user]}
   :args {project-id :thk.project/id}
   :project-id [:thk.project/id project-id]
   :authorization {:cooperation/view-cooperation-page {}}}
  (let [p [:thk.project/id project-id]]
    (meta-query/without-deleted
     db
     (du/idents->keywords
      {:project (project-db/project-by-id db p)
       :overview (cooperation-db/overview db p)}))))

(defquery :cooperation/third-party
  {:doc "Fetches overview plus a given 3rd party and all its applications"
   :context {:keys [db user]}
   :args {project-id :thk.project/id
          teet-id :teet/id :as args}
   :project-id [:thk.project/id project-id]
   :authorization {:cooperation/view-cooperation-page {}}}
  (let [p [:thk.project/id project-id]
        tp-id (cooperation-db/third-party-by-teet-id db teet-id)]
    (meta-query/without-deleted
     db
     (du/idents->keywords
      {:third-party (d/pull db cooperation-model/third-party-display-attrs tp-id)
       :project (project-db/project-by-id db p)
       :overview (cooperation-db/overview db p
                                          #(= tp-id %))}))))

(defquery :cooperation/application
  {:doc "Fetch overview plus a single 3rd party appliation with all information"
   :context {:keys [db user]}
   :args {project-id :thk.project/id
          third-party-teet-id :third-party-teet-id
          application-teet-id :application-teet-id}
   :project-id [:thk.project/id project-id]
   :authorization {:cooperation/view-cooperation-page {}}}
  (let [p [:thk.project/id project-id]
        tp-id (cooperation-db/third-party-by-teet-id db third-party-teet-id)
        app-id (cooperation-db/application-by-teet-id db application-teet-id)]
    (meta-query/without-deleted
     db
     (du/idents->keywords
      {:project (project-db/project-by-id db p)
       :overview (cooperation-db/overview db p)
       :third-party (link-db/fetch-links
                     {:db db
                      :user user
                      :fetch-links-pred? #(contains? % :cooperation.response/status)
                      :return-links-to-deleted? false}
                     (cooperation-db/third-party-with-application db tp-id app-id))
       :related-task (cooperation-db/third-party-application-task db tp-id app-id)}))))
