(ns teet.ui.material-ui
  (:refer-clojure :exclude [List])
  (:require [goog.object :as gobj]
            reagent.core

            ;; Require all MaterialUI components with "mui-" prefix
            ;; the define-mui-components will adapt them and provide
            ;; wrapper functions that can be used directly from reagent

            ["@material-ui/core/Card" :as mui-Card]
            ["@material-ui/core/CardActionArea" :as mui-CardActionArea]
            ["@material-ui/core/CardActions" :as mui-CardActions]
            ["@material-ui/core/CardContent" :as mui-CardContent]
            ["@material-ui/core/CardHeader" :as mui-CardHeader]
            ["@material-ui/core/CardMedia" :as mui-CardMedia]
            ["@material-ui/core/List" :as mui-List]
            ["@material-ui/core/ListItem" :as mui-ListItem]
            ["@material-ui/core/ListItemIcon" :as mui-ListItemIcon]
            ["@material-ui/core/ListItemText" :as mui-ListItemText]
            ["@material-ui/core/ListItemSecondaryAction" :as mui-ListItemSecondaryAction]
            ["@material-ui/core/Button" :as mui-Button]
            ["@material-ui/core/ButtonBase" :as mui-ButtonBase]
            ["@material-ui/core/Fab" :as mui-Fab]
            ["@material-ui/core/IconButton" :as mui-IconButton]
            ["@material-ui/core/Checkbox" :as mui-Checkbox]
            ["@material-ui/core/InputAdornment" :as mui-InputAdornment]
            ["@material-ui/core/FormControl" :as mui-FormControl]
            ["@material-ui/core/FormControlLabel" :as mui-FormControlLabel]
            ["@material-ui/core/InputLabel" :as mui-InputLabel]
            ["@material-ui/core/Input" :as mui-Input]
            ["@material-ui/core/Select" :as mui-Select]
            ["@material-ui/core/MenuItem" :as mui-MenuItem]
            ["@material-ui/core/MenuList" :as mui-MenuList]
            ["@material-ui/core/Menu" :as mui-Menu]
            ["@material-ui/core/ButtonGroup" :as mui-ButtonGroup]
            ["@material-ui/core/RadioGroup" :as mui-RadioGroup]
            ["@material-ui/core/Radio" :as mui-Radio]
            ["@material-ui/core/Paper" :as mui-Paper]
            ["@material-ui/core/Typography" :as mui-Typography]
            ["@material-ui/core/Divider" :as mui-Divider]
            ["@material-ui/core/CircularProgress" :as mui-CircularProgress]
            ["@material-ui/core/LinearProgress" :as mui-LinearProgress]
            ["@material-ui/core/Drawer" :as mui-Drawer]
            ["@material-ui/core/AppBar" :as mui-AppBar]
            ["@material-ui/core/Toolbar" :as mui-Toolbar]
            ["@material-ui/core/CssBaseline" :as mui-CssBaseline]
            ["@material-ui/core/Link" :as mui-Link]
            ["@material-ui/core/Breadcrumbs" :as mui-Breadcrumbs]
            ["@material-ui/core/ClickAwayListener" :as mui-ClickAwayListener]
            ["@material-ui/core/Switch" :as mui-Switch]
            ["@material-ui/core/Container" :as mui-Container]
            ["@material-ui/core/Grid" :as mui-Grid]
            ["@material-ui/core/Chip" :as mui-Chip]
            ["@material-ui/core/Avatar" :as mui-Avatar]
            ["@material-ui/core/Icon" :as mui-Icon]
            ["@material-ui/core/Tabs" :as mui-Tabs]
            ["@material-ui/core/Tab" :as mui-Tab]
            ["@material-ui/core/Table" :as mui-Table]
            ["@material-ui/core/TableRow" :as mui-TableRow]
            ["@material-ui/core/TableHead" :as mui-TableHead]
            ["@material-ui/core/TableCell" :as mui-TableCell]
            ["@material-ui/core/TableSortLabel" :as mui-TableSortLabel]
            ["@material-ui/core/TableBody" :as mui-TableBody]
            ["@material-ui/core/TableFooter" :as mui-TableFooter]
            ["@material-ui/core/Zoom" :as mui-Zoom]
            ["@material-ui/core/Fade" :as mui-Fade]
            ["@material-ui/core/Collapse" :as mui-Collapse]
            ["@material-ui/core/Modal" :as mui-Modal]
            ["@material-ui/core/Dialog" :as mui-Dialog]
            ["@material-ui/core/DialogActions" :as mui-DialogActions]
            ["@material-ui/core/Backdrop" :as mui-Backdrop]
            ["@material-ui/core/DialogContentText" :as mui-DialogContentText]
            ["@material-ui/core/DialogTitle" :as mui-DialogTitle]
            ["@material-ui/core/DialogContent" :as mui-DialogContent]
            ["@material-ui/core/Popover" :as mui-Popover]
            ["@material-ui/core/Popper" :as mui-Popper]
            ["@material-ui/core/Snackbar" :as mui-Snackbar]
            ["@material-ui/core/SnackbarContent" :as mui-SnackbarContent]
            ["@material-ui/core/Badge" :as mui-Badge]
            ["@material-ui/core/TextField" :as mui-TextField]
            ["@material-ui/lab/Autocomplete" :as mui-Autocomplete])

  (:require-macros [teet.ui.material-ui-macros :refer [define-mui-components]]))

;; Cards
(define-mui-components Card CardActionArea CardActions CardContent CardHeader CardMedia)

;; Listing
(define-mui-components List ListItem ListItemIcon ListItemText ListItemSecondaryAction)

;; Form
(define-mui-components
  Button ButtonBase Fab IconButton Checkbox InputAdornment FormControl FormControlLabel
  InputLabel Input TextField
  Select MenuItem MenuList Menu ButtonGroup)

(def TextField-class (aget mui-TextField "default"))

(define-mui-components RadioGroup Radio)

(define-mui-components Paper Typography)

;; Common utility components
(define-mui-components Divider CircularProgress LinearProgress Drawer
                       AppBar Toolbar CssBaseline Link Breadcrumbs
                       ClickAwayListener Switch)

;; Layout
(define-mui-components Container Grid)

;; Data display
(define-mui-components Chip Avatar)

;; Icon
(define-mui-components Icon)

;; Tabs
(define-mui-components Tabs Tab)

;; Table
(define-mui-components Table TableRow TableHead TableCell TableSortLabel TableBody TableFooter)

;; Transitions
(define-mui-components Zoom Fade Collapse)

;; Dialogs and modals
(define-mui-components Modal Dialog DialogActions Backdrop DialogContentText DialogTitle DialogContent Popover Popper)

;; Snackbar
(define-mui-components Snackbar SnackbarContent)

;; Badge
(define-mui-components Badge)

;; Labs components
(define-mui-components Autocomplete)
