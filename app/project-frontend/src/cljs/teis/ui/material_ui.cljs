(ns teis.ui.material-ui
  (:require [goog.object :as gobj]
            [reagent.core])
  (:require-macros [teis.ui.material-ui-macros :refer [define-mui-components]]))

(defonce MaterialUI (delay
                      (let [mui (gobj/get js/window "MaterialUI" nil)]
                        (when-not mui
                          (.error js/console "No MaterialUI object found in page!"))
                        mui)))

(define-mui-components Card CardActionArea CardActions CardContent CardHeader CardMedia)

(define-mui-components Button Fab IconButton)

(define-mui-components Paper Typography)

(define-mui-components Collapse Divider)
