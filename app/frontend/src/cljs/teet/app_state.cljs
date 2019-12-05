(ns teet.app-state
  (:require [reagent.core :as r]))

(defonce app (r/atom {:config {}
                      :navigation {:open? true}}))
