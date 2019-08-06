(ns teet.ui.events
  "Common event handler mixins for react components."
  (:require [reagent.core :as r]))

(defn click-outside
  "Handle mouse click outside of the current component."
  [callback]
  (let [root (atom nil)
        handler (fn [e]
                  (loop [n (.-target e)]
                    (if (nil? n)
                      ;; DOM root was reached without finding the component root
                      (callback)
                      (when-not (= n @root)
                        (recur (.-parentNode n))))))]
    {:component-did-mount (fn [this]
                            (reset! root (r/dom-node this))
                            (.addEventListener js/document.body "click" handler))
     :component-will-unmount (fn [this]
                               (.removeEventListener js/document.body "click" handler))}))
