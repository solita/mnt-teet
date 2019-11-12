(ns teet.projects.projects-style
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn row-style
  []
  ^{:pseudo {:hover {:background-color theme-colors/gray-lightest}
             :focus {:outline (str "2px solid " theme-colors/blue-light)}}}
  {:transition "background-color 0.2s ease-in-out"
   :cursor :pointer})
