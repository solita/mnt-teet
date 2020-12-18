(ns teet.ui.typography
  (:require [herb.core :as herb :refer [<class]]
            [teet.ui.material-ui :refer [Typography]]
            [teet.ui.util :as util]
            [teet.theme.theme-colors :as theme-colors]))

;; New typography styles
(defn h1-desktop []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "300"
   :font-size "32px"
   :line-height "48px"})

(defn h2-desktop []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "normal"
   :font-size "28px"
   :line-height "42px"})

(defn h3-desktop []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "normal"
   :font-size "24px"
   :line-height "36px"})

(defn h4-desktop []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "normal"
   :font-size "20px"
   :line-height "30px"})

(defn h5-desktop []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "normal"
   :font-size "18px"
   :line-height "27px"})

(defn body-1-bold []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "bold"
   :font-size "16px"
   :line-height "24px"})

(defn body-1-regular []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "normal"
   :font-size "16px"
   :line-height "24px"})

(defn link-1 []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "normal"
   :font-size "16px"
   :line-height "24px"})

(defn link-2 []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "normal"
   :font-size "14px"
   :line-height "21px"})

(defn body-2-bold []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "bold"
   :font-size "14px"
   :line-height "21px"})

(defn body-3-bold []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "bold"
   :font-size "12px"
   :line-height "18px"})

(defn body-3 []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "normal"
   :font-size "12px"
   :line-height "18px"})

(defn subtitle-1 []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "bold"
   :font-size "16px"
   :line-height "24px"})

(defn subtitle-2 []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "normal"
   :font-size "14px"
   :line-height "21px"})

(defn h1-mobile []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "300"
   :font-size "24px"
   :line-height "36px"})

(defn h2-mobile []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "normal"
   :font-size "22px"
   :line-height "30px"})

(defn h3-mobile []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "normal"
   :font-size "20px"
   :line-height "30px"})

(defn h4-mobile []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "normal"
   :font-size "19px"
   :line-height "28px"})

(defn h5-mobile []
  {:font-family "Roboto"
   :font-style "normal"
   :font-weight "normal"
   :font-size "18px"
   :line-height "27px"})


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

(def SmallBoldText (util/make-component :span {:class (<class small-bold-text-style)}))
