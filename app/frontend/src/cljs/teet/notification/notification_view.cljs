(ns teet.notification.notification-view
  (:require [reagent.core :as r]
            [herb.core :as herb :refer [<class]]
            [teet.ui.util :refer [mapc]]
            [teet.ui.material-ui :refer [ListItemText ListItemIcon
                                         Badge IconButton Menu MenuItem]]
            [teet.localization :as localization :refer [tr tr-enum]]
            [teet.notification.notification-controller :as notification-controller]
            [teet.ui.query :as query]
            [teet.ui.icons :as icons]
            [teet.navigation.navigation-style :as navigation-style]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.format :as format]
            [teet.ui.typography :as typography]))

(defn notification-badge-style
  []
  {:display :flex
   :flex-direction :column
   :justify-content :flex-end
   :padding-bottom "0.5rem"})

(defn notification-style
  [read?]
  (with-meta
    {:font-weight (if read?
                    :normal
                    :bold)
     :background-color (if read?
                         theme-colors/white
                         theme-colors/blue-lightest)}
    (when (not read?)
      {:pseudo {:hover {:background-color theme-colors/blue-lighter}}})))

(defn- notification-icon [type]
  (case (:db/ident type)
    :notification.type/task-waiting-for-review
    [icons/action-assignment]

    :notification.type/task-assigned
    [icons/action-assignment-ind]

    :notification.type/comment-created
    [icons/communication-comment]

    :notification.type/project-manager-assigned
    [icons/action-work]

    [icons/navigation-more-horiz]))

(defn notification-menu-style
  []
  {:padding 0
   :max-height "400px"
   :overflow-y :auto})

(defn- notifications* [e! refresh! notifications]
  (r/with-let [selected-item (r/atom nil)
               handle-click! (fn [event]
                               (reset! selected-item (.-currentTarget event)))
               handle-close! (fn []
                               (reset! selected-item nil))]
    [:div {:class (herb/join (<class notification-badge-style)
                             (<class navigation-style/divider-style))}
     [Badge {:badge-content (cu/count-matching
                             #(du/enum= :notification.status/unread
                                        (:notification/status %))
                             notifications)
             :color "error"}
      [IconButton
       {:color "primary"
        :size "small"
        :component "span"
        :on-click handle-click!}
       [icons/social-notifications {:color "primary"}]]]
     [Menu {:anchor-el @selected-item
            :anchor-origin {:vertical :bottom
                            :horizontal :center}
            :classes {"list" (<class notification-menu-style)}
            :get-content-anchor-el nil
            :open (boolean @selected-item)
            :on-close handle-close!}
      (if (seq notifications)
        (mapc (fn [{:notification/keys [type status]
                    :meta/keys [created-at]
                    id :db/id}]
                [MenuItem {:class (<class notification-style (du/enum= status :notification.status/acknowledged))
                           :on-click #(do
                                        ;; Navigate to the notification's target
                                        (e! (notification-controller/->NavigateTo id))
                                        ;; Close the menu
                                        (handle-close!)

                                        ;; Acknowledge the notification, if unread
                                        (when (= :notification.status/unread
                                                 (:db/ident status))
                                          (e! (notification-controller/->Acknowledge
                                               id
                                               ;; Refresh after acknowledge
                                               refresh!))))
                           }
                 [ListItemIcon
                  (notification-icon type)]
                 [ListItemText {:disable-typography true}
                  [:div
                   (tr-enum type)]
                  [:div
                   [typography/SmallText
                    (format/date-time created-at)]]]])
              notifications)
        [MenuItem {:on-click handle-close!}
         (tr [:notifications :no-unread-notifications])])]]))

(defn notifications [e!]
  (r/with-let [refresh (r/atom nil)]
    [query/query {:e! e!
                  :query :notification/user-notifications
                  :args {}
                  :refresh @refresh
                  :simple-view [notifications* e!
                                #(reset! refresh (.getTime (js/Date.)))]
                  :loading-state []
                  :poll-seconds 300}]))
