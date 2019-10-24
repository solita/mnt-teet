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
               :MuiInput {:underline {"&::before" {:border :none}}}
               :MuiLink {:underlineHover {:text-decoration :underline
                                          "&:hover" {:text-decoration :none}}}
               :MuiTabs {:indicator {:display :none}}
               :MuiTab {:root {:&$textColorPrimary {:text-transform :capitalize
                                                    :font-size "1rem"
                                                    :font-weight "400"
                                                    :color theme-colors/primary}
                               :&$selected {:background-color theme-colors/white
                                            :border "none"
                                            :font-weight :bold
                                            :border-radius "4px 4px 0 0"}}}
               :MuiIconButton {:root {:border-radius "2px"}}
               :MuiButton {:containedSecondary {:background-color theme-colors/white
                                                :border (str "2px solid " theme-colors/gray)
                                                :color theme-colors/gray-dark
                                                "&:hover" {:background-color theme-colors/gray-lighter}}
                           :contained {:border-radius "20px"
                                       :&$focusVisible {:box-shadow "0 0 0 2px #007BAF"}}
                           :root {:text-transform :capitalize
                                  :font-weight 400
                                  :height "40px"
                                  :&$focusVisible {:box-shadow "0 0 0 2pt #007BAF"}}}
               :MuiDrawer {:paper {:background-color theme-colors/blue
                                   :color theme-colors/white
                                   "& .MuiListItemIcon-root" {:color :inherit
                                                              :min-width "40px"}}
                           :paperAnchorDockedLeft {:border-right 0}}
               ;:MuiDivider {:root {:margin "1rem 0"}}
               :MuiTableHead {:root data-label-style}
               :MuiTypography {:h1 {:fontFamily "Roboto"
                                    :fontWeight 300
                                    :fontSize "2rem"
                                    :lineHeight "2.125rem"
                                    :margin-bottom "1rem"}
                               :h2 {:fontFamily "Roboto"
                                    :fontWeight 400
                                    :fontSize "1.75rem"
                                    :lineHeight "1.3125rem"}
                               :h3 {:fontFamily "Roboto Condensed"
                                    :fontWeight 400
                                    :fontSize "1.5rem"
                                    :lineHeight "1.3125rem"}
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
