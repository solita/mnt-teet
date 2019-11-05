(ns teet.thk-integration-ion
  "THK integration lambdas"
  (:require [teet.log :as log]))

(defn process-thk-file
  [{:keys [input]}]
  (log/event :thk-file-processed
             {:input input}))
