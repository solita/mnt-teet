(ns teet.drtest
  (:refer-clojure :exclude [atom])
  (:require [cljs.core.async :as async :refer [chan timeout close! <!]]
            [drtest.core :as drt]
            [drtest.step :as ds]
            [reagent.core :as r]
            [teet.common.common-controller :as common-controller]
            [tuck.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;;
;; Wait-request step
;;

(defn wait-request
  "Wait for test request matching predicate to be sent. Returns channel
  from which the request can be read."
  [pred]
  (go
    (loop [req (common-controller/take-test-request! pred)]
      (if req
        req
        (do
          (<! (timeout 50))
          (recur (common-controller/take-test-request! pred)))))))

(defn wait-request* [pred callback]
  (go
    (callback (<! (wait-request pred)))))

(defmethod ds/execute :wait-request [{:keys [predicate as response] :as step-descriptor}
                                     ctx ok fail]
  (wait-request* predicate
                 (fn [req]
                   (when response
                     ;; Automatically respond with the given value
                     ((:on-success req) response))
                   (ok (if as
                         (assoc ctx as req)
                         ctx)))))


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

(defmethod ds/execute :expect-tuck-event [{:keys [predicate as] :as step-descriptor}
                                          {events ::events :as ctx} ok fail]
  (if-let [event (some #(when (predicate % ctx) %) @events)]
    (ok (if as
          (assoc ctx as event)
          ctx))
    (fail "Expected tuck event has not been sent")))

;; Expose useful functions so that only this namespace needs to be
;; required
(def step ds/step)

(def atom r/atom)
