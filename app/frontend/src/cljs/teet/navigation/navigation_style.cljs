(ns teet.navigation.navigation-style
  (:require [teet.theme.theme-spacing :as theme-spacing]
            [teet.theme.theme-colors :as theme-colors]
            [teet.common.responsivity-styles :as responsivity-styles]))

(def drawer-width {true 200   ; width when open
                   false 80}) ; width when closed

(defn mobile-drawer
  [open?]
  (with-meta
    (let [w (drawer-width open?)]
      {:min-width (if open?
                    (str w "px")
                    "0px")
       :width (if open?
                (str w "px")
                "0px")
       :transition "all 0.2s ease-in-out"})
    (responsivity-styles/desktop-only-meta
      {:display :none})))

(defn desktop-drawer
  [open?]
  (with-meta
    (let [w (drawer-width open?)]
      {:min-width (str w "px")
       :width (str w "px")
       :transition "all 0.2s ease-in-out"})
    (responsivity-styles/mobile-only-meta
      {:display :none})))


(defn toolbar
  []
  (with-meta
    {:display :flex
     :justify-content :space-around
     :min-height theme-spacing/appbar-height}
    (responsivity-styles/mobile-only-meta {:padding-left 0
                                           :padding-right 0})))

(defn maanteeamet-logo
  []
  {:background-color theme-colors/white
   :display "flex"
   :height "90px"
   :align-items "center"
   :justify-content "center"})

(defn page-listing
  []
  {:padding "0"})

(defn appbar []
  {:display :flex
   :top 0
   :bottom :auto
   :background-color theme-colors/white
   :color theme-colors/gray-dark
   :height theme-spacing/appbar-height
   :box-shadow "0px 2px 4px rgba(0, 0, 0, 0.36)"
   :transition "all 0.2s ease-in-out"})

(def appbar-height "90px")

(defn appbar-position [drawer-open?]
  (with-meta
    {:z-index 10
     :height appbar-height}
    (responsivity-styles/desktop-only-meta
      (let [dw (drawer-width drawer-open?)]
        {:width (str "calc(100% - " dw "px)")
         :margin-left (str dw "px")}))))

(defn main-container [drawer-open?]
  (with-meta
    {:background-color theme-colors/gray-lightest
     :flex 1
     :display :flex
     :position :relative
     :flex-direction :column
     :transition "all 0.2s ease-in-out"}
    (responsivity-styles/desktop-only-meta
      (let [dw (drawer-width drawer-open?)]
        {:width (str "calc(100% - " dw "px)")
         ;:height (str "calc(100vh - " theme-spacing/appbar-height "px)")
         :margin-left (str dw "px")}))))

(defn drawer-projects-style
  []
  {:text-transform :uppercase})

(defn drawer-list-item-style
  [current-page?]
  ^{:pseudo {:last-child {:border-width "1px"}
             :hover {:background-color theme-colors/blue-light}}}
  {:border-color theme-colors/blue-dark
   :border-width "1px 1px 0 1px"
   :border-style "solid"
   :padding "1rem"
   :white-space :nowrap
   :display :flex
   :flex-direction :column
   :align-items :center
   :justify-content :center
   :min-height appbar-height
   :transition "background-color 0.2s ease-in-out"
   :background-color (if current-page?
                       theme-colors/blue-light
                       theme-colors/blue)})

(defn language-select-container-style
  []
  {:display :flex
   :flex-direction :row
   :justify-content :flex-end
   :align-items :center
   :flex-grow 1
   :color theme-colors/gray
   :font-weight 300
   :flex-basis "10%"})

(defn language-select-style
  []
  {:color theme-colors/blue})

(defn divider-style
  []
  (with-meta
    {:border-color theme-colors/gray-lighter
     :border-width "0 1px 0 0"
     :border-style "solid"
     :padding "0 0.5rem 0 0.5rem"}
    (merge {:pseudo {:last-child {:border :none}}}
           (responsivity-styles/mobile-only-meta {:border-width 0
                                                  :padding "0 0.25rem"}))))

(defn logo-style
  []
  {:display :flex
   :flex-direction :row
   :justify-content :flex-start
   :flex-basis "200px"
   :margin-right :auto
   :max-height "100%"})

(defn feedback-container-style []
  (with-meta
    (merge {:display :flex
            :justify-content :center}
           (divider-style))
    (responsivity-styles/mobile-only-meta {:border-width 0})))

(defn navigator-left-panel-style
  []
  {:display :flex
   :flex-direction :column})

(defn feedback-style
  []
  {:display :flex
   :align-items :center})

(defn extra-nav-style
  []
  {:box-shadow "0px 2px 4px rgba(0, 0, 0, 0.36)"
   :background-color theme-colors/white})

(defn extra-nav-element-style
  []
  {:border-bottom (str "1px solid " theme-colors/border-dark)
   :padding "1rem"})

(defn extran-nav-heading-element-style
  []
  {:background-color theme-colors/card-background-extra-light
   :padding "0.5rem"
   :border-bottom (str "1px solid " theme-colors/border-dark)})
