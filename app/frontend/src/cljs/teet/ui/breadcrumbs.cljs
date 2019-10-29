(ns teet.ui.breadcrumbs
  (:require [teet.routes :as routes]
            [teet.ui.util :as util]
            [reagent.core :as r]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [Breadcrumbs Link]]))

(defn breadcrumbs
  [breadcrumbs]
  [Breadcrumbs {:separator (r/as-component [icons/navigation-chevron-right {:color :primary :size :small}])}
   (util/with-keys
     (for [crumb (butlast breadcrumbs)]
       [Link {:href (routes/url-for crumb)}
        (:title crumb)]))
   (when-let [{title :title} (last breadcrumbs)]
     [:span title])])
