(ns teet.cooperation.cooperation-view
  (:require [teet.project.project-view :as project-view]
            [teet.ui.url :as url]
            [teet.ui.typography :as typography]
            [teet.localization :refer [tr]]))


(defn- third-parties [third-parties]
  [:div
   (for [{id :db/id
          :cooperation.3rd-party/keys [name]} third-parties]
     ^{:key (str id)}
     [url/Link {:page :cooperation-third-party
                :params {:third-party (js/encodeURIComponent name)}}
      name])])

(defn cooperation-page-structure [e! app project third-parties-list main-content]
  [project-view/project-full-page-structure
   {:e! e!
    :app app
    :project project
    :left-panel [third-parties third-parties-list]
    :main main-content}])

(defn overview-page [e! {:keys [user] :as app} {:keys [project overview]}]
  [cooperation-page-structure
   e! app project overview
   [:<>
    [typography/Heading2 (tr [:cooperation :page-title])]
    [typography/BoldGreyText (tr [:cooperation :all-third-parties])]
    (pr-str overview)]])

(defn third-party-page [e! {:keys [user params] :as app} {:keys [project overview]}]
  (let [third-party-name (js/decodeURIComponent (:third-party params))
        third-party (some #(when (= third-party-name
                                    (:cooperation.3rd-party/name %)) %)
                          overview)]
    [cooperation-page-structure
     e! app project overview
     [:div (pr-str third-party)]]))
