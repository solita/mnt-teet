(ns teet.ui.typography
  (:require [herb.core :as herb :refer [<class]]
            [teet.ui.material-ui :refer [Typography]]
            [teet.ui.util :as util]
            [teet.theme.theme-colors :as theme-colors]))

(defn- small-gray-text-style
  []
  {:display :block
   :color theme-colors/gray-light
   :font-size "0.75rem"})

(defn- small-text-style
  []
  {:font-size "0.875rem"})

(defn- grey-text-style
  []
  {:display :block
   :color theme-colors/gray-light})

(defn- dark-grey-text-style
  []
  {:display :block
   :color theme-colors/gray
   :font-weight :bold})

(defn- warning-text-style
  []
  {:color theme-colors/warning})

(def Heading1 (util/make-component Typography {:variant "h1"}))

(def Heading2 (util/make-component Typography {:variant "h2"}))

(def Heading3 (util/make-component Typography {:variant "h3"}))

(def Text (util/make-component Typography {:variant "body1"}))

(def Paragraph (util/make-component Typography {:variant "body1" :paragraph true}))

(def SectionHeading (util/make-component Typography {:variant "h6"}))

(def DataLabel (util/make-component Typography {:variant "subtitle1"}))

(def SmallGrayText (util/make-component :span {:class (<class small-gray-text-style)}))

(def GreyText (util/make-component :span {:class (<class grey-text-style)}))

(def BoldGreyText (util/make-component :span {:class (<class dark-grey-text-style)}))

(def WarningText (util/make-component :p {:class (<class warning-text-style)}))

(def SmallText (util/make-component :p {:class (<class small-text-style)}))
