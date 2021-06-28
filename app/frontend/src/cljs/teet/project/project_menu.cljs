(ns teet.project.project-menu
  "Menu to navigate between different project views"
  (:require [teet.common.common-controller :as common-controller]
            [reagent.core :as r]
            [teet.ui.hotkeys :as hotkeys]
            [teet.ui.common :as common]
            [teet.ui.material-ui :refer [MenuList MenuItem IconButton ClickAwayListener Paper
                                         Popper Toolbar]]
            [herb.core :as herb :refer [<class]]
            [teet.project.project-style :as project-style]
            [teet.common.common-styles :as common-styles]
            [teet.ui.icons :as icons]
            [teet.ui.typography :as typography :refer [TextBold]]
            [teet.localization :refer [tr]]
            [teet.authorization.authorization-check :as authorization-check]
            [teet.ui.buttons :as buttons]
            [teet.common.responsivity-styles :as responsivity-styles]
            [teet.navigation.navigation-style :as navigation-style]
            [teet.project.project-controller :as project-controller]))

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
    :match-pages #{:activity-meetings :meeting} ; other pages to match as this tab
    :hotkey "5"
    :feature-flag :meetings}
   {:name :land
    :label [:project :tabs :land]
    :navigate {:page :project :query {:tab "land"}}
    :hotkey "6"}
   {:name :road
    :label [:project :tabs :road-objects]
    :navigate {:page :project :query {:tab "road"}}
    :hotkey "7"}
   {:name :cost-items
    :label [:project :tabs :cost-items]
    :navigate {:page :cost-items}
    :hotkey "8"
    :feature-flag :cost-items
    :authorization :cost-items/cost-items
    :match-pages #{:cost-item :cost-items :cost-items-totals :materials-and-products}}
   {:name :cooperation
    :label [:project :tabs :cooperation]
    :navigate {:page :cooperation}
    :match-pages #{:cooperation :cooperation-third-party :cooperation-application}
    :hotkey "9"
    :feature-flag :cooperation}])

(def ^:const default-tab-name :activities)

(defn active-tab
  "Determine which tab is currently active.
  Returns map describing the active tab or
  default tab if no tab matched navigation info."
  [{:keys [page query] :as _app}]
  (or (some (fn [{{tab-page :page
                   tab-query :query} :navigate
                  tab-match-pages :match-pages :as tab}]
              ;; Tab is active if the page is correct and
              ;; all the query params have correct values.
              ;; The page may have other query params as well.
              (when (and (or (= tab-page page)
                             (and tab-match-pages (tab-match-pages page)))
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
     (fn [_ _ selected? {:keys [label hotkey]
                         page-name :name} _]
       ;; FIXME: show badge here or when tab is selected?
       [MenuItem {:on-click activate!
                  :selected selected?
                  :id (str "navigation-item-" (name page-name))
                  :class (str "project-menu-item-" (name (:page navigate)))
                  :classes {:root (<class project-style/project-view-selection-item)}}
        [:div {:class (<class project-style/project-view-selection-item-label)}
         [:span (tr label)]]
        [:div {:class (<class project-style/project-view-selection-item-hotkey)} (tr [:common :hotkey] {:key hotkey})]]))))

(defn project-tab-header [e! app project dark-theme?]
  (let [{tab-name :name
         tab-label :label} (active-tab app)]
    [:div {:class (<class project-style/project-tab-container dark-theme?)}
     [:div {:class (<class common-styles/space-between-center)}
      [:div {:class (<class common-styles/flex-align-center)}
       [typography/Heading2 {:class (herb/join (<class common-styles/inline-block)
                                               (<class common-styles/no-margin)
                                               (<class common-styles/padding 0 0 0 0.5))}
        (tr tab-label)]]
      [project-tab-action tab-name e! app project]]]))

(def project-menu-hotkey "Q")

(defn project-menu-list [e! app project close-menu!]
  (let [{tab-name :name} (active-tab app)]
    [ClickAwayListener {:on-click-away #(do
                                          (.preventDefault %)
                                          (close-menu!))}
     [Paper
      [MenuList {}
       (doall
         (for [{ff :feature-flag
                authz :authorization :as tab} project-tabs-layout
               :when (and (or (nil? ff)
                              (common-controller/feature-enabled? ff))
                          (or (nil? authz)
                              (authorization-check/authorized?
                                {:functionality authz
                                 :project-id (:db/id project)})))]
           ^{:key (name (:name tab))}
           [project-tabs-item
            e! close-menu! (= tab-name (:name tab))
            tab
            (:thk.project/id project)]))]]]))

(defn project-menu-desktop
  "Project menu for desktop.
  Contains project menu button and menu list displayed as Popper tooltip."
  [_e! _app _project]
  (let [open? (r/atom false)
        anchor-el (atom nil)
        toggle-open! #(do
                        (swap! open? not)
                        (some-> @anchor-el .blur))
        set-anchor! #(reset! anchor-el %)]
    (common/component
      (hotkeys/hotkey project-menu-hotkey toggle-open!)
      (fn [e! app project]
        [:<>
         [buttons/button-primary
          {:data-cy "project-menu"
           :size "small"
           :start-icon (r/as-element [icons/navigation-menu])
           :on-click #(do
                        (.preventDefault %)
                        (toggle-open!))
           :ref set-anchor!}
          (tr [:common :project-menu])]
         [Popper {:open @open?
                  :anchor-el @anchor-el
                  :classes #js {:paper (<class project-style/project-view-selection-menu)}
                  :style {:z-index 1200}
                  :placement "bottom-start"}
          [project-menu-list e! app project #(reset! open? false)]]]))))

(defn project-menu-header-mobile
  "Header extension for project views on mobile.
  Extra header bar contains project name and project hamburger menu.
  Project menu list is displayed full-width under the header (pushes content down, no Popper tooltip
  as with desktop)."
  [e! app drawer-open?]
  (when-let [project (project-controller/app->project app)]
    (r/with-let [open? (r/atom false)
                 toggle-open! #(swap! open? not)]
      [:<>
       [:div (merge {:class [(<class navigation-style/appbar)
                             (<class navigation-style/appbar-position drawer-open?)]}
                    (when @open?
                      {:style {:height "unset"}}))
        [Toolbar {:class (<class navigation-style/toolbar)
                  :style (merge {:display "flex"
                                 :justify-content :space-between}
                                (when @open?
                                  {:height "unset"}))}
         [TextBold (merge {:data-cy "project-header"
                           :style {:text-transform :uppercase}}
                          (when-not @open?
                            {:class (<class common-styles/truncate-text)}))
          (:thk.project/name project)]
         [(common/component
            (hotkeys/hotkey project-menu-hotkey toggle-open!)
            (fn [_ _ _]
              [buttons/stand-alone-icon-button
               {:data-cy "project-menu"
                :class (<class responsivity-styles/mobile-navigation-button)
                :icon (if @open?
                        [icons/navigation-close {:color :primary}]
                        [icons/navigation-menu {:color :primary}])
                :on-click #(do
                             (.preventDefault %)
                             (toggle-open!))}]))]]]
       (when @open?
         [project-menu-list e! app project #(reset! open? false)])])))