(ns teet.app-state
  (:require [reagent.core :as r]
            [taoensso.timbre :as log]))

;; Load config before rendering app
(defonce app (r/atom {})) ;:config {:project-registry-url "http://localhost:3000"}

(defn init!
  "Initialize app state by loading environment configuration.
  Returns promise."
  []
  (-> (js/fetch "/config.json")
      (.then #(.json %))
      (.then #(let [config (js->clj % :keywordize-keys true)]
                (log/info "Loaded app config: " config)
                (swap! app assoc :config config)))))
