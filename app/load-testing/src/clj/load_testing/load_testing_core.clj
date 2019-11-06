(ns load-testing.load-testing-core
  (:require [clojure.core.async :refer [chan go >! <!! <! put! take!]]
            [clj-http.client :as client]
            [clj-gatling.core :as gatling]
            [cognitect.transit :as t])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn write-transit [out data]
  (t/write (t/writer out :json) data))

(defn clj->transit
  "Convert given Clojure `data` to transit+json string."
  [data]
  (with-open [out (ByteArrayOutputStream.)]
    (write-transit out data)
    (.toString out "UTF-8")))

(def base-url "http://localhost:3000/thk_project_search?")
(def post-url "http://localhost:4000/command/")             ;;Need to have the local dev setup running

(defn- http-get [url _]
  (let [response (chan)
        check-status (fn [{:keys [status]}]
                       (go (>! response (= 200 status))))]
    (check-status (client/get url {}))
    response))

(def bar
  (partial http-get base-url))


(def thk-project-fetch
  {:name "TKH projects fetch"
   :scenarios [{:name "thk projects fetch"
                :steps [{:name "fetch all projects" :request bar}]}]})

(defn- ramp-up-distribution [percentage-at _]
  (cond
    (< percentage-at 0.1) 0.1
    (< percentage-at 0.2) 0.2
    :else 1.0))

(defn- run-sim
  []
  (gatling/run thk-project-fetch
               {:concurrency 50
                :concurrency-distribution ramp-up-distribution
                :root "tmp"                                 ;; Saves the resulting report in /tmp/*
                :requests 200}))

(def login-post-data {:command :login :payload {:user/id #uuid "4c8ec140-4bd8-403b-866f-d2d5db9bdf74"
                                                :user/person-id "123456790"
                                                :user/given-name "Danny D."
                                                :user/family-name "Manager"
                                                :user/email "danny.d.manager@example.com"
                                                :user/organization "Maanteeamet"
                                                :site-password "haloo"}})

(defn- http-post [url _]
  (let [response (chan)
        check-status (fn [{:keys [status]}]
                       (go (>! response (= 200 status))))]
    (check-status (client/post url {:headers {"Content-Type" "application/json+transit"}
                                    :body (clj->transit login-post-data)}))
    response))

(def post
  (partial http-post post-url))

(def login-load-test
  {:name "Test local datomic login"
   :scenarios [{:name "Login load test"
                :steps [{:name "Test local login" :request post}]}]})

(defn- run-post-sim
  []
  (gatling/run login-load-test
               {:concurrency 50
                :concurrency-distribution ramp-up-distribution
                :root "tmp"                                 ;; Saves the resulting report in /tmp/*
                :requests 100}))
