(ns ^:figwheel-hooks teet.dev-main
  "Main file for dev that includes the app and tests"
  (:require teet.main
            teet.runner))

(defonce testing? (= "http://localhost:4000/#/test" js/document.location.href))

(when testing?
  (set! (.-onload js/document.body)
        #(do
           (println "Testing mode")
           (let [app (js/document.querySelector "#teet-frontend")]
             (.removeChild (.-parentNode app) app))
           (teet.runner/run-teet-tests))))

(defn ^:after-load after-load []
  (when testing?
    (teet.runner/run-teet-tests)))
