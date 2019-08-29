(ns teet.ui.typography
  (:require [teet.ui.material-ui :refer [Typography]]))

(defn- make-typography [typography-props]
  (fn [& children]
    (if (map? (first children))
      (into [Typography (merge (first children) typography-props)] (rest children))
      (into [Typography typography-props] children))))

;; TODO If first of children is map, merge with {:variant ...}
(defn Heading1 [& children]
  (make-typography {:variant "h1"}))

(defn Heading2 [& children]
  (make-typography {:variant "h2"}))

(defn Heading3 [& children]
  (make-typography {:variant "h3"}))

(defn Text [& children]
  (make-typography {:variant "body1"}))

(defn Paragraph [& children]
  (make-typography {:variant "body1" :paragraph true}))

(defn SectionHeading [& children]
  (make-typography {:variant "h6"}))

(defn DataLabel [& children]
  (make-typography {:variant "subtitle1"}))
