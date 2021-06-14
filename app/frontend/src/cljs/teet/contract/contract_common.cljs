(ns teet.contract.contract-common
  (:require [teet.common.common-styles :as common-styles]
            [teet.contract.contract-menu :as contract-menu]
            [teet.ui.typography :as typography]
            [teet.contract.contract-model :as contract-model]
            [teet.ui.common :as common]
            [clojure.string :as str]
            [teet.localization :refer [tr]]
            [herb.core :refer [<class] :as herb]
            [teet.environment :as environment]
            [teet.common.responsivity-styles :as responsivity-styles]
            [teet.contract.contract-status :as contract-status]
            [teet.ui.format :as format]
            [teet.util.euro :as euro]
            [teet.user.user-model :as user-model]))

(defn contract-procurement-link
  [{:thk.contract/keys [procurement-number]}]
  (if (js/Number.isInteger (js/parseInt (subs procurement-number 0 1)))
    [common/external-contract-link
     {:href (str (environment/config-value :contract :state-procurement-url)
              procurement-number "/general-info")}
     (str (tr [:contracts :state-procurement-link]) " " procurement-number)]
    [:span (str (tr [:contracts :state-procurement-link]) " " procurement-number)]))

(defn contract-external-link
  [{:thk.contract/keys [external-link procurement-number]}]
  (when external-link
    [common/external-contract-link {:href external-link}
     (str (tr [:contracts :external-link]) " " procurement-number)]))

(defn contract-thk-link
  [{:thk.contract/keys [procurement-id]}]
  [common/external-contract-link {:href (str (environment/config-value :contract :thk-procurement-url) procurement-id)}
   (str (tr [:contracts :thk-procurement-link]) " " procurement-id)])

(defn contract-external-links
  [contract]
  [:div {:class (herb.core/join (<class common-styles/flex-row)
                                (<class responsivity-styles/visible-desktop-only))}
   [contract-procurement-link contract]
   [contract-external-link contract]
   [contract-thk-link contract]])

(defn contract-heading
  [e! app contract]
  [:div {:class (<class common-styles/margin 0 1 1 1)}
   [:div {:class (<class common-styles/flex-row-space-between)}
    [:div {:class (<class common-styles/flex-row-center)}
     [contract-menu/contract-menu e! app contract]
     [typography/TextBold {:class (<class common-styles/margin-left 0.5)}
      (contract-model/contract-name contract)]]
    [contract-external-links contract]]])

(defn contract-information-row
  [{:thk.contract/keys [type signed-at start-of-work deadline extended-deadline
                        warranty-end-date cost targets] :as contract}]
  [common/basic-information-row
   {:right-align-last? false
    :font-size "0.875rem"}
   [[(tr [:contract :status])
     [contract-status/contract-status {:show-label? true :size 17}
      (:thk.contract/status contract)]]
    (when-let [region (:ta/region contract)]
      [(tr [:fields :ta/region])
       [typography/Paragraph (tr [:enum region])]])
    (when type
      [(tr [:contract :thk.contract/type])
       [typography/Paragraph (tr [:enum type])]])
    (when signed-at
      [(tr [:fields :thk.contract/signed-at])
       [typography/Paragraph (format/date signed-at)]])
    (when start-of-work
      [(tr [:fields :thk.contract/start-of-work])
       [typography/Paragraph (format/date start-of-work)]])
    (when deadline
      [(tr [:fields :thk.contract/deadline])
       [typography/Paragraph (format/date deadline)]])
    (when extended-deadline
      [(tr [:fields :thk.contract/extended-deadline])
       [typography/Paragraph (format/date extended-deadline)]])
    (when warranty-end-date
      [(tr [:contract :thk.contract/warranty-end-date])
       [typography/Paragraph (format/date warranty-end-date)]])
    (when (not-empty (filterv some?
                       (distinct (mapv #(user-model/user-name (:activity/manager %))
                                   targets))))
      [(tr [:contracts :filters :inputs :project-manager])
       [typography/Paragraph (str/join ", " (filter some?
                                              (distinct (mapv #(user-model/user-name (:activity/manager %))
                                                          targets))))]])
    (when cost
      [(tr [:fields :thk.contract/cost])
       [typography/Paragraph (euro/format cost)]])]])
