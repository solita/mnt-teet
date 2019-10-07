(ns teet.project.project-info
  "UI components to show project info based on THK id."
  (:require [postgrest-ui.components.item-view :as item-view]))

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
