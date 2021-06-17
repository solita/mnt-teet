(ns teet.contract.contract-status
  (:require [teet.theme.theme-colors :as theme-colors]
            [herb.core :refer [<class] :as herb]
            [teet.ui.icons :as icons]
            [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr]]
            [teet.ui.common :as common]
            [reagent.core :as r]))

(defn status-container-style
  [size]
  ^{:combinators
    {[:> :span] {:font-size (str size "px")}}}
  {:width (str size "px")
   :height (str size "px")
   :display :flex
   :justify-content :center
   :align-items :center})

(defn contract-status->variant-icon
  [contract-status]
  (case contract-status
    :thk.contract.status/signed
    [:info [icons/image-circle {:style {:color theme-colors/gray-light}}]]
    :thk.contract.status/in-progress
    [:info [icons/image-circle-outlined {:style {:color theme-colors/success}}]]
    :thk.contract.status/deadline-approaching
    [:warning [icons/action-report-problem-outlined {:style {:color theme-colors/warning}}]]
    :thk.contract.status/deadline-overdue
    [:error [icons/alert-error-outline {:style {:color theme-colors/error}}]]
    :thk.contract.status/warranty
    [:info [icons/content-shield-outlined {:style {:color theme-colors/success}}]]
    :thk.contract.status/completed
    [:success [icons/action-check-circle-outlined {:style {:color theme-colors/success}}]]
    "error"))

(defn contract-status
  [{:keys [show-label? size container-class] :as _opts} status]
  (let [[tooltip-variant icon] (contract-status->variant-icon status)
        component (if show-label?
                    :<>
                    #(common/popper-tooltip {:title (tr [:enum status])
                                             :variant tooltip-variant
                                             :class container-class}
                                            %))]
    [component
     [:div {:class (<class common-styles/flex-row-center)}
      [:div {:class (<class status-container-style (if (number? size) size 19))}
        icon]
      (when show-label?
        [:span {:style {:margin-left "4px"}}
         (tr [:enum status])])]]))
