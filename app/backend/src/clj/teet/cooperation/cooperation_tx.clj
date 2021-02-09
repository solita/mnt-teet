(ns teet.cooperation.cooperation-tx
  (:require [datomic.client.api :as d]
            [datomic.ion :as ion]
            [teet.meta.meta-model :as meta-model]))

(defn save-3rd-party [db {:cooperation.3rd-party/keys [project] :as tp}]
  (let [{db-after :db-after} (d/with db {:tx-data [tp]})
        third-parties
        (d/q '[:find ?tp ?n
               :where
               [?tp :cooperation.3rd-party/project ?p]
               [?tp :cooperation.3rd-party/name ?n]
               [(missing? $ ?tp :meta/deleted?)]
               :in $ ?p]
             db-after project)]
    (if-not (apply distinct? (map second third-parties))
      (ion/cancel
       {:cognitect.anomalies/category :cognitect.anomalies/conflict
        :cognitect.anomalies/message "This 3rd party already exists in project"
        :teet/error :third-party-already-exists})
      [tp])))

(defn delete-application
  [db user application-id]
  ;; TODO User can delete a cooperation application if the involved party response has not been added to the application
  [(meta-model/deletion-tx user application-id)])
