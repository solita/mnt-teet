(ns teet.ui.breadcrumbs
  (:require [teet.routes :as routes]
            [teet.ui.util :as util]
            [teet.ui.material-ui :refer [Breadcrumbs Link]]))

(defn breadcrumbs
  [breadcrumbs]
  [Breadcrumbs {}
   (util/with-keys
     (for [crumb (butlast breadcrumbs)]
       [Link {:underline "always"
              :href (routes/url-for crumb)}
        (:title crumb)]))
   (when-let [{title :title} (last breadcrumbs)]
     [:span title])])
