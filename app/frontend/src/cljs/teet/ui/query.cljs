(ns teet.ui.query
  "Query view. Component that fetches a named query from the server."
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [tuck.core :as t]
            [teet.ui.material-ui :refer [CircularProgress]]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]))

(defrecord Query [query args state-path])
(defrecord QueryResult [state-path result])
(defrecord Cleanup [state-path])

(extend-protocol t/Event
  Query
  (process-event [{:keys [query args state-path]} app]
    (t/fx app
          {:tuck.effect/type :query
           :query query
           :args args
           :method "GET"
           :result-event (partial ->QueryResult state-path)}))

  QueryResult
  (process-event [{:keys [state-path result]} app]
    (assoc-in app state-path result))

  Cleanup
  (process-event [{state-path :state-path} app]
    (assoc-in app state-path nil)))


(defn query
  "Component that does a datomic query and shows view with the resulting data."
  [{:keys [e! query args state-path refresh]}]
  (let [refresh-value (atom refresh)]
    (e! (->Query query args state-path))
    (r/create-class
      {:component-will-unmount (e! ->Cleanup state-path)
       :reagent-render
       (fn [{:keys [e! query args state-path skeleton view app state refresh breadcrumbs]}]
         (when (not= @refresh-value refresh)
           (reset! refresh-value refresh)
           (e! (->Query query args state-path)))
         (if state
           ;; Results loaded, call the view
           ^{:key "query-result-view"}
           [view e! app state breadcrumbs]

           ;; Results not loaded, show skeleton or loading spinner
           (if skeleton
             skeleton
             [:div {:class (<class common-styles/spinner-style)}
              [CircularProgress]])))})))

(defn rpc
  "Component that does an PostgREST RPC call and shows view with the resulting data."
  [{:keys [e! rpc args state-path]}]
  (e! (common-controller/->RPC {:rpc rpc
                                :args args
                                :result-path state-path
                                :loading-path state-path}))
  (r/create-class
   {:component-will-unmount (e! ->Cleanup state-path)
    :reagent-render
    (fn [{:keys [e! state-path view app skeleton]}]
      (let [state (get-in app state-path)]
        (if (:loading? state)
          (or skeleton [CircularProgress])
          [view e! state])))}))
