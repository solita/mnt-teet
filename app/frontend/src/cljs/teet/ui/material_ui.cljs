(ns teet.ui.material-ui
  (:refer-clojure :exclude [List])
  (:require [goog.object :as gobj]
            [reagent.core])
  (:require-macros [teet.ui.material-ui-macros :refer [define-mui-components]]))

(defonce MaterialUI (delay
                      (let [mui (gobj/get js/window "MaterialUI" nil)]
                        (when-not mui
                          (.error js/console "No MaterialUI object found in page!"))
                        mui)))

;; Cards
(define-mui-components Card CardActionArea CardActions CardContent CardHeader CardMedia)

;; Listing
(define-mui-components List ListItem ListItemIcon ListItemText)

(define-mui-components Button Fab IconButton)

(define-mui-components Paper Typography)

;; Common utility components
(define-mui-components Collapse Divider CircularProgress Drawer AppBar MuiThemeProvider Toolbar)

(define-mui-components TextField)

;; Layout
(define-mui-components Container Grid)

;; Data display
(define-mui-components Chip Avatar)
