(ns teet.ui.typography
  (:require [teet.ui.material-ui :refer [Typography]]))

;; TODO If first of children is map, merge with {:variant ...}
(defn Heading1 [& children]
  (into [Typography {:variant "h1"}] children))

(defn Heading2 [& children]
  (into [Typography {:variant "h2"}] children))

(defn Heading3 [& children]
  (into [Typography {:variant "h3"}] children))

(defn Text [& children]
  (into [Typography {:variant "body1"}] children))

(defn Paragraph [& children]
  (into [Typography {:variant "body1" :paragraph true}] children))

(defn SectionHeading [& children]
  (into [Typography {:variant "h6"}] children))

(defn DataLabel [& children]
  (into [Typography {:variant "subtitle1"}] children))
