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
  (let [{:task/keys [type estimated-end-date assignee]
         :meta/keys [deleted? modifier modified-at]} info
        activity (get-in info [:activity/_tasks 0 :db/id])]
    [:span
     [:div
      (if deleted?
        [:<>
         [typography/GreyText
          (tr-enum type)]
         [typography/SmallText
          (tr [:link :target-deleted]
              {:user (user-model/user-name modifier)
               :at (format/date-time modified-at)})]]
        [url/Link
         {:page :activity-task
          :params {:activity activity
                   :task (:db/id to)}}
         (tr-enum type)])]
     [:div
      [typography/SmallGrayText
       (tr [:task :link-info]
           {:assignee (user-model/user-name assignee)
            :deadline (format/date estimated-end-date)})]]]))

(defn- link-wrapper [{:keys [e! from editable?
                             in-progress-atom]}
                     {id :db/id
                      :link/keys [to type] :as link}]
  [:div {:class [(<class common-styles/flex-row-space-between)
                 (<class common-styles/divider-border)]}
   [display link]
   (when editable?
     [IconButton
      {:on-click #(e! (link-controller/->DeleteLink from to type id
                                                    in-progress-atom))}
      [icons/action-delete]])])

(def type-options [:task :cadastral-unit :estate])

(defmulti display-result :link/type)

(defmethod display-result :task [{:task/keys [type assignee estimated-end-date]}]
  [:div {:class (<class common-styles/flex-row-space-between)}
   [:div (tr-enum type)]
   [:div (user-model/user-name assignee)]
   [:div (format/date estimated-end-date)]])

(defmethod display-result :cadastral-unit [u]
  [:div "PALSTA " (pr-str u)])

(defmethod display-result :estate [u]
  [:div "KIINTEISTÖ " (pr-str u)])

(defn links
  "List links to other entities (like tasks).
  Shows input for adding new links.

  If editable? is true, links can be removed and added.
  Otherwise the view is read-only."
  [{:keys [e! links from editable?]}]
  (r/with-let [in-progress (r/atom false)
               selected-type (r/atom (first type-options))
               change-search-value #(reset! selected-type %)
               add-link! (fn [to]
                           (when to
                             (e! (link-controller/->AddLink from (:db/id to) :task in-progress))))]
    [:div.links
     (mapc (r/partial link-wrapper {:e! e!
                                    :from from
                                    :in-progress-atom in-progress
                                    :editable? editable?})
           links)
     (when (and editable? (not @in-progress))
       [:div {:style {:display :flex}}
        [:div {:style {:flex-grow 1}}
         [select/select-search
          {:e! e!
           :placeholder (tr [:link :search :placeholder])
           :no-results (tr [:link :search :no-results])
           :query (fn [text]
                    {:args {:lang @localization/selected-language
                            :text text
                            :from from
                            :types #{@selected-type}}
                     :query :link/search})
           :on-change add-link!

           :format-result display-result}]]
        [:div {:style {:background-color :white
                       :min-width "100px"}}
         [select/form-select {:value {:value @selected-type :label (tr [:link :option-label @selected-type])}
                              :items (doall
                                       (mapv
                                         (fn [opt]
                                           {:value opt :label (tr [:link :option-label opt])})
                                         type-options))
                              :on-change (fn [val]
                                           (change-search-value (:value val)))}]]])]))
