(ns teet.admin.indexes-view
  "Indexes admin user interface"
  (:require [teet.ui.material-ui :refer [Table TableHead TableBody TableRow TableCell Paper Collapse]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.buttons :as buttons]
            [herb.core :refer [<class]]
            [clojure.string :as str]
            [teet.ui.icons :as icons]
            [teet.localization :refer [tr]]
            [teet.ui.form :as form]
            [teet.ui.format :as format]
            [teet.ui.typography :as typography]
            teet.user.user-spec
            [teet.ui.select :as select]
            [teet.ui.util :refer [mapc]]
            [teet.ui.query :as query]
            [reagent.core :as r]
            [teet.theme.theme-colors :as theme-colors]
            [teet.common.common-styles :as common-styles]
            [teet.ui.common :as common-ui]
            [teet.ui.context :as context]
            [teet.user.user-model :as user-model]
            [teet.util.date :as date]))

(def enabled-indexes
  #{
    {:name "Bitumen index"
     :id "bitumen"
     :type :bitumen-index}
    {
     :name "Consumer Price index"
     :id "consumer"
     :type :consumer-price-index
     }
    })

(defn- is-valid-index-id?
  "Checks if id is defined in the 'enabled-indexes' variable and returns the index"
  [index-id]
  (filterv (fn [x] (if (= (:id x) index-id) x nil)) enabled-indexes)
  )


(defn indexes-selection
  [index-id]
  [:div {:style {:min-width "300px"
                 :margin-left "2rem"
                 :background-color theme-colors/gray-lightest
                 :color theme-colors/black-coral}}
   [:div [typography/Heading1 (str "Indexes")]]
   [:div (str "Bitumen index")]])

(defn indexes-content
  [index-id]
  [:div (str "Content")])

(defn indexes-page
  "The main indexes admin page"
  [e! index-id]
  [:div {:class (<class common-styles/flex-align-center)}
   [indexes-selection index-id]
   [indexes-content index-id]
   ]
  )
