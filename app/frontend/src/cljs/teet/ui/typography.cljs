(ns teet.ui.typography
  (:require [teet.ui.material-ui :refer [Typography]]))

(defn- make-typography [typography-props]
  (fn [& children]
    (if (map? (first children))
      (into [Typography (merge (first children) typography-props)] (rest children))
      (into [Typography typography-props] children))))

;; TODO If first of children is map, merge with {:variant ...}
(def Heading1 (make-typography {:variant "h1"}))

(def Heading2 (make-typography {:variant "h2"}))

(def Heading3 (make-typography {:variant "h3"}))

(def Text (make-typography {:variant "body1"}))

(def Paragraph (make-typography {:variant "body1" :paragraph true}))

(def SectionHeading (make-typography {:variant "h6"}))

(def DataLabel (make-typography {:variant "subtitle1"}))
