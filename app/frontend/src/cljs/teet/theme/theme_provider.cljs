(ns teet.theme.theme-provider
  (:require [teet.ui.material-ui :refer [MuiThemeProvider]]
            [cljs-bean.core :refer [->js]]
            [goog.object :as gobj]))

(def teet-theme
  {:palette {}                                              ;; {:primary {:main "#ff0000"}}
   :overrides {:MuiDrawer {:paper {:background-color "#e0e0e0"}}
               ;:MuiAppBar {:positionFixed {:left "78px"}}
               }})

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

