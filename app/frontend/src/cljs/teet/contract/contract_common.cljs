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
            [teet.common.responsivity-styles :as responsivity-styles]))

(defn contract-procurement-link
  [{:thk.contract/keys [procurement-number]}]
  [common/external-contract-link {:href (str (environment/config-value :contract :state-procurement-url) procurement-number)}
   (str/upper-case
     (str (tr [:contracts :state-procurement-link]) " " procurement-number))])

(defn contract-external-link
  [{:thk.contract/keys [external-link procurement-number]}]
  (when external-link
    [common/external-contract-link {:href external-link}
     (str/upper-case
       (str (tr [:contracts :external-link]) " " procurement-number))]))

(defn contract-thk-link
  [{:thk.contract/keys [procurement-id]}]
  [common/external-contract-link {:href (str (environment/config-value :contract :thk-procurement-url) procurement-id)}
   (str/upper-case
     (str (tr [:contracts :thk-procurement-link]) " " procurement-id))])

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
