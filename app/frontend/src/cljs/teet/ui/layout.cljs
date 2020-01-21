(ns teet.ui.layout
  "Layout helper components"
  (:require [teet.ui.util :as util]
            [herb.core :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]))

(defn- flex [{:keys [content-style direction]} content-children]
  (let [content-opts (merge {}
                            (when content-style
                              {:style content-style}))]
    [:div {:style {:display "flex"
                   :flex-direction direction}}
     (map-indexed (fn [i content]
                    ^{:key i}
                    [:div content-opts content])
                  content-children)]))

(defn column
  "Layout content items in a column (top to bottom)."
  [opts & content-children]
  (flex (assoc opts :direction "column") content-children))

(defn row
  "Layout content items in a row (left to right)"
  [opts & content-children]
  (flex (assoc opts :direction "row") content-children))

(defn white-section-spacing
  []
  {:background-color theme-colors/white
   :flex 1})

(def section
  "Section with basic spacing"
  (util/make-component :section {:class (<class white-section-spacing)}))
