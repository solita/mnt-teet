(ns teet.ui.tabs
  (:require [teet.ui.material-ui :refer [Link Tabs Tab]]
            [teet.common.common-controller :as common-controller]
            [teet.ui.url :as url]
            [teet.localization :refer [tr]]
            [teet.comments.comments-view :as comments-view]
            [teet.comments.comments-controller :as comments-controller]
            [reagent.core :as r]
            [teet.ui.typography :as typography]
            [teet.ui.common :as common]
            [herb.core :refer [<class]]
            [teet.ui.panels :as panels]
            [teet.log :as log]
            [teet.common.common-styles :as common-styles]))

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
           :scroll-buttons :auto
           :variant :scrollable
           :on-change (fn [_ v]
                        (let [tab (index->tab v)]
                          (e! (common-controller/->SetQueryParam :tab (:value tab)))))}
     (doall
      (for [{:keys [value label badge]} tabs]
        (Tab {:key value
              :disable-ripple true
              :label (r/as-element
                      [:div {:style {:position :relative}}
                       [:span
                        (if (vector? label)
                          (tr label)
                          label)]
                       (when badge
                         [:div {:style {:position :absolute
                                        :top "-15px"
                                        :right "-5px"}}
                          badge])])})))]))


(defn details-and-comments-tabs
  [{:keys [e!]}]
  (r/create-class
    {:component-will-unmount #(e! (comments-controller/->ClearCommentField))
     :reagent-render
     (fn [{:keys [app] :as opts} details]
       (let [query (:query app)
             comments-component [:div
                                 (when (common/wide-display?)
                                   [typography/Heading2 {:class (<class common-styles/margin-bottom 2)} (tr [:document :comments])])
                                 [comments-view/lazy-comments
                                  (select-keys opts [:e! :app :entity-id :entity-type
                                                     :show-comment-form?])]]]
         (if (common/wide-display?)
           ;; Wide display, show side by side
           [panels/side-by-side
            50 details
            50 comments-component]

           ;; Not a wide display, show tabbed interface
           [:div
            [:div {:style {:margin "1rem 0 1rem 0"}}
             [:div {:style {:display :inline-block}}            ;;TODO cleanup inline-styles and html structure
              (if (= (:tab query) "comments")
                [Link {:href (url/remove-query-param :tab)} (tr [:project :tabs :details])]
                [typography/SectionHeading (tr [:project :tabs :details])])]
             [:div {:style {:display :inline-block
                            :margin-left "2rem"}}
              (if (= (:tab query) "comments")
                [typography/SectionHeading (tr [:document :comments])]
                [Link {:href (url/set-query-param :tab "comments")} (tr [:document :comments])])]]
            (if (= (:tab query) "comments")                     ;;TODO LOAD comments on render and
              comments-component
              (with-meta
                details
                {:key "details"}))])))}))
