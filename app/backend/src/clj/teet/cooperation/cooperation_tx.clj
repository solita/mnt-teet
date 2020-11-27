(ns teet.cooperation.cooperation-tx
  (:require [datomic.client.api :as d]
            [datomic.ion :as ion]))

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
