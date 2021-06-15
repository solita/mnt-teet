(ns teet.ui.breadcrumbs
  (:require [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.routes :as routes]
            [teet.ui.common :as common]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [Breadcrumbs]]
            [teet.ui.util :as util]))

(defn breadcrumbs-style
  []
  ^{:combinators
    {[:> :ol :li :a] {:font-size "0.875rem"}}}
  {:padding "1rem"
   :font-size "0.875rem"})

(defn separator-style
  []
  {:margin "0 .4rem"})

(defn breadcrumbs
  [breadcrumbs]
  [Breadcrumbs {:class [:breadcrumbs (<class breadcrumbs-style)]
                :classes {:separator (<class separator-style)}
                :separator (r/as-element [icons/navigation-chevron-right {:color :primary :size :small}])}
   (util/with-keys
     (for [crumb (butlast breadcrumbs)]
       (or (:link crumb)
           [common/Link {:href (routes/url-for crumb)}
            (:title crumb)])))
   (when-let [{title :title} (last breadcrumbs)]
     [:span title])])
