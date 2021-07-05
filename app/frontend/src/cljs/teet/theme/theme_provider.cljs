(ns teet.theme.theme-provider
  (:require [cljs-bean.core :refer [->js]]
            [goog.object :as gobj]
            [teet.theme.theme-colors :as theme-colors]
            [reagent.core :as r]
            ["@material-ui/core/styles" :as styles]
            [teet.common.common-styles :as common-styles]))

;;To target disabled buttons :MuiButton {:root {:&$disabled {//css here//}}

(def data-label-style
  {:fontFamily    "Roboto Condensed"
   :fontWeight    400
   :fontSize      "0.875rem"
   :lineHeight    1.375
   :letterSpacing "0.25px"
   :color         theme-colors/secondary-text
   :border-bottom (str "2px solid " theme-colors/gray-light)
   :textTransform :uppercase})

(def section-heading-style
  {:fontWeight    700
   :fontSize      "1rem"
   :lineHeight    1.375
   :letterSpacing "0.25px"})

(def teet-theme
  {:breakpoints {:values {:xs 0
                          :sm 600
                          :md 1024
                          :lg 1280
                          :xl 1920}}
   :typography {:body2 {:fontSize "1rem"}
                :fontFamily "Roboto"}
   :palette {:primary {:main theme-colors/primary}
             :secondary {:main theme-colors/secondary}
             :error {:main theme-colors/error}
             :background {:default theme-colors/white}
             :text {:primary theme-colors/primary-text
                    :secondary theme-colors/secondary-text}}
   :overrides {:MuiAppBar {:colorDefault {:background-color theme-colors/white}
                           :positionSticky {:box-shadow "none"}}
               :MuiCardHeader {:action {:align-self :center
                                        :margin-top 0
                                        :margin-right 0}}
               :MuiDialogContentText {:root {:padding "2rem 1rem"
                                             :background-color theme-colors/gray-lightest}}
               :MuiChip {:sizeSmall {:height "20px"}
                         :labelSmall {:padding-left "6px"
                                      :padding-right "6px"}}
               :MuiToolBar {:root {:min-height "80px"}} ;This doesn't properly target the toolbar inside appbar
               :MuiFab {:root {:border-radius "2px"}}
               :MuiFilledInput {:root {:border-top-left-radius 0
                                       :border-top-right-radius 0}
                                :input {:padding "10px"}}
               :MuiInputLabel {:shrink {:transform "translate(0, 2.5px)"
                                        :font-weight 300
                                        :font-size "14px"
                                        :line-height "14px"}}
               :MuiTabs {:root {:background-color theme-colors/gray-lighter}
                         :flexContainer {:border-bottom (str "1px solid " theme-colors/gray-lighter)
                                         :justify-content :flex-start
                                         :padding "0.25rem 0.25rem 0 0.25rem"
                                         :background-color theme-colors/gray-lightest}
                         :scrollable {:background-color theme-colors/gray-lightest}
                         :indicator {:display :none}
                         :scrollButtons {:background-color theme-colors/gray-lighter
                                         :border-width "0 0 1px 0"
                                         :border-style "solid"
                                         :border-color theme-colors/gray-lighter
                                         :color theme-colors/primary}}
               :scrollButtons {"&:disabled" {:outline "1px solid red"}}
               :MuiTab {:root {:&$textColorPrimary {:min-width "80px"
                                                    :text-transform :capitalize
                                                    :font-size "0.875rem"
                                                    :font-weight "400"
                                                    :border-width "0 0 1px 0"
                                                    :border-style "solid"
                                                    :margin-bottom "-1px" ;;This is done so the selected tab bottom white border overrides the flexcontainer border
                                                    :border-color theme-colors/gray-lighter
                                                    :color theme-colors/primary}
                               :&$selected {:background-color theme-colors/white
                                            :border-width "1px"
                                            :border-style "solid"
                                            :border-bottom-color theme-colors/white
                                            :border-color theme-colors/gray-lighter
                                            :font-weight :bold
                                            :border-radius "4px 4px 0 0"}}}
               :MuiIconButton {:root {:border-radius "2px"
                                      "&:focus" theme-colors/button-focus-style}}
               :MuiButtonBase {:root {:font-size "1rem"}}
               :MuiLink {:root common-styles/link-1
                         :button (merge
                                   common-styles/link-1
                                   {"&:hover" {:text-decoration :none}})}
               :MuiButton {:sizeSmall {:padding "0 1rem"
                                       :line-height "1.125rem"
                                       :font-size "0.75rem"}

                           :outlined {:border (str "1px solid " theme-colors/white)
                                      :color theme-colors/white
                                      "&:hover" {:background-color theme-colors/gray-dark}
                                      "&:active" {:background-color "#000"}
                                      "&:focus" theme-colors/button-focus-style}
                           :containedSecondary {:background-color theme-colors/white
                                                :border (str "2px solid " theme-colors/gray)
                                                :color theme-colors/gray-dark
                                                "&:hover" {:background-color theme-colors/gray-lighter
                                                           :box-shadow "none"}
                                                "&:disabled" {:background-color theme-colors/gray-lightest
                                                              :border-color theme-colors/gray-lighter}}
                           :contained {:border-radius "20px"
                                       "&:focus" theme-colors/button-focus-style
                                       :box-shadow "none"
                                       "&:hover" {:box-shadow "none"}}
                           :containedPrimary {:border (str "2px solid " theme-colors/primary)
                                              "&:disabled" {:opacity "0.8"
                                                            :background-color theme-colors/primary
                                                            :color theme-colors/white}}
                           :containedSizeLarge {:border-radius "1.5rem"
                                                :font-size "1rem"
                                                :height "3rem"
                                                :padding "0 1.5rem"}
                           :root {:text-transform :none
                                  :font-weight 400
                                  :padding "4px 1.875rem"
                                  :font-size "1rem"
                                  :white-space :nowrap
                                  :max-height "50px"
                                  :box-shadow "none"
                                  "&:hover" {:box-shadow "none"}}}
               :MuiDrawer {:paper {:background-color theme-colors/blue
                                   :color theme-colors/white
                                   "& .MuiListItemIcon-root" {:color :inherit
                                                              :min-width "40px"}}
                           :paperAnchorDockedLeft {:border-right 0}}
               :MuiDivider {:light {:background-color theme-colors/white}}
               :MuiTableHead {:root data-label-style}

               :MuiTableSortLabel {:root {:display "inline-block" :width "100%"}
                                   :icon {:float "right"}}
               :MuiOutlinedInput {:root {:border-radius "2px"
                                         :border (str "1px solid " theme-colors/gray-light)
                                         :background-color theme-colors/white
                                         :max-height "43px"}
                                  :notchedOutline {:border :none}}}})

(defn- create-theme [theme]
  (styles/createMuiTheme (->js theme)))


(def ThemeProvider (r/adapt-react-class styles/ThemeProvider))

(defonce teet-theme-instance (create-theme teet-theme))

(defn theme-provider [content]
  [ThemeProvider
   {:theme teet-theme-instance}
   content])
