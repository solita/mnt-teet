(ns teet.ui.typography
  (:require [herb.core :as herb :refer [<class]]
            [teet.ui.util :as util]
            [teet.theme.theme-colors :as theme-colors]))

;; Old typography styles
(defn- small-gray-text-style
  []
  {:display :block
   :color theme-colors/gray-light
   :font-size "0.875rem"})

(defn- small-text-style
  []
  {:font-size "0.875rem"})

(defn grey-text-style
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

(defn- small-bold-text-style
  []
  {:font-weight 700
   :font-size "0.875rem"})

;; Legacy style
(defn- section-heading-style []
  {:font-weight    700
   :font-size      "1rem"
   :line-height    1.375
   :letter-spacing "0.25px"})

(defn- data-label-style []
  {:fontFamily    "Roboto Condensed"
   :fontWeight    400
   :fontSize      "0.875rem"
   :lineHeight    1.375
   :letterSpacing "0.25px"
   :color         theme-colors/secondary-text
   :border-bottom (str "2px solid " theme-colors/gray-light)
   :textTransform :uppercase})


(def Heading1 (util/make-component :h1 {}))

(def Heading2 (util/make-component :h2 {}))

(def Heading3 (util/make-component :h3 {}))

(def Text (util/make-component :span {}))

(def Paragraph (util/make-component :p {}))

(def SectionHeading (util/make-component :h6 {:class (<class section-heading-style)}))

(def DataLabel (util/make-component :span {:class (<class data-label-style)}))

(def SmallGrayText (util/make-component :span {:class (<class small-gray-text-style)}))

(def GreyText (util/make-component :span {:class (<class grey-text-style)}))

(def BoldGreyText (util/make-component :span {:class (<class dark-grey-text-style)}))

(def WarningText (util/make-component :p {:class (<class warning-text-style)}))

(def SmallText (util/make-component :p {:class (<class small-text-style)}))

(def SmallBoldText (util/make-component :span {:class (<class small-bold-text-style)}))
