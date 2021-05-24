(ns teet.ui.project-context
  (:require [teet.ui.context :as context]))

(def project-context-keys [:db/id :thk.project/id :integration/id])

(defn provide
  "Provide the project id info as context. This is usually enough."
  [context child]
  (context/provide :project-context (select-keys context project-context-keys) child))

(defn provide-full
  "Provide the full project object. If some descendant needs the full project info, use this."
  [context child]
  (context/provide :project-context context child))

(defn consume [component-fn]
  (context/consume :project-context component-fn))
