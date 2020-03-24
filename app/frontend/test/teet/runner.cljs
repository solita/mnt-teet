(ns teet.runner
  (:require [figwheel.main.testing :refer-macros [run-tests-async]]
            teet.example-tuck-test
            teet.example-test
            ;; teet.document.document-view-test
            teet.common.common-controller-test))

;; Replace actual backend calls with functions that gather the
;; requests to `common-controller/test-requests`.

(defn -main [& args]
  (run-tests-async 10000))
