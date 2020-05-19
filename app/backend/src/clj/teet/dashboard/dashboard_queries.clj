(ns teet.dashboard.dashboard-queries
  (:require [teet.db-api.core :refer [defquery]]
            [datomic.client.api :as d]
            [teet.meta.meta-query :as meta-query]
            [teet.user.user-model :as user-model]
            [teet.permission.permission-db :as permission-db]
            [teet.notification.notification-db :as notification-db]
            [teet.project.project-db :as project-db]
            [teet.util.collection :as cu]
            [teet.log :as log]))


(defn- user-tasks
  "Fetch all tasks the user is the assignee."
  [db user]
  (into
   []
   (keep
    (fn [[task]]
      (let [activity (get-in task [:activity/_tasks 0])
            lifecycle (get-in activity [:thk.lifecycle/_activities 0])
            project (get-in lifecycle [:thk.project/_lifecycles 0])]
        (when project
          {:task (dissoc task :activity/_tasks)
           :activity-id (:db/id activity)
           :lifecycle-id (:db/id lifecycle)
           :project-id (:db/id project)}))))
   (d/q '[:find (pull ?e [:db/id :task/type :task/group
                          :task/estimated-start-date :task/estimated-end-date
                          :task/actual-start-date :task/actual-end-date
                          :task/status
                          {:activity/_tasks
                           [:db/id
                            {:thk.lifecycle/_activities
                             [:db/id
                              {:thk.project/_lifecycles
                               [:db/id :thk.project/id]}]}]}])
          :where
          [?e :task/assignee ?user]
          [(missing? $ ?e :meta/deleted)]
          :in $ ?user]
        db (user-model/user-ref user))))

(defn- user-activities
  "Fetch all activities the user is involved in."
  [db user]
  ;; FIXME: pull activities the user is managing
  [])

(defn user-projects
  "Fetch all projects the user is involved in.
  Returns all projects where the user is owner or manager or has a project
  scoped permission."
  [db user project-ids]
  (let [user-ref (user-model/user-ref user)
        owner-or-managed-projects
        (mapv first
              (d/q '[:find ?e
                     :where
                     (or [?e :thk.project/owner ?user]
                         [?e :thk.project/manager ?user])]
                   db user-ref))

        permission-projects (map :db/id
                                 (mapcat :permission/projects
                                         (permission-db/user-permissions db user-ref)))]
    (mapv first
          (d/q '[:find (pull ?e [:db/id :thk.project/name :thk.project/project-name
                                 :thk.project/estimated-start-date :thk.project/estimated-end-date
                                 {:thk.project/lifecycles
                                  [:db/id :thk.lifecycle/type
                                   :thk.lifecycle/estimated-start-date
                                   :thk.lifecycle/estimated-end-date
                                   {:thk.lifecycle/activities
                                    [:db/id :activity/type
                                     :activity/estimated-start-date
                                     :activity/estimated-end-date
                                     :activity/actual-start-date
                                     :activity/actual-end-date]}]}])
                 :in $ [?e ...]]
               db (distinct (concat owner-or-managed-projects
                                    permission-projects
                                    project-ids))))))


(defn user-dashboard [db user]
  (let [tasks (user-tasks db user)
        tasks-by-activity (group-by :activity-id tasks)
        projects (user-projects db user (map :project-id tasks))
        ;;activities (user-activities db user)
        notifications-by-project
        (group-by (comp :db/id :notification/project)
                  (notification-db/user-notifications db user 100))]
    (for [{id :db/id :as project} projects]
      (do
        (def p* project)
        {:project (cu/update-path project
                                  [:thk.project/lifecycles (constantly true)
                                   :thk.lifecycle/activities (constantly true)]
                                  (fn [{id :db/id :as activity}]
                                    (assoc activity :activity/tasks
                                           (vec (tasks-by-activity id)))))
         :notifications (mapv #(dissoc % :notification/project)
                              (notifications-by-project id))}))))

(defquery :dashboard/user-dashboard
  {:doc "Get a dashboard for the current user"
   :context {:keys [user db]}
   :args _
   :project-id nil
   :authorization {}}
  (user-dashboard db user))
