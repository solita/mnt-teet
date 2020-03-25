(ns teet.ui.context
  "Helpers to use React context"
  (:require [reagent.core :as r]))

(defonce contexts (atom {}))

(defn- get-or-create-context [name]
  (-> contexts
      (swap! (fn [contexts]
               (if (contains? contexts name)
                 contexts
                 (assoc contexts name (js/React.createContext nil)))))
      (get name)))

(defn provide
  "Provide the given named context value to child components."
  [context-name context-value child]
  (r/create-element (aget (get-or-create-context context-name) "Provider")
                    #js {:value context-value}
                    (r/as-element child)))

(defn consume
  "Consume the given named context. Takes context name
  and calls component with the context provided value."
  [context-name component-fn]
  (r/create-element (aget (get-or-create-context context-name) "Consumer")
                    nil
                    (fn [ctx]
                      (r/as-element
                       [component-fn ctx]))))
