(ns teet.ui.query
  "Query view. Component that fetches a named query from the server."
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [tuck.core :as t]
            [teet.ui.material-ui :refer [CircularProgress]]
            [teet.common.common-styles :as common-styles]
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.project.project-style :as project-style]))

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

(declare query-page-view)

(defn query
  "Component that does a datomic query and shows view with the resulting data."
  [{:keys [e! query args state-path refresh poll-seconds]}]
  (let [refresh-value (atom refresh)
        previous-args (atom args)
        state-atom (when-not state-path
                     (r/atom nil))
        poll-id (when poll-seconds
                  (js/setInterval #(e! (->Query query args state-path state-atom))
                                  (* 1000 poll-seconds)))]
    (e! (->Query query args state-path state-atom))
    (r/create-class
     {:component-will-unmount #(do
                                 (when poll-id
                                   (js/clearInterval poll-id))
                                 (e! (->Cleanup state-path)))
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
              [query-page-view view e! app state breadcrumbs])

            ;; Results not loaded, show skeleton or loading spinner
            (if skeleton
              skeleton
              [:div {:class (<class common-styles/spinner-style)}
               [CircularProgress]]))))})))

(defn query-page-view [page-content-view e! app state breadcrumbs]
  [:<>
   (when (> (count breadcrumbs) 1)
     [breadcrumbs/breadcrumbs breadcrumbs])
   [page-content-view e! app state]])
