(ns teet.runner
  (:require "react"
            "react-dom"
            [cljs.test :as test]
            [teet.common.common-controller :as common-controller]
            [postgrest-ui.impl.fetch :as postgrest-fetch]
            teet.drtest

            ;; Require all test namespaces
            teet.comments.comments-view-test
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

(defonce original-postgrest-fetch-impl @postgrest-fetch/fetch-impl)

(defn run-teet-tests []
  (reset! common-controller/test-mode? true)
  (reset! postgrest-fetch/fetch-impl common-controller/send-fake-postgrest-query!)
  (test/run-tests 'teet.comments.comments-view-test
                  'teet.example-tuck-test
                  'teet.example-test
                  'teet.common.common-controller-test))


;; (function() {
;;    var link = document.querySelector("link[rel*='icon']") || document.createElement('link');
;;    link.type = 'image/x-icon';
;;    link.rel = 'shortcut icon';
;;    link.href = 'http://www.stackoverflow.com/favicon.ico';
;;    document.getElementsByTagName('head')[0].appendChild(link);
;;
;;})();

(def success-icon-url "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAHklEQVQ4T2NkaGD4z0ABYBw1gGE0DBhGw4BhWIQBAINNGAEQDxpxAAAAAElFTkSuQmCC")
(def failure-icon-url "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAHklEQVQ4T2P8z8Dwn4ECwDhqAMNoGDCMhgHDsAgDAKXUH/HLXNS/AAAAAElFTkSuQmCC")

(defn- set-favicon! [success?]
  ;; remove old favicon (if any)
  (.forEach (js/document.querySelectorAll "link[rel='icon']") #(.removeChild (.-parentNode %) %))
  (.forEach (js/document.querySelectorAll "link[rel='shortcut icon']") #(.removeChild (.-parentNode %) %))

  ;; add new favicon based on success
  (let [link (.createElement js/document "link")]
    (set! (.-type link) "image/x-icon")
    (set! (.-rel link) "shortcut icon")
    (set! (.-href link) (if success?
                          success-icon-url
                          failure-icon-url))
    (.appendChild (aget (.getElementsByTagName js/document "head") 0) link)))

(defmethod test/report [:cljs.test/default :end-run-tests] [m]

  (set-favicon! (cljs.test/successful? m))

  (if (cljs.test/successful? m)
    (println "Success!")
    (println "FAIL")))

;; This is called by CI runner in clj-chrome-devtools invocation
(aset js/window "TEET_TESTS"
      #(do
         (reset! teet.drtest/take-screenshots? true)
         (aset js/window "CLJ_TESTS_STARTED" true)
         (set! *print-fn* (fn [& msg] (swap! PRINTED conj (apply str msg))))
         (aset js/window "CLJ_TEST_GET_PRINTED" get-printed)
         (run-teet-tests)))


;; The main is called by figwheel extra main
(defn -main [& _args]
  (run-teet-tests))
