(ns teet.ui.project-context
  (:require [teet.ui.context :as context]))

(defn provide [context child]
  (assert (:thk.project/id context) (str "got context without project id: " (pr-str context)))
  (context/provide :project-context context child))

(defn consume [component-fn]
  (context/consume :project-context component-fn))
