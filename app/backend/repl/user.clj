(ns user
  (:require [teet.main :as main]
            [teet.environment :as environment]))

(defn go []
  (main/restart)
  (environment/datomic-connection))
