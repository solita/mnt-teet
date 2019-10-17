(ns teet.theme.theme-panels
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn side-panel []
  {:background-color theme-colors/gray-lightest
   :height "90vh"
   :padding "1rem"})
