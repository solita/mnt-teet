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

(defn- medium-italic-style
  []
  {:font-style :italic
   :color theme-colors/text-medium-emphasis})

;; Legacy style
(defn- section-heading-style []
  {:font-weight    700
   :font-size      "1rem"
   :line-height    1.375
   :letter-spacing "0.25px"})

(defn- hidden-text-style []
  {:visibility :hidden})

(defn- data-label-style []
  {:fontFamily    "Roboto Condensed"
   :fontWeight    400
   :fontSize      "0.875rem"
   :lineHeight    1.375
   :letterSpacing "0.25px"
   :color         theme-colors/secondary-text
   :border-bottom (str "2px solid " theme-colors/gray-light)
   :textTransform :uppercase})

(defn- sub-section-style []
  {:font-weight    700
   :font-size      "1rem"
   :margin-bottom "0.5rem"
   :margin-top "1.5rem"
   :color         theme-colors/secondary-text
   :text-transform :uppercase})

(def Heading1 (util/make-component :h1 {}))
(def Heading2 (util/make-component :h2 {}))
(def Heading3 (util/make-component :h3 {}))
(def Heading4 (util/make-component :h4 {}))
(def Heading5 (util/make-component :h5 {}))

;; TODO: maybe some other element type?
(def Subtitle1 (util/make-component :p {:class "subtitle1"}))
(def Subtitle2 (util/make-component :p {:class "subtitle2"}))

(def Text (util/make-component :p {}))
(def HiddenText (util/make-component :p {:class (<class hidden-text-style)}))
(def TextBold (util/make-component :p {:class "body1-bold"}))
(def Text2 (util/make-component :p {:class "body2"}))
(def Text2Bold (util/make-component :p {:class "body2-bold"}))
(def Text3 (util/make-component :p {:class "body3"}))
(def Text3Bold (util/make-component :p {:class "body3-bold"}))

(def Paragraph (util/make-component :p {:class "paragraph"}))
(def ParagraphBold (util/make-component :p {:class "paragraph body1-bold"}))
(def Paragraph2 (util/make-component :p {:class "paragraph body2"}))
(def Paragraph2Bold (util/make-component :p {:class "paragraph body2-bold"}))
(def Paragraph3 (util/make-component :p {:class "paragraph body3"}))
(def Paragraph3Bold (util/make-component :p {:class "paragraph body3-bold"}))

(def Link (util/make-component :a {}))
(def Link2 (util/make-component :a {:class "link2"}))

(def SectionHeading (util/make-component :h6 {:class (<class section-heading-style)}))

(def DataLabel (util/make-component :div {:class (<class data-label-style)}))

(def SubSectionLabel (util/make-component :p {:class (<class sub-section-style)}))

(def SmallGrayText (util/make-component :span {:class (<class small-gray-text-style)}))

(def GreyText (util/make-component :span {:class (<class grey-text-style)}))

(def BoldGreyText (util/make-component :span {:class (<class dark-grey-text-style)}))

(def WarningText (util/make-component :p {:class (<class warning-text-style)}))

(def SmallText (util/make-component :p {:class (<class small-text-style)}))

(def SmallBoldText (util/make-component :span {:class (<class small-bold-text-style)}))

(def ItalicMediumEmphasis (util/make-component :span {:class (<class medium-italic-style)}))
