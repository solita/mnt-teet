(ns teet.link.link-view
  "View for generic linking between entities"
  (:require [teet.ui.select :as select]
            [reagent.core :as r]
            [teet.link.link-controller :as link-controller]
            [teet.localization :as localization :refer [tr tr-enum]]
            [teet.user.user-model :as user-model]
            [teet.ui.format :as format]
            [teet.ui.buttons :as buttons]
            [teet.common.common-styles :as common-styles]
            [teet.theme.theme-colors :as theme-colors]
            [herb.core :refer [<class]]
            [teet.ui.util :refer [mapc]]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [IconButton]]
            [teet.ui.typography :as typography]
            [teet.ui.url :as url]
            [teet.file.file-view :as file-view]
            [teet.file.file-style :as file-style]
            [teet.link.link-style :as link-style]))

(defmulti display (fn [_ link] (:link/type link)))

(defmethod display :default
  [_ link]
  [:div "Unknown link type: " (pr-str link)])


(defmethod display :task
  [_ {:link/keys [info to]}]
  (let [{:task/keys [type estimated-end-date assignee]
         :meta/keys [deleted? modifier modified-at]} info
        activity (get-in info [:activity/_tasks 0 :db/id])]
    [:div {:class [(<class link-style/link-row-heading-line)]}
     [:div {:class [(<class link-style/link-row-icon)]}
      [icons/content-link {:style {:color theme-colors/primary}}]]
     [:div
      [:div
      (if deleted?
        [:<>
         [typography/GreyText
          (tr-enum type)]
         [typography/SmallText
          (tr [:link :target-deleted]
              {:user (user-model/user-name modifier)
               :at (format/date-time modified-at)})]]
        [:div {:style {:display :flex
                       :align-items :center}}
         [url/Link
          {:page :activity-task
           :target "_blank"
           :class (<class file-style/file-list-entity-name-style)
           :params {:activity activity
                    :task (:db/id to)}}
          (tr-enum type)]
         [typography/SmallGrayText
          (str "\u00a0" (tr [:link :type-label :task]))]])]
     [:div
      [typography/SmallGrayText
       (tr [:task :link-info]
           {:assignee (user-model/user-name assignee)
            :deadline (format/date estimated-end-date)})]]]]))

(defmethod display :cadastral-unit
  [_ {:link/keys [info external-id]}]
  (let [{:keys [L_AADRESS TUNNUS]} info
        valid? (:link/valid? info)]
    [:div {:class [(<class link-style/link-row)]}
     [:div {:class [(<class link-style/link-row-heading-line)]}
      [:div {:class [(<class link-style/link-row-icon)]}
       [icons/content-link {:style {:color theme-colors/primary}}]]
      (if valid?
        [url/Link
         {:page :project
          :class (<class file-style/file-list-entity-name-style)
          :query {:tab "land"
                  :unit-id external-id}
          :target "_blank"}
         (str
           L_AADRESS
           " "
           TUNNUS)]
        [:p (str
             L_AADRESS
             " "
             TUNNUS)])
      [typography/SmallGrayText "\u00a0" (tr [:link :type-label :cadastral-unit])]]
     (when (not valid?)
       [typography/SmallGrayText (tr [:link :land-unit-not-in-project])])]))

(defmethod display :estate
  [_ {:link/keys [external-id] :as link}]
  (let [valid? (get-in link [:link/info :link/valid?])]
    [:div {:class [(<class link-style/link-row)]}
     [:div {:class [(<class link-style/link-row-heading-line)]}
      [:div {:class [(<class link-style/link-row-icon)]}
       [icons/content-link {:style {:color theme-colors/primary}}]]
      (if valid?
        [url/Link
         {:page :project
          :class (<class file-style/file-list-entity-name-style)
          :query {:tab "land"
                  :estate-id external-id}
          :target "_blank"}
         external-id]
        [:p external-id])
      [typography/SmallGrayText "\u00a0" (tr [:link :type-label :estate])]]

     (when (not valid?)
       [typography/SmallGrayText (tr [:link :no-units-in-estate-selected])])]))

(defmethod display :file
  [link-entity-opts {file :link/info}]
  [file-view/file-row2 (merge {:comments-link? true
                               :link-icon? true
                               :link-to-new-tab? true
                               :column-widths [10 1]}
                              (when link-entity-opts
                                link-entity-opts))
   file])

(defn- link-wrapper [{:keys [e! from editable?
                             in-progress-atom]}
                     link-entity-opts
                     {id :db/id
                      :link/keys [to type] :as link}]
  [:div {:class [(<class common-styles/flex-row-space-between)]
         :style {:border-top (str "solid " theme-colors/gray-lighter " 2px")}}
   [:div {:class [(<class link-style/link-row-style editable?)]}
    [display (get link-entity-opts type) link]]
   (when editable?
     [:div {:class [(<class link-style/link-row-editable-box)]}
      [buttons/delete-button-with-confirm {:clear? true
                                           :id (str "link-delete-button-" id)
                                           :icon-position :start
                                           :trashcan? true
                                           :action #(e! (link-controller/->DeleteLink from to type id
                                                                                      in-progress-atom))}]])])

(def type-options [:task :file :cadastral-unit :estate])

(defmulti display-result :link/type)

(defmethod display-result :task [{:task/keys [type assignee estimated-end-date]}]
  [:div {:class (<class common-styles/flex-row-space-between)}
   [:div (tr-enum type)]
   [:div (user-model/user-name assignee)]
   [:div (format/date estimated-end-date)]])

(defmethod display-result :cadastral-unit [{:keys [L_AADRESS TUNNUS]}]
  [:div {:class (<class common-styles/flex-row-space-between)}
   [:div L_AADRESS]
   [:div TUNNUS]])

(defmethod display-result :estate [{:keys [KINNISTU]}]
  [:div {:class (<class common-styles/flex-row-space-between)}
   [:div KINNISTU]])

(defmethod display-result :file [file]
  [file-view/file-row2 {:no-link? true
                        :comments-link? false
                        :actions? false} file])

(defn links
  "List links to other entities (like tasks).
  Shows input for adding new links.

  If editable? is true, links can be removed and added.
  Otherwise the view is read-only."
  [{:keys [e! links from editable? link-entity-opts]}]
  (r/with-let [in-progress (r/atom false)
               selected-type (r/atom (first type-options))
               change-search-value #(reset! selected-type %)
               add-link! (fn [to]
                           (when to
                             (e! (link-controller/->AddLink from
                                                            (:db/id to)
                                                            (:link/external-id to)
                                                            @selected-type
                                                            in-progress))))]
    [:div.links {:style {:margin "1rem 0"}}
     (when (seq links)
     [typography/Subtitle1
      {:style {:margin "1rem 0"}}
      (tr [:fields :meeting/links])])
     (mapc (r/partial link-wrapper {:e! e!
                                    :from from
                                    :in-progress-atom in-progress
                                    :editable? editable?}
                      link-entity-opts)
           links)
     (when (and editable? (not @in-progress))
       [:div {:style {:display :flex}}
        [:div {:style {:flex-grow 1}}
         ^{:key (name @selected-type)}                      ; force remount if type changes
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
                       :max-width "150px"}}
         [select/form-select {:value {:value @selected-type
                                      :label (tr [:link :type-label @selected-type])}
                              :items (doall
                                       (mapv
                                         (fn [opt]
                                           {:value opt :label (tr [:link :type-label opt])})
                                         type-options))
                              :on-change (fn [val]
                                           (change-search-value (:value val)))
                              :data-item? true}]]])]))
