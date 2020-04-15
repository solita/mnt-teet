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
            [teet.navigation.navigation-style :as navigation-style]))

(defn notification-style
  []
  {:display :flex
   :flex-direction :column
   :justify-content :flex-end
   :padding-bottom "0.5rem"})

(defn- notifications* [e! refresh! notifications]
  (r/with-let [selected-item (r/atom nil)
               handle-click! (fn [event]
                               (reset! selected-item (.-currentTarget event)))
               handle-close! (fn []
                               (reset! selected-item nil))]
    [:div {:class (herb/join (<class notification-style)
                             (<class navigation-style/divider-style))}
     [Badge {:badge-content (count notifications)
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
            :get-content-anchor-el nil
            :open (boolean @selected-item)
            :on-close handle-close!}
      (if (seq notifications)
        (mapc (fn [{:notification/keys [type]
                    id :db/id}]
                [MenuItem {:on-click #(do
                                        ;; Navigate to the notification's target
                                        (e! (notification-controller/->NavigateTo id))

                                        ;; Acknowledge the notification
                                        (e! (notification-controller/->Acknowledge
                                             id

                                             ;; After acknowledge, close menu and refresh
                                             (fn []
                                               (handle-close!)
                                               (refresh!)))))}
                 [ListItemIcon
                  (case (:db/ident type)
                    :notification.type/task-waiting-for-review
                    [icons/action-assignment]

                    :notification.type/comment-created
                    [icons/communication-comment]

                    [icons/navigation-more-horiz])]
                 [ListItemText (tr-enum type)]])
              notifications)
        [MenuItem {:on-click handle-close!}
         (tr [:notifications :no-unread-notifications])])]]))

(defn notifications [e!]
  (r/with-let [refresh (r/atom nil)]
    [query/query {:e! e!
                  :query :notification/unread-notifications
                  :args {}
                  :refresh @refresh
                  :simple-view [notifications* e!
                                #(reset! refresh (.getTime (js/Date.)))]
                  :loading-state []
                  :poll-seconds 300}]))
