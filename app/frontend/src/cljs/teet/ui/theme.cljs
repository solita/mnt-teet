(ns teet.ui.theme
  "Generate Material UI theme"
  (:require [teet.ui.material-ui :refer [MuiThemeProvider]]
            [cljs-bean.core :refer [->js]]))

(def teet-theme
  {:palette {:primary {:main "#ff0000"}}
   :overrides {:MuiDrawer {:paper {:background-color "#e0e0e0"}}}})

(defn- create-theme [theme]
  (js/MaterialUI.createMuiTheme
   (->js theme)))

(defn theme-provider [content]
  [MuiThemeProvider
   {:theme (create-theme teet-theme)}
   content])
