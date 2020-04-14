(ns teet.ui.query
  "Query view. Component that fetches a named query from the server."
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [tuck.core :as t]
            [teet.ui.material-ui :refer [CircularProgress]]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]))

(defrecord Query [query args state-path state-atom])
(defrecord QueryResult [state-path state-atom result])
(defrecord Cleanup [state-path])

(extend-protocol t/Event
  Query
  (process-event [{:keys [query args state-path state-atom]} app]
    (t/fx app
          {:tuck.effect/type :query
           :query query
           :args args
           :method "GET"
           :result-event (partial ->QueryResult state-path state-atom)}))

  QueryResult
  (process-event [{:keys [state-path state-atom result]} app]
    (if state-path
      (assoc-in app state-path result)
      (do
        (reset! state-atom result)
        app)))

  Cleanup
  (process-event [{state-path :state-path} app]
    (if state-path
      (assoc-in app state-path nil)
      app)))


(defn query
  "Component that does a datomic query and shows view with the resulting data."
  [{:keys [e! query args state-path refresh]}]
  (let [refresh-value (atom refresh)
        previous-args (atom args)
        state-atom (when-not state-path
                     (r/atom nil))]
    (e! (->Query query args state-path state-atom))
    (r/create-class
     {:component-will-unmount (e! ->Cleanup state-path)
      :reagent-render
      (fn [{:keys [e! query args state-path skeleton view app state refresh breadcrumbs
                   simple-view loading-state]}]
        (let [state (if state-path
                      state
                      @state-atom)]
          (when (or (not= @refresh-value refresh)
                    (not= @previous-args args))
            (reset! refresh-value refresh)
            (reset! previous-args args)
            (e! (->Query query args state-path state-atom)))
          (if (or state loading-state)
            ;; Results loaded, call the view
            (if simple-view
              (conj simple-view (or state loading-state))
              ^{:key "query-result-view"}
              [view e! app state breadcrumbs])

            ;; Results not loaded, show skeleton or loading spinner
            (if skeleton
              skeleton
              [:div {:class (<class common-styles/spinner-style)}
               [CircularProgress]]))))})))

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
