(ns teet.runner
  (:require "react"
            "react-dom"
            [cljs.test :as test]
            teet.example-tuck-test
            teet.example-test
            teet.common.common-controller-test))

;; runner code from clj-chrome-devtools
(def PRINTED (atom []))

(defn get-printed []
  (let [v @PRINTED]
    (reset! PRINTED [])
    (clj->js v)))

(def screenshot-number (atom {:name nil :number 0}))

(defn screenshot-file-name []
  (let [test-name (-> (cljs.test/get-current-env) :testing-vars first meta :name)]
    (str test-name
         "-"
         (:number (swap! screenshot-number
                         (fn [{:keys [name number]}]
                           {:name test-name
                            :number (if (= name test-name)
                                      (inc number)
                                      0)})))
         ".png")))

(defn screenshot [& file-name]
  (let [file-name (or file-name (screenshot-file-name))]
    (js/Promise.
     (fn [resolve _]
       (aset js/window "CLJ_SCREENSHOT_NAME" file-name)
       (aset js/window "CLJ_SCREENSHOT_RESOLVE" resolve)))))

(aset js/window "screenshot" screenshot)


(aset js/window "TEET_TESTS" #(do
                                (aset js/window "CLJ_TESTS_STARTED" true)
                                (set! *print-fn* (fn [& msg] (swap! PRINTED conj (apply str msg))))
                                (aset js/window "CLJ_TEST_GET_PRINTED" get-printed)
                                (test/run-tests 'teet.example-tuck-test
                                                'teet.example-test
                                                'teet.common.common-controller-test)))
