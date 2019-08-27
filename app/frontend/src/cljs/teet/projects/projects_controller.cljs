(ns teet.projects.projects-controller
  (:require [tuck.core :as t]))

(defrecord UpdateProjectsFilter [new-filter])
(defrecord SetProjectsFilter [filter])
(defrecord SetListingState [state])

(extend-protocol t/Event
  SetListingState
  (process-event [{state :state} app]
    (assoc-in app [:projects :listing] state))

  UpdateProjectsFilter
  (process-event [{new-filter :new-filter} app]
    (let [app (update-in app [:projects :new-filter] merge new-filter)]
      (t/fx app
            {:tuck.effect/type :debounce
             :timeout 300
             :id :projects-filter
             :event (constantly (->SetProjectsFilter (get-in app [:projects :new-filter])))})))

  SetProjectsFilter
  (process-event [{filter :filter} app]
    (assoc-in app [:projects :filter] filter)))


(defn project-filter-where [{:keys [text]}]
  (when text
    {:and {"searchable_text" [:ilike (str "%" text "%")]}}))
