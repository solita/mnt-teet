(ns teet.drtest
  (:refer-clojure :exclude [atom])
  (:require [cljs.core.async :as async :refer [chan timeout close! <!]]
            [drtest.core :as drt]
            [drtest.step :as ds]
            [postgrest-ui.impl.fetch :as postgrest-fetch]
            [reagent.core :as r]
            [teet.common.common-controller :as common-controller]
            [tuck.core :as t]
            [teet.localization :as localization])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce take-screenshots? (cljs.core/atom false))

(defonce test-initialized
  (js/Promise.
   (fn [ok _err]
     (localization/load-language! @localization/selected-language ok))))

(defonce init-step
  {:drtest.step/label "Wait for test initialization"
   :drtest.step/type :wait-promise
   :promise test-initialized})

;;
;; Wait-request step
;;

(defn wait-request
  "Wait for test request matching predicate to be sent. Returns channel
  from which the request can be read."
  [pred retries]
  (go
    (loop [req (common-controller/take-test-request! pred)
           retries-left retries]
      (cond (some? req) req

            (not (pos? retries-left)) ::fail

            :else
            (do
              (<! (timeout 50))
              (recur (common-controller/take-test-request! pred)
                     (dec retries-left)))))))

(defn wait-request* [pred retries callback]
  (go
    (callback (<! (wait-request pred retries)))))

(defmethod ds/execute :wait-request [{:keys [predicate retries as response] :as step-descriptor
                                      :or {retries 10}}
                                     {e!-atom ::e! :as ctx} ok fail]
  (wait-request* predicate
                 retries
                 (fn [req]
                   (if (= req ::fail)
                     (fail (str "Request not caugth after " retries " retries."))
                     (do (when response
                           ;; Automatically respond with the given value
                           (if-let [evt (:result-event req)]
                             (@e!-atom (evt response))
                             (println "Response given, but no :result-event")))
                         (ok (if as
                               (assoc ctx as req)
                               ctx)))))))

(defmethod ds/execute :wait-command [{:keys [command payload predicate] :as step-descriptor}
                                     ctx ok fail]
  (ds/execute (assoc step-descriptor
                     :drtest.step/type :wait-request
                     :predicate (fn [req]
                                  (and (= :command! (:tuck.effect/type req))
                                       (or (and predicate (predicate req))
                                           (and payload (= payload (:payload req)))))))
              ctx ok fail))

(defmethod ds/execute :wait-query [{:keys [query args predicate] :as step-descriptor}
                                   ctx ok fail]
  (ds/execute (assoc step-descriptor
                     :drtest.step/type :wait-request
                     :predicate (fn [req]
                                  (and (= :query (:tuck.effect/type req))
                                       (= query (:query req))
                                       (or (and predicate (predicate req))
                                           (and args (:args req))))))
              ctx ok fail))

(defmethod ds/execute :no-request [_ ctx ok fail]
  (if-let [req (common-controller/take-test-request! (constantly true))]
    (fail (str "Expected no requests, but one was found: " (pr-str req)))
    (ok ctx)))
;;
;; Tuck-render and expect-tuck-event steps
;;

(defn capture-e! [e!-atom events-atom component orig-e! app]
  (let [e! (fn [event]
             (swap! events-atom conj event)
             (orig-e! event))]
    (reset! e!-atom e!)
    [component e! app]))

(defmethod ds/execute :tuck-render [{:keys [component] :as step-descriptor} ctx ok fail]
  (let [e!-atom (clojure.core/atom nil)
        events (clojure.core/atom [])]
    (ds/execute (assoc step-descriptor
                       ::ds/type :render
                       :component (fn [{app :app}]
                                    [t/tuck app (partial capture-e! e!-atom events component)]))
                (assoc ctx
                       ::e! e!-atom
                       ::events events) ok fail)))

(defmethod ds/execute :expect-no-tuck-event [_ {events ::events :as ctx} ok fail]
  (if (seq events)
    (fail (str "Expected no tuck events, but " (count events) " events were waiting processing."))
    (ok ctx)))

(defmethod ds/execute :expect-tuck-event [{:keys [predicate as] :as step-descriptor}
                                          {events ::events :as ctx} ok fail]
  (if-let [event (some #(when (predicate % ctx) %) @events)]
    (do
      (swap! events (fn [events] (filterv (complement #(predicate % ctx)) events)))
      (.log js/console " after events   =>  " (pr-str @events))
      (ok (if as
            (assoc ctx as event)
            ctx)))
    (fail "Expected tuck event has not been sent")))

;; Expose useful functions so that only this namespace needs to be
;; required
(def step ds/step)

(def atom r/atom)

(defmethod ds/execute :debug-test [_step-descriptor _ctx ok fail]
  (js/console.log "Invoke TEST_OK() or TEST_FAIL() to continue.")
  (aset js/window "TEST_OK" ok)
  (aset js/window "TEST_FAIL" fail))

;; stop test execution for inspecting DOM
(defonce debug (step :debug-test "Debug tests"))
