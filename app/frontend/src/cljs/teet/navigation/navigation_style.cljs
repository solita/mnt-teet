(ns teet.navigation.navigation-style
  (:require [teet.theme.theme-spacing :as theme-spacing]
            [teet.theme.theme-colors :as theme-colors]
            [teet.common.responsivity-styles :as responsivity-styles]))

(def drawer-width {true 200   ; width when open
                   false 80}) ; width when closed

(def mobile-search-height "3.125rem")

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
    {:display         :flex
     :justify-content :space-around
     :min-height      theme-spacing/appbar-height}
    (responsivity-styles/mobile-only-meta
      {:height theme-spacing/appbar-height-mobile
       :min-height theme-spacing/appbar-height-mobile
       :padding-left ".4rem"
       :padding-right ".4rem"})))

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
  (with-meta
    {:display :flex
      :top 0
      :bottom :auto
      :background-color theme-colors/white
      :color theme-colors/gray-dark
      :height theme-spacing/appbar-height
      :box-shadow "0px 2px 4px rgba(0, 0, 0, 0.36)"
      :transition "all 0.2s ease-in-out"}
    (responsivity-styles/mobile-only-meta
      {:height theme-spacing/appbar-height-mobile})
    ))

(defn appbar-position [drawer-open?]
  (with-meta
    {:z-index 10
     :height theme-spacing/appbar-height-mobile}
    (responsivity-styles/desktop-only-meta
      (let [dw (drawer-width drawer-open?)]
        {:width (str "calc(100% - " dw "px)")
         :height theme-spacing/appbar-height
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
   :min-height theme-spacing/appbar-height
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
  ^{:pseudo {:focus {:min-width "3rem" :width :auto}}}
  {:color               theme-colors/blue
   :width               :auto
   :background-position "right .2em top 50% !important"
   :padding-right       "1.2rem !important"})

(defn divider-style
  []
  (with-meta
    {:border-color theme-colors/gray-lighter
     :border-width "0 1px 0 0"
     :border-style "solid"
     :padding "0 1rem 0 1rem"}
    (merge {:pseudo {:last-child {:border :none}}}
           (responsivity-styles/mobile-only-meta {:border-width 0
                                                  :padding "0"}))))

(defn open-account-navigation-style
  []
  (with-meta
    {}
    (responsivity-styles/desktop-only-meta
      {:padding-left "1rem"}))
  )
(defn logo-style
  []
  (with-meta
    {:display :flex
     :flex-direction :row
     :justify-content :flex-start
     :flex-basis "200px"
     :margin-right :auto
     :height "100%"}
    (responsivity-styles/mobile-only-meta
      {:margin-left ".2rem"
       :flex-basis :auto})))

(defn logo-shield-style []
  (with-meta
    {:max-height "100%"
     :width :auto
     :height "100%"}
    (responsivity-styles/mobile-only-meta
      {:max-height "110%"
       :height "110%"
       :margin-top "-0.2rem"})))

(defn feedback-container-style []
  (with-meta
    (merge {:display :flex
            :justify-content :center}
           (divider-style))
    (responsivity-styles/mobile-only-meta {:display :none})))

(defn navigator-left-panel-style
  []
  {:display :flex
   :flex-direction :column
   :max-height "100%"})

(defn feedback-style
  []
  {:display :flex
   :align-items :center})

(defn extra-nav-style
  []
  {:box-shadow "0px 2px 4px rgba(0, 0, 0, 0.36)"
   :background-color theme-colors/white})

(defn extra-nav-element-style
  [last-item?]
  ^{:pseudo {:after {:content "''"
                     :height "1px"
                     :position :absolute
                     :bottom "0"
                     :right "0"
                     :left "3.4rem"
                     :display :inline-block
                     :background-color (if last-item?
                                         theme-colors/white
                                         theme-colors/border-dark)}}}
  {:padding-left "1rem"
   :height (if last-item?
             "3.18rem"
             "3.1rem")
   :display :flex
   :position :relative})

(defn extra-nav-search-container-style
  []
  (with-meta
    {}
    (responsivity-styles/mobile-only-meta
      {:height mobile-search-height})))

(defn search-input-style
  []
  (with-meta
    {}
    (responsivity-styles/mobile-only-meta
      {:height mobile-search-height
       :width "100%"
       :border :none
       :border-radius "0"
       :outline :none
       :border-top (str "1px solid " theme-colors/border-dark)
       :border-bottom (str "1px solid " theme-colors/border-dark)})))

(defn extra-nav-heading-element-style
  []
  {:background-color theme-colors/card-background-extra-light
   :height "2.25rem"
   :display :flex
   :align-items :center
   :padding-left "1rem"
   :border-top (str "1px solid " theme-colors/border-dark)
   :border-bottom (str "1px solid " theme-colors/border-dark)})
