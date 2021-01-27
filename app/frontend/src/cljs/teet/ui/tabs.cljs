(ns teet.ui.tabs
  (:require [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.comments.comments-controller :as comments-controller]
            [teet.comments.comments-view :as comments-view]
            [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr]]
            [teet.ui.common :as common]
            [teet.ui.panels :as panels]
            [teet.ui.typography :as typography]
            [teet.ui.url :as url]
            [teet.theme.theme-colors :as theme-colors]))

(defn- comments-link-content
  [comment-counts]
  (if-not comment-counts
    ;; No comment counts provided, use just the comments text
    (tr [:document :comments])

    ;; Comment counts provided, show badge
    [:span {:style {:text-transform :lowercase}}
     [common/comment-count-chip {:comment/counts comment-counts}]
     (tr [:document :comments])]))

(defn tab-element-style
  []
  ^{:pseudo {:last-child {:margin-right 0}}}
  {:margin-right "1rem"
   :border-radius "3px 3px 0px 0px"
   :align-items "center"
   :padding "13.5px 8px"
   })

(defn tab-element-style-selected
  []
  ^{:pseudo {:last-child {:margin-right 0}}}
  {:margin-right "1rem"
   :color theme-colors/white
   :align-items "center"
   :padding "13.5px 8px"
   :background "#005AA3"
   :border-radius "3px 3px 0px 0px"})

(defn tab-links-container-style
  []
  {:display :flex
   :margin-bottom "2rem"
   :padding-bottom "0"
   :border-bottom "solid #005AA3 1px"})

(defn details-and-comments-tabs
  [{:keys [e!]}]
  (r/create-class
    {:component-will-unmount #(e! (comments-controller/->ClearCommentField))
     :reagent-render
     (fn [{:keys [app tab-wrapper comment-link-comp comment-counts]
           :or {tab-wrapper :span}
           :as opts} details]
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
            [tab-wrapper
             [:div.tab-links {:class (<class tab-links-container-style)}

               [:div {:class (<class (if (= (:tab query) "comments") tab-element-style tab-element-style-selected))}
                (if (= (:tab query) "comments")
                  [common/Link {:href (url/remove-query-param :tab)} (tr [:project :tabs :details])]
                  [typography/SectionHeading (tr [:project :tabs :details])])]

               [:div {:class (<class (if (= (:tab query) "comments") tab-element-style-selected tab-element-style))}
                (if (= (:tab query) "comments")
                  [typography/SectionHeading
                   [comments-link-content comment-counts]]
                  (or comment-link-comp
                      [common/Link {:href (url/set-query-param :tab "comments")}
                       [comments-link-content comment-counts]]))]]]
            (if (= (:tab query) "comments")
              comments-component
              (with-meta
                details
                {:key "details"}))])))}))



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
              [typography/SectionHeading {:class (<class tab-element-style-selected)} (tr [:tab-names tab])]
              [common/Link {:class (<class tab-element-style)
                            :href (url/set-query-param :tab (name tab))}
               (tr [:tab-names tab])])
            {:key tab})))]
     [:div.tab-content
      content]]))
