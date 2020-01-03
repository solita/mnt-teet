(ns teet.ui.typography
  (:require [teet.ui.material-ui :refer [Typography]]
            [teet.ui.util :as util]))

(def Heading1 (util/make-component Typography {:variant "h1"}))

(def Heading2 (util/make-component Typography {:variant "h2"}))

(def Heading3 (util/make-component Typography {:variant "h3"}))

(def Text (util/make-component Typography {:variant "body1"}))

(def Paragraph (util/make-component Typography {:variant "body1" :paragraph true}))

(def SectionHeading (util/make-component Typography {:variant "h6"}))

(def DataLabel (util/make-component Typography {:variant "subtitle1"}))

(def SmallText (util/make-component Typography {:variant "subtitle2"}))
