(ns teet.search.search-controller
  "Controller for search views"
  (:require [tuck.core :as t]))

(defrecord UpdateQuickSearchTerm [term])

(extend-protocol t/Event
  UpdateQuickSearchTerm
  (process-event [{term :term} app]
    (t/fx (-> app
              (assoc-in [:quick-search :results] nil)
              (assoc-in [:quick-search :term] term))
          {:tuck.effect/type :debounce
           :timeout 300
           :effect {:tuck.effect/type :rpc
                    :endpoint (get-in app [:config :project-registry-url])
                    :rpc "quicksearch"
                    :args {:q term}
                    :result-path [:quick-search :results]}})))
