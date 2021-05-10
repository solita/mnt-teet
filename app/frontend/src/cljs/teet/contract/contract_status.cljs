(ns teet.contract.contract-status
  (:require [teet.theme.theme-colors :as theme-colors]
            [herb.core :refer [<class] :as herb]
            [teet.ui.icons :as icons]
            [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr]]
            [teet.ui.common :as common]
            [reagent.core :as r]))

(defn status-container-style
  []
  {:width "24px"
   :height "24px"
   :display :flex
   :justify-content :center
   :align-items :center})

(defn signed-icon-style
  []
  {:width "19px"
   :height "19px"
   :border-radius "100%"
   :background-color theme-colors/gray-light})

(defn in-progress-icon-style
  []
  {:width "19px"
   :height "19px"
   :border-radius "100%"
   :border "3px solid"
   :border-color theme-colors/success})

(defn signed-status-icon
  []
  [:div {:class (<class status-container-style)}
   [:div {:class (<class signed-icon-style)}]])

(defn in-progress-status-icon
  []
  [:div {:class (<class status-container-style)}
   [:div {:class (<class in-progress-icon-style)}]])

(defn contract-status->variant-icon
  [contract-status]
  (case contract-status
    :thk.contract.status/signed
    [:info [signed-status-icon]]
    :thk.contract.status/in-progress
    [:info [in-progress-status-icon]]
    :thk.contract.status/deadline-approaching
    [:warning [icons/action-report-problem-outlined {:style {:color theme-colors/warning}}]]
    :thk.contract.status/deadline-overdue
    [:error [icons/alert-error-outline {:style {:color theme-colors/error}}]]
    :thk.contract.status/warranty
    [:info [icons/content-shield-outlined {:style {:color theme-colors/success}}]]
    :thk.contract.status/completed
    [:success [icons/action-check-circle-outlined {:style {:color theme-colors/success}}]]))


(defn contract-status
  [{:keys [show-label?] :as _opts} status]
  (let [[tooltip-variant icon] (contract-status->variant-icon status)
        component (if show-label?
                    :<>
                    #(common/popper-tooltip {:title (tr [:enum status])
                                             :variant tooltip-variant}
                                            %))]
    [component
     [:div {:class (<class common-styles/flex-row-center)}
      icon
      (when show-label?
        [:span {:style {:margin-left "4px"}}
         (tr [:enum status])])]]))
