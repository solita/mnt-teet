(ns teet.ui.build-info
  (:require [herb.core :refer [<class]]
            [teet.navigation.navigation-style :as navigation-style]
            [teet.ui.material-ui :refer [Collapse IconButton]]
            [teet.ui.icons :as icons]
            [reagent.core :as r]
            [teet.localization :refer [tr]]
            [teet.theme.theme-colors :as theme-colors]))

(defn banner-style
  [nav-open? page]
  (let [nav-width (str (navigation-style/drawer-width nav-open?) "px")]
    (merge {:background-color theme-colors/warning
            :transition       "all 0.2s ease-in-out"
            :padding-left     nav-width
            :display          :flex
            :justify-content  :space-between
            :min-height       "91px"
            :align-items      :center
            :border-bottom    (str "1px solid " theme-colors/gray-light)}
           (when (= page :login)
             {:padding-left 0
              :transition   0}))))

(defn top-banner
  "Show build information at the top of the page"
  [nav-open? page]
  (r/with-let [branch (.-teet_branch js/window)
               git-hash (.-teet_githash js/window)
               build-time (.-teet_buildtime js/window)
               open? (r/atom (or branch git-hash))          ;;TODO: Add check for production/prelive environment
               close-fn #(swap! open? not)]
    [Collapse {:in @open?}
     [:div {:class (<class banner-style nav-open? page)}
      [:p {:style {:padding "0 1rem"
                   :margin  0}}
       [:b (tr [:environment :info-text])]
       [:span (tr [:environment :build-time]) ": " [:strong build-time]]
       [:p {:style {:margin 0}}
        [:span (tr [:environment :version])
         [:strong branch]]]
       [:span "Git hash: "
        [:strong git-hash]]]
      [:div [IconButton {:on-click close-fn
                         :color    :primary}
             [icons/navigation-close]]]]]))
