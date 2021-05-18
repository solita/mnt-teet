(ns teet.contract.contract-menu
  (:require [teet.ui.buttons :as buttons]
            [teet.ui.common :as common]
            [teet.ui.hotkeys :as hotkeys]
            [reagent.core :as r]
            [teet.ui.icons :as icons]
            [teet.localization :refer [tr]]
            [herb.core :refer [<class]]
            [teet.ui.material-ui :refer [MenuList MenuItem
                                         IconButton ClickAwayListener Paper
                                         Popper Link]]
            [teet.project.project-style :as project-style]
            [teet.common.common-controller :as common-controller]
            [teet.ui.typography :as typography]
            [teet.contract.contract-model :as contract-model]))

(def contract-pages
  [{:name :contract-information
    :label [:contract :contract-information]
    :navigate {:page :contract}
    :hotkey "1"}
   {:name :partners-information
    :label [:contract :partner-information]
    :navigate {:page :partner-information}
    :hotkey "2"}])

(defn- contract-menu-item [e! close-menu!
                           {:keys [hotkey navigate] :as _item}
                           contract-id]
  (let [activate! #(do
                     (e! (common-controller/map->Navigate
                           (assoc-in navigate
                                     [:params :contract-ids]
                                     contract-id)))
                     (close-menu!))]
    (common/component
      (hotkeys/hotkey hotkey activate!)
      (fn [_ _ {:keys [label hotkey]
                page-name :name} _]
        [MenuItem {:on-click activate!
                   :component (r/reactify-component Link)
                   :href (str "#/contracts/" contract-id)
                   :id (str "navigation-item-" (name page-name))
                   :class (str "project-menu-item-" (name (:page navigate)))
                   :classes {:root (<class project-style/project-view-selection-item)}}
         [:div {:class (<class project-style/project-view-selection-item-label)}
          [:span (tr label)]]
         [:div {:class (<class project-style/project-view-selection-item-hotkey)}
          (tr [:common :hotkey] {:key hotkey})]]))))

(defn contract-menu
  [_e! _app _contract]
  (let [open? (r/atom false)
        anchor-el (atom nil)
        toggle-open! #(do
                        (swap! open? not)
                        (.blur @anchor-el))
        set-anchor! #(reset! anchor-el %)]
    (common/component
      (hotkeys/hotkey "Q" toggle-open!)
      (fn [e! app contract]
        [:div
         [buttons/small-button-primary {:start-icon (r/as-element [icons/navigation-menu])
                                        :on-click toggle-open!
                                        :ref set-anchor!}
          (tr [:common :contract-menu])]
         [Popper {:open @open?
                  :anchor-el @anchor-el
                  :classes {:paper (<class project-style/project-view-selection-menu)}
                  :placement "bottom-start"}
          [ClickAwayListener {:on-click-away toggle-open!}
           [Paper
            (into [MenuList {}]
                  (for [page contract-pages]
                    [contract-menu-item
                     e! toggle-open! page
                     (contract-model/contract-url-id contract)]))]]]]))))
