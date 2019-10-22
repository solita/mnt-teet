(ns teet.theme.theme-provider
  (:require [teet.ui.material-ui :refer [MuiThemeProvider]]
            [cljs-bean.core :refer [->js]]
            [goog.object :as gobj]
            [teet.theme.theme-colors :as theme-colors]
            [reagent.core :as r]))

;;To target disabled buttons :MuiButton {:root {:&$disabled {//css here//}}

(def data-label-style
  {:fontFamily "Roboto Condensed"
   :fontWeight 400
   :fontSize "0.875rem"
   :lineHeight 1.375
   :letterSpacing "0.25px"
   :color theme-colors/secondary-text
   :background-color theme-colors/gray-lightest
   :textTransform :uppercase})

(def section-heading-style
  {:fontFamily "Roboto Condensed"
   :fontWeight 700
   :fontSize "1rem"
   :lineHeight 1.375
   :letterSpacing "0.25px"
   :textTransform :uppercase})

(def teet-theme
  {:typography {:fontFamily "Roboto"}
   :palette {:primary {:main theme-colors/primary}
             :secondary {:main theme-colors/secondary}
             :error {:main theme-colors/error}
             :background {:default theme-colors/white}
             :text {:primary theme-colors/primary-text
                    :secondary theme-colors/secondary-text}}
   :overrides {:MuiAppBar {:colorDefault {:background-color theme-colors/white}
                           :positionSticky {:box-shadow "none"}}
               :MuiToolBar {:root {:min-height "80px"}}     ;This doesn't properly target the toolbar inside appbar
               :MuiFab {:root {:border-radius "2px"}}
               :MuiIconButton {:root {:border-radius "2px"}}
               :MuiButton {:contained {:border-radius "20px"
                                       :&$focusVisible {:box-shadow "0 0 0 2px #007BAF"}}
                           :root {:&$focusVisible {:box-shadow "0 0 0 2pt #007BAF"}}}
               :MuiDrawer {:paper {:background-color theme-colors/blue
                                   :color theme-colors/white
                                   "& .MuiListItemIcon-root" {:color :inherit
                                                              :min-width "40px"}}
                           :paperAnchorDockedLeft {:border-right 0}}
               ;:MuiDivider {:root {:margin "1rem 0"}}
               :MuiTableHead {:root data-label-style}
               :MuiTypography {:h1 {:fontFamily "Roboto Condensed"
                                    :fontWeight 700
                                    :fontSize "2rem"
                                    :lineHeight 1.25
                                    :margin-bottom "1rem"}
                               :h2 {:fontFamily "Roboto Condensed"
                                    :fontWeight 700
                                    :fontSize "1.5rem"
                                    :lineHeight 1.25}
                               :h3 {:fontFamily "Roboto Condensed"
                                    :fontWeight 700
                                    :fontSize "1.125rem"
                                    :lineHeight 1.25}
                               ;; SectionHeading
                               :h6 section-heading-style
                               ;; DataLabel
                               :subtitle1 data-label-style
                               :body1 {:fontWeight 400
                                       :fontSize "1rem"
                                       :lineHeight 1.5}}

               :MuiTableSortLabel {:root {:display "inline-block" :width "100%"}
                                   :icon {:float "right"}}}})

(defn- create-theme [theme]
  (let [create-mui-theme (-> js/window
                           (gobj/get "MaterialUI")
                           (gobj/get "createMuiTheme"))]
    (-> theme
      ->js
      create-mui-theme)))

(defn theme-provider [content]
  (r/with-let [theme (create-theme teet-theme)]
    [MuiThemeProvider
     {:theme theme}
     content]))
