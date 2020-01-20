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
   :border-bottom (str "2px solid " theme-colors/gray-light)
   :textTransform :uppercase})

(def section-heading-style
  {:fontFamily "Roboto Condensed"
   :fontWeight 700
   :fontSize "1rem"
   :lineHeight 1.375
   :letterSpacing "0.25px"
   :textTransform :uppercase})

(def teet-theme
  {:typography {:body2      {:fontSize "1rem"}
                :fontFamily "Roboto"}
   :palette    {:primary    {:main theme-colors/primary}
                :secondary  {:main theme-colors/secondary}
                :error      {:main theme-colors/error}
                :background {:default theme-colors/white}
                :text       {:primary   theme-colors/primary-text
                             :secondary theme-colors/secondary-text}}
   :overrides  {:MuiAppBar            {:colorDefault   {:background-color theme-colors/white}
                                       :positionSticky {:box-shadow "none"}}
                :MuiCardHeader        {:action {:align-self   :center
                                                :margin-top   0
                                                :margin-right 0}}
                :MuiDialogContentText {:root {:padding          "2rem 1rem"
                                              :background-color theme-colors/gray-lightest}}
                :MuiToolBar           {:root {:min-height "80px"}}     ;This doesn't properly target the toolbar inside appbar
                :MuiFab               {:root {:border-radius "2px"}}
                :MuiFilledInput       {:root  {:border-top-left-radius  0
                                               :border-top-right-radius 0}
                                       :input {:padding "10px"}}
                :MuiInputLabel        {:shrink {:transform   "translate(0, 2.5px)"
                                                :font-weight 300
                                                :font-size   "14px"
                                                :line-height "14px"}}
                :MuiLink              {:button         {:&$focusVisible {:outline "none"}}
                                       :underlineHover {:text-decoration :underline
                                                        :font-size       "1rem"
                                                        "&:hover"        {:text-decoration :none}
                                                        "&:focus"        {:outline    0
                                                                          :box-shadow (str "0 0 0 3px" theme-colors/white ", "
                                                                                           "0 0 0 5px " theme-colors/blue-light)}}}
                :MuiTabs              {:flexContainer {:border-bottom    (str "1px solid " theme-colors/gray-lighter)
                                                       :justify-content  :flex-start
                                                       :padding          "0.25rem 0.25rem 0 0.25rem"
                                                       :background-color theme-colors/gray-lightest}
                                       :indicator     {:display :none}}
                :MuiTab               {:root {:&$textColorPrimary {:min-width      "80px"
                                                                   :text-transform :capitalize
                                                                   :font-size      "0.875rem"
                                                                   :font-weight    "400"
                                                                   :border-width   "0 0 1px 0"
                                                                   :border-style   "solid"
                                                                   :margin-bottom  "-1px" ;;This is done so the selected tab bottom white border overrides the flexcontainer border
                                                                   :border-color   theme-colors/gray-lighter
                                                                   :color          theme-colors/primary}
                                              :&$selected         {:background-color    theme-colors/white
                                                                   :border-width        "1px"
                                                                   :border-style        "solid"
                                                                   :border-bottom-color theme-colors/white
                                                                   :border-color        theme-colors/gray-lighter
                                                                   :font-weight         :bold
                                                                   :border-radius       "4px 4px 0 0"}}}
                :MuiIconButton        {:root {:border-radius "2px"
                                              "&:focus"      theme-colors/focus-style}}
                :MuiButtonBase        {:root {:font-size "1rem"}}
                :MuiButton            {:sizeSmall          {:padding   "0 10px"
                                                            :font-size "0.875rem"}
                                       :containedSecondary {:background-color theme-colors/white
                                                            :border           (str "2px solid " theme-colors/gray)
                                                            :color            theme-colors/gray-dark
                                                            "&:hover"         {:background-color theme-colors/gray-lighter
                                                                               :box-shadow       "none"}}
                                       :contained          {:border-radius "20px"
                                                            "&:focus"      theme-colors/focus-style
                                                            :box-shadow    "none"
                                                            "&:hover"      {:box-shadow "none"}}
                                       :root               {:text-transform :none
                                                            :font-weight    400
                                                            :padding        "4px 1.875rem"
                                                            :font-size      "1rem"
                                                            :white-space    :nowrap
                                                            :max-height     "40px"
                                                            :box-shadow     "none"
                                                            "&:hover"       {:box-shadow "none"}}}
                :MuiDrawer            {:paper                 {:background-color         theme-colors/blue
                                                               :color                    theme-colors/white
                                                               "& .MuiListItemIcon-root" {:color     :inherit
                                                                                          :min-width "40px"}}
                                       :paperAnchorDockedLeft {:border-right 0}}
                ;:MuiDivider {:root {:margin "1rem 0"}}
                :MuiTableHead         {:root data-label-style}
                :MuiTypography        {:h1        {:fontFamily    "Roboto"
                                                   :fontWeight    300
                                                   :fontSize      "2rem"
                                                   :lineHeight    "2.125rem"
                                                   :margin-bottom "1rem"}
                                       :h2        {:fontFamily "Roboto"
                                                   :fontWeight 400
                                                   :fontSize   "1.5rem"
                                                   :lineHeight "1.75rem"}
                                       :h3        {:fontFamily "Roboto"
                                                   :fontWeight 500
                                                   :fontSize   "1.125rem"
                                                   :lineHeight "1.25rem"}
                                       ;; SectionHeading
                                       :h6        section-heading-style
                                       ;; DataLabel
                                       :subtitle1 data-label-style
                                       :subtitle2 {:fontSize    "0.875rem"
                                                   :font-weight 400
                                                   :color       theme-colors/gray}
                                       :body1     {:fontWeight 400
                                                   :fontSize   "1rem"
                                                   :lineHeight 1.5}}

                :MuiTableSortLabel    {:root {:display "inline-block" :width "100%"}
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
