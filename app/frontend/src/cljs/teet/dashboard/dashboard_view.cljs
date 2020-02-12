(ns teet.dashboard.dashboard-view
  (:require [teet.ui.itemlist :as itemlist]
            [teet.user.user-model :as user-model]
            [teet.project.task-model :as task-model]
            [teet.ui.util :as util]
            [teet.ui.typography :as typography]
            [teet.routes :as routes]
            [teet.ui.material-ui :refer [Link Paper]]
            [teet.localization :refer [tr]]))

(defn dashboard-page [e! {user :user :as _app} {:keys [tasks] :as _dashboard} _breadcrumbs]
  (let [tasks-by-project  (group-by #(get-in % [:activity/_tasks 0
                                                :thk.lifecycle/_activities 0
                                                :thk.project/_lifecycles 0]) tasks)]

    [:div {:style {:margin "3rem" :display "flex" :justify-content "center"}}
     [Paper {:style {:flex 1
                     :max-width "800px" :padding "1rem"}}
      [typography/Heading2 (str "Assigned tasks for " (user-model/user-name user))]
      (for [[{:thk.project/keys [id name]} tasks] tasks-by-project]
        ^{:key id}
        [itemlist/ProgressList
         {:title [Link {:href (routes/url-for {:page :project
                                               :params {:project id}
                                               :query nil})} name]}

         (for [t tasks]
           ^{:key (:db/id t)}
           {:status (cond
                      (task-model/completed? t) :success
                      (task-model/rejected? t) :fail
                      (task-model/in-progress? t) :in-progress
                      :else :unknown)
            :link (routes/url-for {:page :activity-task
                                   :params {:project id
                                            :task (str (:db/id t))}
                                   :query nil})
            :name [:span
                   (tr [:enum (-> t :task/type :db/ident)]) ": "
                   (:task/description t)]})])]]))
