(ns teet.ui.build-info
  (:require [herb.core :refer [<class]]
            [teet.navigation.navigation-style :as navigation-style]
            [teet.ui.material-ui :refer [Collapse IconButton]]
            [teet.ui.icons :as icons]
            [reagent.core :as r]
            [teet.localization :refer [tr]]
            [teet.theme.theme-colors :as theme-colors]
            [clojure.string :as str]))

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
              :transition   "none"}))))

(defn detect-env [hostname]
  (cond
    (str/includes? hostname "dev-teet")
    :dev

    (= hostname "localhost")
    :dev

    (re-matches #"[a-z]\..*" hostname)
    :dev

    (str/includes? hostname "teet-test")
    :test

    (str/includes? hostname "teet-prelive")
    :prelive
    
    :default
    nil))

(defn top-banner
  "Show build information at the top of the page"
  [nav-open? page]
  (r/with-let [branch (aget js/window "teet_branch")
               git-hash (aget js/window "teet_githash")
               build-time (aget js/window "teet_buildtime")
               hostname (-> js/window
                            .-location
                            .-hostname)
               env-name (detect-env hostname)
               open? (r/atom (and env-name (boolean (or branch git-hash))))
               close-fn #(swap! open? not)]
    [Collapse {:in @open?}
     [:div {:class (<class banner-style nav-open? page)}
      [:p {:style {:padding "0 1rem"
                   :margin  0}}
       [:b (tr [:environment :info-text env-name]) " "]
       [:span (tr [:environment :build-time]) ": " [:strong build-time]]
       [:span {:style {:margin 0
                       :display :block}}
        [:span (tr [:environment :version]) ": "
         [:strong branch]]]
       [:span "Git hash: "
        [:strong git-hash]]]
      [:div [IconButton {:on-click close-fn
                         :color    :primary}
             [icons/navigation-close]]]]]))
