(ns teet.search.search-controller
  "Controller for search views"
  (:require [tuck.core :as t]))

(defrecord UpdateQuickSearchTerm [term])

(def min-search-term-length
  "Minimum number of characters that must be typed for search to automatically start."
  3)

(extend-protocol t/Event
  UpdateQuickSearchTerm
  (process-event [{term :term} app]
    (let [app (-> app
                  (assoc-in [:quick-search :results] nil)
                  (assoc-in [:quick-search :term] term))]
      (if (>= (count term) min-search-term-length)
        (t/fx app
              {:tuck.effect/type :debounce
               :timeout 300
               :effect {:tuck.effect/type :query
                        :query :thk.project/search
                        :args {:text term}
                        :result-path [:quick-search :results]}})
        app))))
