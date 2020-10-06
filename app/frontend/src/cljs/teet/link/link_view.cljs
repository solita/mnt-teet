(ns teet.link.link-view
  "View for generic linking between entities"
  (:require [teet.ui.select :as select]
            [reagent.core :as r]
            [teet.link.link-controller :as link-controller]
            [teet.localization :as localization :refer [tr tr-enum]]
            [teet.user.user-model :as user-model]
            [teet.ui.format :as format]
            [teet.common.common-styles :as common-styles]
            [herb.core :refer [<class]]
            [teet.ui.util :refer [mapc]]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [IconButton]]
            [teet.ui.typography :as typography]
            [teet.ui.url :as url]))

(defmulti display :link/type)

(defmethod display :default
  [link]
  [:div "Unknown link type: " (pr-str link)])


(defmethod display :task
  [{:link/keys [info to]}]
  (let [{:task/keys [type estimated-end-date assignee]} info
        activity (get-in info [:activity/_tasks 0 :db/id])]
    [:span
     [:div [url/Link
            {:page :activity-task
             :params {:activity activity
                      :task (:db/id to)}}
            (tr-enum type)]]
     [:div
      [typography/SmallGrayText
       (tr [:task :link-info]
           {:assignee (user-model/user-name assignee)
            :deadline (format/date estimated-end-date)})]]]))

(defn- link-wrapper [{:keys [e! from in-progress-atom]}
                     {id :db/id
                      :link/keys [to type] :as link}]
  [:div {:class [(<class common-styles/flex-row-space-between)
                 (<class common-styles/divider-border)]}
   [display link]
   [IconButton
    {:on-click #(e! (link-controller/->DeleteLink from to type id
                                                  in-progress-atom))}
    [icons/action-delete]]])

(defn links
  "List links to other entities (like tasks).
  Shows input for adding new links."
  [{:keys [e! links from]}]
  (r/with-let [in-progress (r/atom false)
               add-link! (fn [to]
                           (e! (link-controller/->AddLink from (:db/id to) :task in-progress)))]
    [:div.links
     (mapc (r/partial link-wrapper {:e! e!
                                    :from from
                                    :in-progress-atom in-progress})
           links)
     (when-not @in-progress
       [select/select-search
        {:e! e!
         :placeholder (tr [:link :search :placeholder])
         :no-results (tr [:link :search :no-results])
         :query (fn [text]
                  {:args {:lang @localization/selected-language
                          :text text
                          :from from
                          :types #{:task}}
                   :query :link/search})
         :on-change add-link!

         :format-result (fn [{:task/keys [type assignee estimated-end-date]}]
                          [:div {:class (<class common-styles/flex-row-space-between)}
                           [:div (tr-enum type)]
                           [:div (user-model/user-name assignee)]
                           [:div (format/date estimated-end-date)]])}])]))
