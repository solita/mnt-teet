(ns teet.contract.contract-view
  (:require [teet.common.common-styles :as common-styles]
            [herb.core :refer [<class]]
            [teet.ui.url :as url]))

(defn link-to-target
  [navigation-info]
  [url/Link navigation-info
   "LINK TO THIS target"])

(defn target-row
  [target]
  [:div
   (when-let [navigation-info (:navigation-info target)]
     [link-to-target navigation-info])
   [:p (pr-str target)]])

(defn target-table
  [targets]
  [:div
   (for [target targets]
     ^{:key (str (:db/id target))}
     [target-row target])])

(defn contract-page
  [e! app {:thk.contract/keys [targets] :as contract}]
  [:div
   [:div {:class (<class common-styles/margin-bottom 1)}
    [:h1 "contract page"]
    [:span (pr-str contract)]]
   [target-table targets]])
