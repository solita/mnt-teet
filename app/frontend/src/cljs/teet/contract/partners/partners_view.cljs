(ns teet.contract.partners.partners-view
  (:require [teet.util.collection :as cu]
            [teet.ui.material-ui :refer [Paper Grid]]
            [herb.core :refer [<class]]
            [teet.common.common-styles :as common-styles]
            [reagent.core :as r]
            [teet.ui.typography :as typography]))

(defn- scrollable-grid [xs content]
  [Grid {:item true :xs xs
         :classes #js {:item (<class common-styles/content-scroll-max-height "60px")}}
   content])

;; Targeted from routes.edn will be located in route "/contracts/:contract-ids/partners"
(defn partners-info-page [_e! app]
  (let [open (r/atom #{})
        toggle! #(swap! open cu/toggle %)]
    (r/create-class
      {:component-will-receive-props
       (fn [_e! app]
         (println "Component received props"))
       :reagent-render
       (fn [_e! app]
         (println "Component rendered")
         [:<>
          [:div {:class (<class common-styles/flex-row-space-between)
                 :style {:align-items :center}}

           [typography/Heading1 "Partners info page"]]
          [Paper {}
           [Grid {:container true :spacing 0 :wrap :wrap}
            [scrollable-grid 4
             [:div "Partners list"]]
            [scrollable-grid 8
             [:div {:style {:padding "1rem"}}]]]]])})))