(ns teet.ui.tabs
  (:require [teet.ui.material-ui :refer [Tabs Tab]]
            [teet.common.common-controller :as common-controller]
            [teet.ui.material-ui :refer [Link]]
            [teet.ui.url :as url]
            [teet.comments.comments-view :as comments-view]
            [teet.comments.comments-controller :as comments-controller]
            [reagent.core :as reagent]
            [teet.ui.typography :as typography]
            [teet.ui.common :as common]
            [teet.ui.panels :as panels]
            [teet.log :as log]))

(defn tabs [{:keys [e! selected-tab class]} tabs]
  (let [tabs (map-indexed
              (fn [i tab]
                (assoc tab ::index i))
              tabs)
        index->tab (into {}
                         (map (juxt ::index identity))
                         tabs)
        value->tab (into {}
                         (map (juxt :value identity))
                         tabs)]
    [Tabs {:value (::index (value->tab selected-tab))
           :textColor "primary"
           :class class
           :on-change (fn [_ v]
                        (let [tab (index->tab v)]
                          (e! (common-controller/->SetQueryParam :tab (:value tab)))))}
     (doall
      (for [{:keys [value label]} tabs]
        (Tab {:key value
              :disable-ripple true
              :label label})))]))


(defn details-and-comments-tabs
  [{:keys [e!]}]
  (reagent/create-class
    {:component-will-unmount #(e! (comments-controller/->ClearCommentField))
     :reagent-render
     (fn [{:keys [e! app entity-id entity-type]} details]
       (let [query (:query app)
             comments-component [comments-view/lazy-comments
                                 {:e! e!
                                  :app app
                                  :entity-type entity-type
                                  :entity-id entity-id}]]
         (log/info "WIDE? " (common/wide-display?))
         (if (common/wide-display?)
           ;; Wide display, show side by side
           [panels/side-by-side
            50 details
            50 comments-component]

           ;; Not a wide display, show tabbed interface
           [:div
            [:div {:style {:margin-bottom "1rem"}}
             [:div {:style {:display :inline-block}}            ;;TODO cleanup inline-styles and html structure
              (if (= (:tab query) "comments")
                [Link {:href (url/remove-query-param :tab)} "Details"]
                [typography/SectionHeading "Details"])]
             [:div {:style {:display :inline-block
                            :margin-left "2rem"}}
              (if (= (:tab query) "comments")
                [typography/SectionHeading "Comments"]
                [Link {:href (url/set-query-param :tab "comments")} "Comments"])]]
            (if (= (:tab query) "comments")                     ;;TODO LOAD comments on render and
              comments-component
              details)])))}))
