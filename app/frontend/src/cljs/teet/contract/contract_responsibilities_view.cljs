(ns teet.contract.contract-responsibilities-view
  (:require [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr tr-enum]]
            [herb.core :refer [<class] :as herb]
            [teet.ui.icons :as icons]
            [teet.ui.text-field :refer [TextField]]
            [teet.contract.contract-common :as contract-common]
            [teet.contract.contract-style :as contract-style]
            [teet.ui.material-ui :refer [Grid Checkbox Divider]]
            [teet.ui.typography :as typography]
            [teet.ui.buttons :as buttons]
            [reagent.core :as r]
            teet.contract.contract-spec
            [teet.common.common-controller :as common-controller]
            [teet.contract.contract-partners-controller :as contract-partners-controller]
            [teet.routes :as routes]
            [teet.ui.form :as form]
            [teet.ui.select :as select]
            [teet.ui.common :as common]
            [teet.ui.validation :as validation]
            [teet.authorization.authorization-check :as authorization-check]
            [teet.user.user-model :as user-model]
            [teet.ui.format :as format]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.table :as table]
            [clojure.string :as str]
            [re-svg-icons.feather-icons :as fi]
            [teet.ui.url :as url]
            [teet.contract.contract-model :as contract-model]
            [teet.contract.contract-status :as contract-status]))

(defn pull-tasks [target]
  ["Task is here" "TRAM person" "Company responsible" "Status"])

(defn targets-responsibilities-table
  [targets]
  (cljs.pprint/pprint targets)
  [:div
   [table/simple-table
    [[] [] []]
    (for [target targets
          :let [task? (some? (get-in target [:target :task/type]))]]
      [[(get-in target [:project :thk.project/name])]
       [(tr [:enum (get-in target [:target :activity/name])])]
       [[url/Link
         (merge (:target-navigation-info target)
                {:component-opts {:data-cy "target-responsibility-activity-link"}})
         (tr [:link :target-view-activity])]]])]])

(defn responsibilities-page
  [e! app contract]
  (let [targets (:thk.contract/targets contract)]
    [:div {:class (<class common-styles/flex-column-1)}
     [contract-common/contract-heading e! app contract]
     [:div
      [:div
       (when
         (not-empty targets)
         [:div {:class (<class common-styles/margin-bottom 4)}
          [typography/Heading4 {:class (<class common-styles/margin-bottom 2)}
           (tr [:contract :table-heading :task-responsibilities])]
          [targets-responsibilities-table targets]])
       ]]]))
