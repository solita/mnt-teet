(ns teet.theme.theme-provider
  (:require [teet.ui.material-ui :refer [MuiThemeProvider]]
            [cljs-bean.core :refer [->js]]
            [goog.object :as gobj]
            [teet.theme.theme-colors :as theme-colors]))

;;To target disabled buttons :MuiButton {:root {:&$disabled {//css here//}}

(def teet-theme
  {:palette {:primary {:main theme-colors/primary}
             :secondary {:main theme-colors/secondary}
             :background {:default theme-colors/white}}
   :overrides {:MuiAppBar {:colorDefault {:background-color theme-colors/white}
                           :positionSticky {:box-shadow "none"}}
               :MuiToolBar {:root {:min-height "80px"}}     ;This doesn't properly target the toolbar inside appbar
               :MuiButton {:contained {:border-radius "2px"}}
               :MuiDrawer {:paper {:background-color theme-colors/primary
                                   :box-shadow "5px 3px 5px #cecece"}
                           :paperAnchorDockedLeft {:border-right 0}}
               :MuiDivider {:root {:margin "1rem 0"}}}})

(defn- create-theme [theme]
  (let [create-mui-theme (-> js/window
                           (gobj/get "MaterialUI")
                           (gobj/get "createMuiTheme"))]
    (-> theme
      ->js
      create-mui-theme)))

(defn theme-provider [content]
  [MuiThemeProvider
   {:theme (create-theme teet-theme)}
   content])

