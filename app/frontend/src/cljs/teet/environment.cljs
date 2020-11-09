(ns teet.environment
  (:require [teet.app-state :as app-state]))


(defn config-value [& path]
  (get-in @app-state/config (vec path)))
