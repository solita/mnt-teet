(ns teet.ui.material-ui
  (:refer-clojure :exclude [List])
  (:require [goog.object :as gobj]
            [reagent.core :as r]
            [herb.core :as herb :refer [<class]]
            [reagent.impl.template :as rtpl]
            [teet.theme.theme-colors :as theme-colors])
  (:require-macros [teet.ui.material-ui-macros :refer [define-mui-components]]))

(defonce MaterialUI (delay
                      (let [mui (gobj/get js/window "MaterialUI" nil)]
                        (when-not mui
                          (.error js/console "No MaterialUI object found in page!"))
                        mui)))

;; Cards
(define-mui-components Card CardActionArea CardActions CardContent CardHeader CardMedia)

;; Listing
(define-mui-components List ListItem ListItemIcon ListItemText ListItemSecondaryAction)

;; Form
(define-mui-components
  Button ButtonBase Fab IconButton Checkbox InputAdornment FormControl FormControlLabel
  InputLabel Input
  Select MenuItem Menu ButtonGroup)

(define-mui-components Paper Typography)

;; Common utility components
(define-mui-components Collapse Divider CircularProgress LinearProgress Drawer
                       AppBar MuiThemeProvider Toolbar CssBaseline Link Breadcrumbs
                       ClickAwayListener Switch)

;; Layout
(define-mui-components Container Grid)

;; Data display
(define-mui-components Chip Avatar)

;; Icon
(define-mui-components Icon)

;; Tabs
(define-mui-components Tabs Tab TabPanel)

;; Table
(define-mui-components Table TableRow TableHead TableCell TableSortLabel TableBody TableFooter)

;; Transitions
(define-mui-components Zoom Fade)

;; Dialogs and modals
(define-mui-components Dialog DialogTitle DialogContent Popover)

;; Snackbar
(define-mui-components Snackbar SnackbarContent)

;; monkey patch based on this https://github.com/reagent-project/reagent/blob/master/doc/examples/material-ui.md
(def ^:private input-component
  (r/reactify-component
    (fn [props]
      [:input (-> props
                  (assoc :ref (:inputRef props))
                  (dissoc :inputRef))])))

(def ^:private textarea-component
  (r/reactify-component
    (fn [props]
      [:textarea (-> props
                     (assoc :ref (:inputRef props))
                     (dissoc :inputRef))])))

#_(defn TextField
    [props & children]
    (let [mui-text (delay
                     (goog.object/get @MaterialUI "TextField"))
          props (-> props
                    (assoc-in [:InputProps :inputComponent] (cond
                                                              (and (:multiline props) (:rows props) (not (:maxRows props)))
                                                              textarea-component

                                                              ;; FIXME: autosize multiline field is broken
                                                              (:multiline props)
                                                              nil

                                                              :else
                                                              input-component))
                    rtpl/convert-prop-value)]
      (apply r/create-element @mui-text props (map r/as-element children))))
