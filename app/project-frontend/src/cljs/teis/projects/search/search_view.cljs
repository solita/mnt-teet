(ns teis.projects.search.search-view
  "Quick search of projects and project groups"
  (:require [teis.ui.material-ui :refer [TextField]]
            [teis.localization :refer [tr]]))

(defn quick-search [e! app]
  [:<>
   [TextField {:id "search"
               :label "Pikahaku"
               :placeholder "Pikahaku"}]])
