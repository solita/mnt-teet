(ns teet.thk.thk-integration-ion
  "THK integration lambdas"
  (:require [teet.log :as log]))

(defn process-thk-file
  [{:keys [input] :as event}]
  (log/event :thk-file-processed
             {:event event}))
