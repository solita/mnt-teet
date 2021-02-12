(ns teet.ui.query
  "Query view. Component that fetches a named query from the server."
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [tuck.core :as t]
            [teet.ui.material-ui :refer [CircularProgress]]
            [teet.common.common-styles :as common-styles]
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.project.project-style :as project-style]
            goog.async.Debouncer
            [goog.functions :as functions]
            [teet.ui.context :as context]
            [teet.common.common-controller :as common-controller]))

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
        filter-atom (r/atom {})
        debounced-refresh (functions/debounce (fn []
                                       (e! (common-controller/->Refresh)))
                                     250)
        change-filter #(swap! filter-atom merge %)
        reset-filter #(reset! filter-atom {})
        poll-id (when poll-seconds
                  (js/setInterval #(e! (->Query query args state-path state-atom))
                                  (* 1000 poll-seconds)))]
    (add-watch filter-atom :requery-filters
               (fn [_ _ _ _]
                 (debounced-refresh)))
    (e! (->Query query args state-path state-atom))
    (r/create-class
      {:component-will-unmount #(do
                                  (when poll-id
                                    (js/clearInterval poll-id))
                                  (e! (->Cleanup state-path)))
       :reagent-render
       (fn [{:keys [e! query args state-path skeleton view app state refresh breadcrumbs
                    simple-view loading-state]}]
         [context/provide :query-filter {:value @filter-atom
                                         :on-change change-filter
                                         :reset-filter reset-filter}
          (let [state (if state-path
                        state
                        @state-atom)]
            (when (or (not= @refresh-value refresh)
                      (not= @previous-args args))
              (reset! refresh-value refresh)
              (reset! previous-args args)
              (e! (->Query query (assoc args :filters @filter-atom) state-path state-atom)))
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
                 [CircularProgress]])))])})))

(defn debounce-query
  [{:keys [args] :as params} debounce-time]
  (r/with-let [args-atom (r/atom args)
               args-change-fn (functions/debounce #(reset! args-atom %) debounce-time)]
    (args-change-fn args)
    [query (assoc params :args @args-atom)]))

(defn query-page-view [page-content-view e! app state breadcrumbs]
  [:<>
   (when (> (count breadcrumbs) 1)
     [breadcrumbs/breadcrumbs breadcrumbs])
   [page-content-view e! app state]])
