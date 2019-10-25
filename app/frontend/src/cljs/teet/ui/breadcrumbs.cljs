(ns teet.ui.breadcrumbs
  (:require [teet.routes :as routes]
            [teet.ui.util :as util]
            [teet.ui.material-ui :refer [Breadcrumbs Link]]))

(defn breadcrumbs
  [breadcrumbs]
  [Breadcrumbs {:separator "›"}
   (util/with-keys
     (for [crumb (butlast breadcrumbs)]
       [Link {:href (routes/url-for crumb)}
        (:title crumb)]))
   (when-let [{title :title} (last breadcrumbs)]
     [:span title])])
