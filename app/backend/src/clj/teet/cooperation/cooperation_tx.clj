(ns teet.cooperation.cooperation-tx
  (:require [datomic.client.api :as d]
            [datomic.ion :as ion]
            [teet.cooperation.cooperation-db :as cooperation-db]
            [teet.cooperation.cooperation-model :as cooperation-model]
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

(def ^:private response-given-conflict
  {:cognitect.anomalies/category :cognitect.anomalies/conflict
   :cognitect.anomalies/message "This application has a third party response and cannot be deleted."
   :teet/error :application-has-third-party-response})

(defn delete-application
  "Delete application if it doesn't have a third party response."
  [db user application-id]
  (if (cooperation-db/application-editable? db application-id)
    [(meta-model/deletion-tx user application-id)]
    (ion/cancel response-given-conflict)))

(defn edit-application
  [db user application]
  (if (cooperation-db/application-editable? db (:db/id application))
    [(merge (select-keys application
                         (conj cooperation-model/editable-application-attributes :db/id))
            (meta-model/modification-meta user))]
    (ion/cancel response-given-conflict)))
