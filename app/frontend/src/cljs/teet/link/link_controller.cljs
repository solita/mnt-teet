(ns teet.link.link-controller
  (:require [tuck.core :as t]
            [teet.common.common-controller :as common-controller]))

(defrecord AddLink [from to external-id type in-progress-atom])
(defrecord DeleteLink [from to type id in-progress-atom])

(defn command-with-progress [app in-progress-atom command payload]
  (when in-progress-atom
      (reset! in-progress-atom true))
  (t/fx app
        {:tuck.effect/type :command!
         :command command
         :payload payload
         :result-event (fn [_]
                           (when in-progress-atom
                             (reset! in-progress-atom false))
                           (common-controller/->Refresh))}))
(extend-protocol t/Event
  AddLink
  (process-event [{:keys [from to external-id type in-progress-atom] :as event} app]
    (command-with-progress
     app in-progress-atom
     :link/add-link
     (merge {:from from
             :type type}
            (cond
              external-id
              {:external-id external-id}

              to
              {:to to}

              :else
              (throw (ex-info "Add link requires :to or :external-id"
                              {:event event}))))))

  DeleteLink
  (process-event [{:keys [from to type id in-progress-atom]} app]
    (command-with-progress
     app in-progress-atom
     :link/delete
     {:from from
      :to to
      :type type
      :db/id id})))
