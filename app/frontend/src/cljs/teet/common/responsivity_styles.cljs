(ns teet.common.responsivity-styles
  (:require [teet.theme.theme-colors :as theme-colors]
            [reagent.core :as r]))

(def ^{:const true
       :doc "Minimum browser window width that is considered wide for layout purposes."}
  wide-display-cutoff-width 2200)

(def ^:const desktop-cutoff-width 1024)

(def ^:const desktop-breakpoint (str desktop-cutoff-width "px"))
(def ^:const mobile-breakpoint (str (- desktop-cutoff-width 1) "px"))

(defonce window-width
         (let [width (r/atom js/document.body.clientWidth)]
           (set! (.-onresize js/window)
                 (fn [_]
                   (reset! width js/document.body.clientWidth)))
           width))

(defn wide-display? []
  (>= @window-width wide-display-cutoff-width))

(defn mobile?
  []
  (>= desktop-cutoff-width @window-width))

(defn mobile-only-meta
  [style]
  {:media {{:screen :only :max-width mobile-breakpoint} style}})

(defn desktop-only-meta
  [style]
  {:media {{:screen :only :min-width desktop-breakpoint} style}})

(defn desktop-only-style
  ([desktop-only]
   (with-meta
     {}
     (desktop-only-meta desktop-only)))
  ([desktop-only general]
   (with-meta
     general
     (desktop-only-meta desktop-only))))

(defn mobile-only-style
  ([mobile-only]
   (with-meta
     {}
     (mobile-only-meta mobile-only)))
  ([mobile-only general]
   (with-meta
     general
     (mobile-only-meta mobile-only))))

(defn visible-mobile-only
  []
  (with-meta
    {}
    (desktop-only-meta {:display :none})))

(defn visible-desktop-only
  []
  (with-meta
    {}
    (mobile-only-meta {:display :none})))

(defn mobile-navigation-button
  []
  (with-meta
    {:color theme-colors/primary}
    (desktop-only-meta {:display :none})))
