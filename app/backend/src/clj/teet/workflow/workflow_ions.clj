(ns teet.workflow.workflow-ions
  "Datomic Ions for accessing workflow information"
  (:require [datomic.client.api :as d]
            [cheshire.core :as cheshire]))

(def db-name "tatu-test-1")

(def get-client
  "This function will return a local implementation of the client
interface when run on a Datomic compute node. If you want to call
locally, fill in the correct values in the map."
  (memoize #(d/client {:server-type :ion
                       :region "eu-central-1"
                       :system "teet-dev-datomic"
                       :query-group "teet-dev-datomic"
                       :endpoint "http://entry.teet-dev-datomic.eu-central-1.datomic.net:8182/"
                       :proxy-port 8182})))

(defn get-connection []
  (d/connect (get-client) {:db-name db-name}))

(defn fetch-workflow [{:keys [input]}]
  (let [db (d/db (get-connection))
        {:keys [id]} (cheshire/decode input keyword)]
    (cheshire/encode
     (d/pull db '[:workflow/name
                  {:workflow/activities
                   [:db/id
                    :activity/name
                    {:activity/tasks [:db/id :task/status]}]}]
             id))))
