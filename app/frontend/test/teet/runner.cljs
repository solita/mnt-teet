(ns teet.runner
  (:require [figwheel.main.testing :refer-macros [run-tests-async]]
            [teet.example-test]))

(defn -main [& args]
  (run-tests-async 10000))
