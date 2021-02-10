(ns teet.cooperation.cooperation-tx
  (:require [datomic.client.api :as d]
            [datomic.ion :as ion]
            [teet.cooperation.cooperation-db :as cooperation-db]
            [teet.meta.meta-model :as meta-model]))

(defn create-3rd-party [db {:cooperation.3rd-party/keys [project name] :as tp}]
  (if (seq (d/q '[:find ?tp
                  :where
                  [?tp :cooperation.3rd-party/project ?project]
                  [?tp :cooperation.3rd-party/name ?name]
                  :in $ ?project ?name]
                db project name))
    (ion/cancel
     {:cognitect.anomalies/category :cognitect.anomalies/conflict
      :cognitect.anomalies/message "This 3rd party already exists in project"
      :teet/error :third-party-already-exists})
    [tp]))

(defn delete-application
  "Delete application if it doesn't have a third party response."
  [db user application-id]
  (if (cooperation-db/application-has-third-party-response? db application-id)
    (ion/cancel
     {:cognitect.anomalies/category :cognitect.anomalies/conflict
      :cognitect.anomalies/message "This application has a third party response and cannot be deleted."
      :teet/error :application-has-third-party-response})
    [(meta-model/deletion-tx user application-id)]))
