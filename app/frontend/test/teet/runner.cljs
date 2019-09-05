(ns teet.runner
  (:require [figwheel.main.testing :refer-macros [run-tests-async]]
            [postgrest-ui.impl.fetch :as postgrest-fetch]
            [teet.common.common-controller :as common-controller]
            [teet.example-test]))

;; Replace actual backend calls with functions that gather the
;; requests to `common-controller/test-requests`.
(reset! common-controller/test-mode? true)
(reset! postgrest-fetch/fetch-impl common-controller/send-fake-postgrest-query!)

(defn -main [& args]
  (run-tests-async 10000))
