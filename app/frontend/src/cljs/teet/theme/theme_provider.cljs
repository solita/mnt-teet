(ns teet.theme.theme-provider
  (:require [teet.ui.material-ui :refer [MuiThemeProvider]]
            [cljs-bean.core :refer [->js]]
            [goog.object :as gobj]))

;;To target disabled buttons :MuiButton {:root {:&$disabled {//css here//}}

(def teet-theme
  {:palette {:primary {:main :#006db5}}                                              ;; {:primary {:main "#ff0000"}}
   :overrides {:MuiDrawer {:paper {:background-color "#041E42"}
                           :paperAnchorDockedLeft {:border-right 0}}
               :MuiDivider {:root {:margin "1rem 0"}}
               :MuiButton {:root {:background-color "#fff"}}}})

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

