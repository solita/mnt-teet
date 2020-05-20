(ns teet.ui.context
  "Helpers to use React context"
  (:require [reagent.core :as r]
            react))

(defonce contexts (atom {}))

(defn- get-or-create-context [name]
  (-> contexts
      (swap! (fn [contexts]
               (if (contains? contexts name)
                 contexts
                 (assoc contexts name (react/createContext nil)))))
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
                       (if (vector? component-fn)
                         (conj component-fn ctx)
                         [component-fn ctx])))))

(defn- context-update [context-name component update-fn ctx]
  (provide context-name
           (update-fn ctx)
           component))

(defn update-context
  "Consume and provide an updated context"
  [context-name update-fn component]
  (consume context-name [context-update context-name component update-fn]))
