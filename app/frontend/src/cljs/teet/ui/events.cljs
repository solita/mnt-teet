(ns teet.ui.events
  "Common event handler mixins for react components."
  (:require [reagent.dom :as rd]))

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
                            (reset! root (rd/dom-node this))
                            (.addEventListener js/document.body "click" handler))
     :component-will-unmount (fn [_]
                               (.removeEventListener js/document.body "click" handler))}))
