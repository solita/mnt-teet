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
            [teet.common.common-styles :as common-styles]
            [teet.util.collection :as cu]))

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
                                                     :show-comment-form? :after-comment-list-rendered-event
                                                     :after-comment-added-event
                                                     :after-comment-deleted-event])]]]
         (if (common/wide-display?)
           ;; Wide display, show side by side
           [panels/side-by-side
            1 details
            1 comments-component]

           ;; Not a wide display, show tabbed interface
           [:div.page-content-tabs
            [:div.tab-links {:style {:margin "1rem 0 1rem 0"}}
             [:div {:style {:display :inline-block}}            ;;TODO cleanup inline-styles and html structure
              [:div {:class (if (= (:tab query) "comments") "tab-inactive" "tab-active")}
               (if (= (:tab query) "comments")
                 [Link {:href (url/remove-query-param :tab)} (tr [:project :tabs :details])]
                 [typography/SectionHeading (tr [:project :tabs :details])])]]
             [:div {:style {:display :inline-block
                            :margin-left "2rem"}}
              [:div {:class (if (= (:tab query) "comments") "tab-active" "tab-inactive")}
               (if (= (:tab query) "comments")
                 [typography/SectionHeading (tr [:document :comments])]
                 [Link {:href (url/set-query-param :tab "comments")} (tr [:document :comments])])]]]
            (if (= (:tab query) "comments")
              comments-component
              (with-meta
                details
                {:key "details"}))])))}))

(defn tab-element-style
  []
  ^{:pseudo {:last-child {:margin-right 0}}}
  {:margin-right "1rem"})

(defn tab-links-container-style
  []
  {:display :flex
   :margin-bottom "2rem"})

(defn tabs
  "Given as [[:tab-name tab-component]...[...]] first being the default
   and tab-name used as url-parameter"
  [{query-param-tab :tab} tabs]
  (let [tab-names (map (comp keyword first) tabs)
        selected-tab (or (keyword query-param-tab)
                         (-> tabs
                             first
                             first
                             keyword))
        content (some (fn [[tab-name content]]
                        (if (= (keyword tab-name) selected-tab)
                          content
                          false))
                      tabs)]
    [:div
     [:div.tab-link {:class (<class tab-links-container-style)}
      (doall
        (for [tab tab-names]
          (with-meta
            (if (= (keyword tab) selected-tab)
              [typography/SectionHeading {:class (<class tab-element-style)} (tr [:tab-names tab])]
              [Link {:class (<class tab-element-style)
                     :href (url/set-query-param :tab (name tab))}
               (tr [:tab-names tab])])
            {:key tab})))]
     [:div.tab-content
      content]]))
