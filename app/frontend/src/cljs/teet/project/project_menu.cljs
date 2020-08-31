(ns teet.project.project-menu
  "Menu to navigate between different project views"
  (:require [teet.common.common-controller :as common-controller]
            [reagent.core :as r]
            [teet.ui.hotkeys :as hotkeys]
            [teet.ui.common :as common]
            [teet.ui.material-ui :refer [MenuList MenuItem
                                         IconButton ClickAwayListener Paper
                                         Popper]]
            [herb.core :refer [<class]]
            [teet.project.project-style :as project-style]
            [teet.common.common-styles :as common-styles]
            [teet.ui.icons :as icons]
            [teet.ui.typography :as typography]
            [teet.localization :refer [tr]]
            [teet.ui.project-context :as project-context]
            [teet.theme.theme-colors :as theme-colors]))

;; Define multimethods that different views can implement to hook into
;; the project menu system.
;; The project-tab-content is the only required one.

(defmulti project-tab-action (fn [tab-name _e! _app _project] tab-name))
(defmulti project-tab-content (fn [tab-name _e! _app _project] tab-name))
(defmulti project-tab-footer (fn [tab-name _e! _app _project] tab-name))
(defmulti project-tab-badge (fn [tab-name _project] tab-name))

(defmethod project-tab-action :default [_ _ _ _] nil)
(defmethod project-tab-footer :default [_ _ _ _] nil)
(defmethod project-tab-badge :default [_ _] nil)
(defmethod project-tab-content :default [tab _ _ _]
  [:div "ERROR no tab content for " (pr-str tab)])

(def project-tabs-layout
  [{:name :activities
    :label [:project :tabs :activities]
    :navigate {:page :project :query {:tab "activities"}}
    :layers #{:thk-project :related-cadastral-units :related-restrictions}
    :hotkey "1"}
   {:name :people
    :label [:project :tabs :people]
    :navigate {:page :project :query {:tab "people"}}
    :layers #{:thk-project}
    :hotkey "2"}
   {:name :details
    :label [:project :tabs :details]
    :navigate {:page :project :query {:tab "details"}}
    :layers #{:thk-project}
    :hotkey "3"}
   {:name :restrictions
    :label [:project :tabs :restrictions]
    :navigate {:page :project :query {:tab "restrictions"}}
    :layers #{:thk-project}
    :hotkey "4"}
   {:name :meetings
    :label [:project :tabs :meetings]
    :navigate {:page :project-meetings}
    :hotkey "5"}
   {:name :land
    :label [:project :tabs :land]
    :navigate {:page :project :query {:tab "land"}}
    :hotkey "6"}
   {:name :road
    :label [:project :tabs :road-objects]
    :navigate {:page :project :query {:tab "road"}}
    :hotkey "7"}])

(def ^:const default-tab-name :activities)

(defn active-tab
  "Determine which tab is currently active.
  Returns map describing the active tab or
  default tab if no tab matched navigation info."
  [{:keys [page query] :as _app}]
  (or (some (fn [{{tab-page :page
                   tab-query :query} :navigate :as tab}]
              ;; Tab is active is the page is correct and
              ;; all the query params have correct values.
              ;; The page may have other query params as well.
              (when (and (= tab-page page)
                         (or (nil? tab-query)
                             (= tab-query (select-keys query (keys tab-query)))))
                tab))
            project-tabs-layout)

      ;; return default tab
      (some #(when (= (:name %) default-tab-name) %) project-tabs-layout)))

(defn- project-tabs-item [e! close-menu! _selected?
                          {:keys [hotkey navigate] :as _tab}
                          project-id]
  (let [activate! #(do
                     (e! (common-controller/map->Navigate
                          (assoc-in navigate
                                    [:params :project]
                                    (str project-id))))
                     (close-menu!))]
    (common/component
     (hotkeys/hotkey hotkey activate!)
     (fn [_ _ selected? {:keys [label hotkey]} _]
       ;; FIXME: show badge here or when tab is selected?
       [MenuItem {:on-click activate!
                  :selected selected?
                  :classes {:root (<class project-style/project-view-selection-item)}}
        [:div {:class (<class project-style/project-view-selection-item-label)} (tr label)]
        [:div {:class (<class project-style/project-view-selection-item-hotkey)} (tr [:common :hotkey] {:key hotkey})]]))))

(defn project-menu [_e! _app _project _dark-theme?]
  (let [open? (r/atom false)
        anchor-el (atom nil)
        toggle-open! #(do
                        (swap! open? not)
                        (.blur @anchor-el))
        set-anchor! #(reset! anchor-el %)]
    (common/component
     (hotkeys/hotkey "Q" toggle-open!)
     (fn [e! {:keys [params query] :as app} project dark-theme?]
       (let [{tab-name :name
              tab-label :label} (active-tab app)]
         [:div {:class (<class project-style/project-tab-container dark-theme?)}
          [:div {:class (<class common-styles/space-between-center)}
           [:div {:class (<class common-styles/flex-align-center)}
            [IconButton {:on-click toggle-open!
                         :ref set-anchor!}
             [icons/navigation-apps (when dark-theme?
                                      {:style {:color theme-colors/white}})]]
            [typography/Heading2 {:class [(<class common-styles/inline-block)
                                          (<class common-styles/no-margin)
                                          (<class common-styles/padding 0 0 0 0.5)]}
             (tr tab-label)]]
           [project-tab-action tab-name e! app project]]
          [Popper {:open @open?
                   :anchor-el @anchor-el
                   :classes {:paper (<class project-style/project-view-selection-menu)}
                   :placement "bottom-start"}
           (project-context/consume
             (fn [{project-id :thk.project/id}]
               [ClickAwayListener {:on-click-away toggle-open!}
                [Paper
                 [MenuList {}
                  (doall
                    (for [tab project-tabs-layout]
                      ^{:key (name (:name tab))}
                      [project-tabs-item
                       e! toggle-open! (= tab-name (:name tab))
                       tab
                       project-id]))]]]))]])))))
