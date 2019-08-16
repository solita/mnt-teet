(ns teet.ui.hotkeys
  "Handle and store keyboard shortcuts"
  (:require [clojure.string :as str]
            [reagent.impl.component :as util]
            [reagent.core :as reagent]))

(defonce ^{:doc "Currently registered hotkeys (key => '(handler fns))"}
  hotkey-registry
  (atom {}))

(defonce ^{:doc "Keys that are currently down"}
  hotkey-down-keys
  (atom #{}))

(declare hotkey)

(defn set-on-key-press [this focus-element-name on-key-press]
  (let [ref (some-> this .-refs (aget focus-element-name))]
  (when ref
    (when (not (.-tabIndex ref))
      (set! (.-tabIndex ref) 0)))
  on-key-press))

(defn update-or-assoc-hotkey-registry [this key on-key-press focus-element-name]
    (swap! hotkey-registry update key (fnil conj []) (set-on-key-press this focus-element-name on-key-press)))

(defn assoc-hotkey-registry [this key on-key-press focus-element-name]
   (swap! hotkey-registry assoc key [(set-on-key-press this focus-element-name on-key-press)]))

(defn hotkey
  "Returns a mixin that will respond to the given hotkey while this component is mounted.
  Must be used with r/create-class."
  ([key on-key-press] (hotkey key on-key-press nil))
  ([key on-key-press focus-element-name]
   {:component-did-mount (fn
                           [this]
                           (update-or-assoc-hotkey-registry this key on-key-press focus-element-name))
    :component-will-unmount #(swap! hotkey-registry update key (fn [key-handlers]
                                                                 (if (seq key-handlers)
                                                                   (pop key-handlers)
                                                                   key-handlers)))
    :component-will-receive-props (fn
                                    [this new-argv]
                                    (let [current-props (reagent/props this)
                                          new-props (util/extract-props new-argv)
                                          comparison-prop (if (some? (:disabled? current-props))
                                                            :disabled?
                                                            :hotkey-refresh-on)]
                                      (if (not= (comparison-prop current-props) (comparison-prop new-props))
                                        (assoc-hotkey-registry this key (:on-click new-props) focus-element-name))))}))


(defonce hotkey-disallow #{"INPUT" "SELECT" "TEXTAREA"})
(def space-key 32)
(def escape 27)

(defn- hotkey-disallowed?
  "Returns true when hotkey is disallowed
  When focus is in certain elements and user is manipulating the input, hotkeys get in the way. Prevent hotkeys being triggered when that is the case.
  SELECT tag is a special case. When doing position or sector measurements space key is the way actual measurement is done. The default behaviour
  of space in the select tag doesn't make sense in this context, so space hotkeys are let through."
  [active-tag kc]
  (if (or
        (and (= active-tag "SELECT")
          (= kc space-key))
        (= kc escape))
    false
    (some? (hotkey-disallow active-tag))))

(defonce hotkey-handler
  (do
    (set! (.-onkeydown js/window)
          (fn [event]
            (when (not (.-ctrlKey event))
              (let [kc (.-keyCode event)]
                (when (and (not (hotkey-disallowed? (some-> js/document .-activeElement .-tagName) kc))
                           (not (@hotkey-down-keys kc)))
                  (swap! hotkey-down-keys conj kc)
                  (let [hotkey-registry @hotkey-registry
                        key (.-key event)]
                    (when-let [handler-fn (or (peek (get hotkey-registry key))
                                              (peek (get hotkey-registry (str/upper-case key))))]
                      (.preventDefault event)
                      (handler-fn))))))))
    (set! (.-onkeyup js/window)
          (fn [event]
            (when (not (.-ctrlKey event))
              (swap! hotkey-down-keys disj (.-keyCode event)))))))
