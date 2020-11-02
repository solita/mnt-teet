(ns teet.project.project-info
  "UI components to show project info based on THK id."
  (:require [postgrest-ui.components.item-view :as item-view]
            [clojure.string :as str]
            [teet.app-state :as app-state]))

(defn- get-name [{name "name"}]
  name)

(defn project-name [{:keys [config user]} project-id]
  [item-view/item-view
   {:endpoint (:api-url config)
    :token (:api-token user)
    :table "thk_project"
    :select ["name"]
    :view get-name}
   project-id])

(defn thk-url
  "Checks the environment based on the hostname and returns THK url for a project"
  [{:thk.project/keys [id]}]
  (str (app-state/config-value :thk :url) id))
