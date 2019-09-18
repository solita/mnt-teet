(ns teet.projects.projects-controller
  (:require [tuck.core :as t]
            [taoensso.timbre :as log]))

(defrecord UpdateProjectsFilter [column value])
(defrecord SetProjectsFilter [filter])
(defrecord SetListingState [state])
(defrecord SetTotalCount [total])
(defrecord ClearProjectsFilter [])

(extend-protocol t/Event
  SetListingState
  (process-event [{state :state} app]
    (assoc-in app [:projects :listing] state))

  UpdateProjectsFilter
  (process-event [{:keys [column value]} app]
    (let [app (update-in app [:projects :new-filter] merge {column value})]
      (t/fx app
            {:tuck.effect/type :debounce
             :timeout 300
             :id :projects-filter
             :event (constantly (->SetProjectsFilter (get-in app [:projects :new-filter])))})))

  SetProjectsFilter
  (process-event [{f :filter} app]
    (assoc-in app [:projects :filter] f))

  ClearProjectsFilter
  (process-event [_ app]
    (-> app
        (update-in [:projects] dissoc :filter :new-filter)))

  SetTotalCount
  (process-event [{total :total} app]
    (assoc-in app [:projects :total-count] total)))


(defn project-filter-where [{:strs [name road_nr]}]
  (let [clauses (merge
                 (when name
                   {"name" [:ilike (str "%" name "%")]})
                 (when road_nr
                   {"road_nr" [:= road_nr]}))]

    (when clauses
      {:and clauses})))
