(ns teet.ui.query
  "Query view. Component that fetches a named query from the server."
  (:require [reagent.core :as r]
            [tuck.core :as t]
            [teet.ui.material-ui :refer [CircularProgress]]
            teet.common.common-controller
            [taoensso.timbre :as log]))

(defrecord Query [query args state-path])
(defrecord QueryResult [state-path result])

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
    (assoc-in app state-path result)))

(defn query [{:keys [e! query args state-path refresh]}]
  (let [refresh-value (atom refresh)]
    (e! (->Query query args state-path))
    (fn [{:keys [e! query args state-path skeleton view app state refresh]}]
      (when (not= @refresh-value refresh)
        (reset! refresh-value refresh)
        (e! (->Query query args state-path)))
      (if state
        ;; Results loaded, call the view
        [view e! app state]

        ;; Results not loaded, show skeleton or loading spinner
        (if skeleton
          skeleton
          [CircularProgress])))))
