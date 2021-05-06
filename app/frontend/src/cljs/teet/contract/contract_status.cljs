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

(defn contract-status
  [{:keys [show-label?] :as opts} status]
  (let [tooltip-variant (case status
                          :thk.contract.status/signed
                          :info
                          :thk.contract.status/in-progress
                          :success
                          :thk.contract.status/deadline-approaching
                          :warning
                          :thk.contract.status/deadline-overdue
                          :error
                          :thk.contract.status/warranty
                          :success
                          :thk.contract.status/completed
                          :success)
        component (if show-label?
                    :<>
                    #(common/popper-tooltip {:title (tr [:enum status])
                                             :variant tooltip-variant}
                                            %))]
    [component
     [:div {:class (<class common-styles/flex-row-center)}
      (case status
        :thk.contract.status/signed
        [signed-status-icon]
        :thk.contract.status/in-progress
        [in-progress-status-icon]
        :thk.contract.status/deadline-approaching
        [icons/action-report-problem-outlined {:style {:color theme-colors/warning}}]
        :thk.contract.status/deadline-overdue
        [icons/alert-error-outline {:style {:color theme-colors/error}}]
        :thk.contract.status/warranty
        [icons/content-shield-outlined {:style {:color theme-colors/success}}]
        :thk.contract.status/completed
        [icons/action-check-circle-outlined {:style {:color theme-colors/success}}])
      (when show-label?
        [:span (tr [:enum status])])]]))
