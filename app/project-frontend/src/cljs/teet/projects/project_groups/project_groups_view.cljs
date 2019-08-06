(ns teet.projects.project-groups.project-groups-view
  "View for project groups: listing and form to add new one."
  (:require [reagent.core :as r]
            [tuck.core :as t]
            [postgrest-ui.components.listing :as postgrest-listing]
            [postgrest-ui.components.item-view :as postgrest-item-view]
            [teet.projects.project-groups.project-groups-controller :as project-groups-controller]
            [teet.ui.panels :as panels]
            [teet.ui.grid :as grid]
            [teet.ui.info :as info]
            [taoensso.timbre :as log]
            [teet.localization :as localization :refer [tr]]
            [postgrest-ui.display :as postgrest-display]
            [teet.map.map-view :as map-view]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-features :as map-features]
            [teet.projects.search.search-interface :as search-interface]
            [teet.ui.icons :as icons]))

(defmethod postgrest-display/display [:listing "projectgroup" {:table "phase" :select ["name"]}]
  [_ _ _ {phase "name"}]
  (localization/localized-text phase))

(defmethod search-interface/format-search-result "projectgroup" [{:keys [id label]}]
  {:icon (icons/action-group-work)
   :href (str "#/projectgroup/" id)
   :text label})

(defn- link-to-project-group [{:strs [id name] :as group}]
  (log/info "project group"  group)
  [:a {:href (str "#/projectgroup/" id)} name])

(defn project-groups-listing [e! app]
  [postgrest-listing/listing
   {:endpoint (get-in app [:config :project-registry-url])
    :state (get-in app [:project-groups :listing])
    :set-state! #(e! (project-groups-controller/->SetListingState %))
    :table "projectgroup"
    :select ["id" "name" "description"
             {:table "phase" :select ["name"]}]
    :format {"name" link-to-project-group}}])

(defn project-group-info [{:strs [name] :as group}]
  [panels/main-content-panel {:title name}
   [info/info {:data group}
    {:title (tr [:common :general])
     :table "projectgroup"
     :keys ["name" "description" (fn [{phase "phase"}]
                                   [(tr [:fields "projectgroup" "phase"]) (localization/localized-text (get phase "name"))])]}]])

(defn project-group-page [e! {:keys [project-group] :as app}]
  (let [group-id (get-in app [:params :group])
        endpoint (get-in app [:config :project-registry-url])]
    ^{:key group-id}
    [:div.project-group-page
     [map-view/map-view e! {:height "400px"
                            :layers {:group-projects (map-layers/mvt-layer endpoint "mvt_projectgroup_projects"
                                                                           {"id" group-id}
                                                                           map-features/project-style)}}
      app]
     [postgrest-item-view/item-view
      {:endpoint endpoint
       :state project-group
       :set-state! #(e! (project-groups-controller/->SetProjectGroupState %))
       :table "projectgroup"
       :select ["id" "name" "description" "county" {:table "phase" :select ["name"]} "url"
                "created" "deleted" "modified" "created_by" "modified_by" "deleted_by"]
       :view project-group-info}
      group-id]]))
