(ns teet.snackbar.snackbar-view
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.ui.icons :as icons]
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.ui.material-ui :refer [Button IconButton Snackbar SnackbarContent]]
            [teet.theme.theme-colors :as theme-colors]))

(def snack-color {:success theme-colors/success
                  :error theme-colors/error
                  :warning theme-colors/warning})

(defn message-style
  []
  {:display :flex
   :justify-content :center
   :align-items :center})

(defn icon-style
  []
  {:margin-right "1rem"})

(defn snack-icon
  [variant]
  (condp = variant
    :success
    icons/action-done
    :error
    icons/alert-error-outline
    :warning
    icons/alert-warning
    icons/action-done))

(defn snackbar-container
  [e! {:keys [open? message variant hide-duration action] :as snack}]
  (println "SNACK: " snack)
  [Snackbar {:anchor-origin {:vertical :bottom
                             :horizontal :right}
             :open open?
             :auto-hide-duration hide-duration
             :on-close (e! snackbar-controller/->CloseSnackbar)}
   [SnackbarContent
    {:style {:background-color (snack-color variant)}
     :message (r/as-element [:span
                             {:class (<class message-style)}
                             [(snack-icon variant) {:class (<class icon-style)}]
                             [:span message]])
     :action (r/as-element
              [:<>
               (when-let [{:keys [title event]} action]
                 [Button {:size :small
                          :color :inherit
                          :on-click #(do
                                       (e! event)
                                       (e! (snackbar-controller/->CloseSnackbar)))}
                  title])
               [IconButton {:size :small
                            :color "inherit"
                            :on-click (e! snackbar-controller/->CloseSnackbar)}
                [icons/content-clear]]])}]])
