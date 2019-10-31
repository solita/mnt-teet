(ns teet.log
  (:require [clojure.string :as str]
            [cljs-bean.core :refer [->clj]]
            taoensso.timbre
            [tuck.core :as t])
  (:require-macros teet.log))

(defrecord NoOp [_]
  t/Event
  (process-event [_ app]
    app))

(defrecord LogFrontendError [error]
  t/Event
  (process-event [{:keys [error]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :logging/log-error
           :payload error
           :result-event ->NoOp})))

(defn- ->error [event]
  {:message (.-message event)
   :stack (->> (-> event .-error .-stack
                   (str/split "\n"))
               (take 5)
               (into []))})

(defn hook-onerror! [e!]
  (js/console.log "Hooking onerror")
  (js/window.addEventListener "error"
                              (fn [event]
                                (js/console.log "In error handling")
                                (js/console.log event)
                                (e! (->LogFrontendError (->error event))))))
