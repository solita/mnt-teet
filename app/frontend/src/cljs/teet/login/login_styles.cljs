(ns teet.login.login-styles
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn login-background
  []
  {:display :flex
   :justify-content :center
   :align-items :center
   :background-image "linear-gradient(rgba(52,57,76,0.7), rgba(52,57,76,0.7)), url(/img/loginsplash.webp)"
   :background-position :center
   :background-size :cover
   :min-height "100vh"})

(defn login-container
  []
  {:width "420px"
   :border-radius "4px"
   :overflow :hidden
   :background-color theme-colors/white
   :box-shadow "0px 4px 16px rgba(0, 0, 0, 0.36)"})

(defn user-list
  []
  {:display :flex
   :flex-direction :column
   :background-color theme-colors/gray-lightest
   :padding "1rem 2rem"})

(defn logo-text
  []
  {:font-family "Roboto Condensed"
   :font-style :normal
   :font-weight :normal
   :font-size "14px"
   :line-height "25px"
   :letter-spacing "0.5px"
   :text-decoration :none
   :margin-left "0.7rem"
   :color "#006EB5"})

(defn logo-container
  []
  {:display :flex
   :justify-content :flex-start
   :align-items :center
   :padding "2rem 1rem"
   :border-bottom (str "1px solid " theme-colors/gray-lighter)})

(defn tara-login []
  {:padding "1rem 2rem"
   :background-color theme-colors/gray-lightest
   :width "100%"
   :display "inline-block"
   :text-align :center})
