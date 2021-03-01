(ns teet.common.responsivity-styles
  (:require [teet.theme.theme-colors :as theme-colors]))

(def ^:const desktop-breakpoint "1024px")

(defn mobile-only-meta
  [style]
  {:media {{:screen :only :max-width desktop-breakpoint} style}})

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
