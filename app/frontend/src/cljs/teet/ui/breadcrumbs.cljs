(ns teet.ui.breadcrumbs
  (:require [herb.core :refer [<class]]
            [teet.routes :as routes]
            [teet.ui.util :as util]
            [reagent.core :as r]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [Breadcrumbs Link]]))

(defn breadcrumbs-style
  []
  {:margin-bottom "0.5rem"
   :padding        "1.5rem 1.875rem"})

(defn breadcrumbs
  [breadcrumbs]
  [Breadcrumbs {:class [:breadcrumbs (<class breadcrumbs-style)]
                :separator (r/as-element [icons/navigation-chevron-right {:color :primary :size :small}])}
   (util/with-keys
     (for [crumb (butlast breadcrumbs)]
       [Link {:href (routes/url-for crumb)}
        (:title crumb)]))
   (when-let [{title :title} (last breadcrumbs)]
     [:span title])])
