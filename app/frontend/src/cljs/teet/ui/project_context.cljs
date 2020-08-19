(ns teet.ui.project-context
  (:require [teet.ui.context :as context]))

(defn provide [context child]
  {:pre [(:thk.project/id context)]}
  (context/provide :project-context context child))

(defn consume [component-fn]
  (context/consume :project-context component-fn))
