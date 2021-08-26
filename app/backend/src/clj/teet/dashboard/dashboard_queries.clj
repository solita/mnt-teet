(ns teet.dashboard.dashboard-queries
  (:require [teet.db-api.core :refer [defquery]]
            [datomic.client.api :as d]
            [teet.user.user-model :as user-model]
            [teet.permission.permission-db :as permission-db]
            [teet.notification.notification-db :as notification-db]
            [teet.util.collection :as cu]
            [teet.project.project-model :as project-model]
            [teet.project.task-model :as task-model]
            [teet.meta.meta-query :as meta-query]
            [teet.contract.contract-db :as contract-db]))


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
          {:task (task-model/task-with-status (dissoc task :activity/_tasks))
           :activity-id (:db/id activity)
           :lifecycle-id (:db/id lifecycle)
           :project-id (:db/id project)}))))
   (d/q '[:find (pull ?e [:db/id :task/type :task/group
                          :task/estimated-start-date :task/estimated-end-date
                          :task/actual-start-date :task/actual-end-date
                          :task/assignee
                          :task/status
                          {:activity/_tasks
                           [:db/id
                            {:thk.lifecycle/_activities
                             [:db/id
                              {:thk.project/_lifecycles
                               [:db/id :thk.project/id]}]}]}])
          :where
          [?e :task/assignee ?user]
          [(missing? $ ?e :meta/deleted?)]
          :in $ ?user]
        db (user-model/user-ref user))))

(defn user-projects
  "Fetch all projects the user is involved in.
  Returns all projects where the user is owner or manager of an activity
  or has a project scoped permission."
  [db user project-ids]
  (let [user-ref (user-model/user-ref user)
        owned-projects
        (mapv first
              (d/q '[:find ?e
                     :where
                     [?e :thk.project/owner ?user]
                     :in $ ?user]
                   db user-ref))

        managing-activity-projects
        (mapv first
              (d/q '[:find ?e
                     :where
                     [?e :thk.project/lifecycles ?lc]
                     [?lc :thk.lifecycle/activities ?act]
                     [?act :activity/manager ?user]
                     :in $ ?user]
                   db user-ref))

        permission-projects (map :db/id
                                 (mapcat :permission/projects
                                         (permission-db/user-permissions db user-ref)))
        contract-access-projects (contract-db/projects-the-user-has-access-through-contracts db user-ref)]
    (map
      project-model/project-with-status
      (meta-query/without-deleted
        db
        (mapv first
              (d/q '[:find (pull ?e [:db/id :thk.project/name :thk.project/project-name
                                     :thk.project/id
                                     :thk.project/estimated-start-date :thk.project/estimated-end-date
                                     {:thk.project/owner [:user/id :user/given-name :user/family-name :user/email]}
                                     {:thk.project/manager [:user/id :user/given-name :user/family-name :user/email]}
                                     {:thk.project/lifecycles
                                      [:db/id :thk.lifecycle/type
                                       :thk.lifecycle/estimated-start-date
                                       :thk.lifecycle/estimated-end-date
                                       {:thk.lifecycle/activities
                                        [:db/id
                                         :activity/estimated-end-date
                                         :activity/estimated-start-date
                                         :activity/name
                                         :activity/status
                                         :meta/deleted?
                                         {:activity/tasks [:db/id
                                                           :task/status
                                                           :meta/deleted?
                                                           :task/estimated-end-date]}]}]}])
                     :in $ [?e ...]]
                   db (distinct (concat owned-projects
                                        managing-activity-projects
                                        permission-projects
                                        project-ids
                                        contract-access-projects))))))))


(defn user-dashboard [db user]
  (let [tasks (user-tasks db user)
        tasks-by-activity (group-by :activity-id tasks)
        projects (user-projects db user (map :project-id tasks))
        ;;activities (user-activities db user)
        notifications-by-project
        (notification-db/user-notifications-by-project
         db user (map :db/id projects))]
    (for [{id :db/id :as project} projects]
      {:project (-> project
                    (cu/update-path
                      [:thk.project/lifecycles (constantly true)
                       :thk.lifecycle/activities (constantly true)]
                      (fn [{id :db/id :as activity}]
                        (assoc activity :activity/tasks
                                        (mapv :task (tasks-by-activity id)))))
                    (update :thk.project/lifecycles project-model/sort-lifecycles))
       :notifications (->> id
                           notifications-by-project
                           (map #(dissoc % :notification/project))
                           (sort-by :meta/created-at)
                           reverse
                           vec)})))

(defquery :dashboard/user-dashboard
  {:doc "Get a dashboard for the current user"
   :context {:keys [user db]}
   :args _
   :allowed-for-all-users? true}
  (user-dashboard db user))
