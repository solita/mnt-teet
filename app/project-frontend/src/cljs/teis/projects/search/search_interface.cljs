(ns teis.projects.search.search-interface
  "Multimethods to hook into the search"
  (:require [teis.ui.icons :as icons]))

(defmulti format-search-result :type)

(defmethod format-search-result :default [result]
  {:icon (icons/alert-warning)
   :text (str "WARNING Unimplemented search result type: " (pr-str result))})
