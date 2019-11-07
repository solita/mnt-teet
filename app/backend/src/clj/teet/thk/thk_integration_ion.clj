(ns teet.thk.thk-integration-ion
  "THK integration lambdas"
  (:require [cheshire.core :as cheshire]
            [teet.log :as log]))

(def test-input "{\"Records\":[1, 2, 3]}")

(def test-event
  {:input test-input})

(defn process-thk-file
  [{:keys [input]}]
  (log/event :thk-file-processed
             {:input (cheshire/decode input keyword)}))
