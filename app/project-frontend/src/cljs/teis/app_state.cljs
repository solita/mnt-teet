(ns teis.app-state
  (:require [reagent.core :as r]))

;; Load config before rendering app
(defonce app (r/atom {:config {:project-registry-url "http://localhost:3000"}}))
