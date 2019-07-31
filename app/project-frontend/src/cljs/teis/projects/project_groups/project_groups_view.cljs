(ns teis.projects.project-groups.project-groups-view
  "View for project groups: listing and form to add new one."
  (:require [reagent.core :as r]
            [tuck.core :as t]
            [postgrest-ui.components.listing :as postgrest-listing]
            [postgrest-ui.components.item-view :as postgrest-item-view]
            [teis.projects.project-groups.project-groups-controller :as project-groups-controller]
            [teis.ui.panels :as panels]
            [teis.ui.grid :as grid]
            [teis.ui.info :as info]
            [taoensso.timbre :as log]))

(defn- link-to-project-group [{:strs [id name] :as group}]
  (log/info "project group"  group)
  [:a {:href (str "#/projectgroup/" id)} name])

(defn project-groups-listing [e! app]
  [postgrest-listing/listing
   {:endpoint (get-in app [:config :project-registry-url])
    :state (get-in app [:project-groups :listing])
    :set-state! #(e! (project-groups-controller/->SetListingState %))
    :table "projectgroup"
    :select ["id" "name" "description"]
    :format {"name" link-to-project-group}}])

(defn project-group-info [{:strs [name] :as group}]
  [panels/main-content-panel {:title name}
   [info/info {:data group}
    {:title "Yleiset"
     :keys ["name" "description"]}]])

(defn project-group-page [e! {:keys [project-group] :as app}]

  [postgrest-item-view/item-view
   {:endpoint (get-in app [:config :project-registry-url])
    :state project-group
    :set-state! #(e! (project-groups-controller/->SetProjectGroupState %))
    :table "projectgroup"
    :select ["id" "name" "description" "county" "phase" "url"
             "created" "deleted" "modified" "created_by" "modified_by" "deleted_by"]
    :view project-group-info}
   (get-in app [:params :group])])
