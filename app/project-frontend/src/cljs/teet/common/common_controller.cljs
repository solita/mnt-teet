(ns teet.common.common-controller
  "Common UI controller code"
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [taoensso.timbre :as log]
            [teet.routes :as routes]
            [tuck.core :as t]
            [tuck.effect :as tuck-effect]))


(defn query-param-atom [{:keys [page params query] :as app} param-name read-param write-param]
  (r/wrap (read-param (get query param-name))
          #(routes/navigate! page params (write-param query %))))

(defn query-param-boolean-atom [app param-name]
  (query-param-atom app param-name
                    #(= "1" %)
                    (fn [query value]
                      (assoc query param-name (if value "1" "0")))))

#_(defn query-param-ids-atom
  "Returns atom that reads/writes list of ids to the given query parameter"
  [app param-name]
  (query-param-atom app param-name
                    #(when %
                       (into []
                             (map js/parseInt)
                             (str/split #"," %)))
                    #(str/join "," %)))

(defrecord DebounceEffect [effect])
(defrecord RPCResponse [path data])

(defonce debounce-timeouts (atom {}))

(defmethod tuck-effect/process-effect :debounce [e! {:keys [event effect timeout id]}]
  (let [timeout-id (or id event)
        existing-timeout (get @debounce-timeouts timeout-id)]
    (when existing-timeout
      (.clearTimeout js/window existing-timeout))
    (swap! debounce-timeouts
           assoc timeout-id
           (.setTimeout js/window #(do
                                     (swap! debounce-timeouts dissoc timeout-id)
                                     (if event
                                       (e! (event))
                                       (e! (->DebounceEffect effect)))) timeout))))

(extend-protocol t/Event
  DebounceEffect
  (process-event [{effect :effect} app]
    (t/fx app effect))

  RPCResponse
  (process-event [{:keys [path data]} app]
    (assoc-in app path data)))


(defmethod tuck-effect/process-effect :rpc [e! {:keys [rpc args result-path result-event endpoint method]}]
  (assert rpc "Must specify :rpc function to call")
  (assert (map? args) "Must specify :args map")
  (assert (or result-path result-event) "Must specify result-path or result-event")
  (assert endpoint "Must specify :endpoint for PostgREST server")
  (assert #{nil :GET :POST} "Must specify :method :GET or :POST (default) ")
  (-> (if (= method :GET)
           ;; GET request, add parameters to URL
           (js/fetch (str endpoint "/rpc/" rpc "?"
                          (str/join "&"
                                    (map (fn [[arg val]]
                                           (str (if (keyword? arg)
                                                  (name arg)
                                                  arg)
                                                "=" (js/encodeURIComponent (str val))))
                                         args))))

           ;; POST request, send parameters as JSON body
           (js/fetch (str endpoint "/rpc/" rpc)
                     #js {:method "POST"
                          :headers #js {"Content-Type" "application/json"
                                        "Accept" "application/json"}
                          :body (-> args clj->js js/JSON.stringify)}))
      (.then #(.json %))
      (.then (fn [json]
               ;; FIXME: generic error handling
               (log/info "RESPONSE: " json)
               (let [data (js->clj json :keywordize-keys true)]
                 (if result-path
                   (e! (->RPCResponse result-path data))
                   (e! (result-event data))))))))

(defmethod tuck-effect/process-effect :navigate [e! {:keys [page params query]}]
  (routes/navigate! page params query))
