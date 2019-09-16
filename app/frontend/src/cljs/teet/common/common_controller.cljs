(ns teet.common.common-controller
  "Common UI controller code"
  (:require [clojure.string :as str]
            [cljs-bean.core :refer [->clj]]
            [reagent.core :as r]
            [taoensso.timbre :as log]
            [teet.routes :as routes]
            [tuck.core :as t]
            [tuck.effect :as tuck-effect]
            [teet.transit :as transit]))


(defonce api-token (atom nil))

;; Helpers for faking backend requests in unit tests
(defonce test-mode? (atom false))
(defonce test-requests (atom []))

(defn take-test-request!
  "Return test request matching predicate and remove it from the list.
  If no matching request is found, returns nil."
  [pred]
  (let [req (some (fn [candidate]
                    (when (pred candidate)
                      candidate))
                  @test-requests)]
    (when req
      (swap! test-requests (fn [reqs]
                             (filterv #(not= req %) reqs))))
    req))

(defn- send-fake! [type]
  (fn [req]
    (swap! test-requests conj (merge req {:type type}))))

(def send-fake-query!
  (send-fake! :query))

(def send-fake-command!
  (send-fake! :command))

(def send-fake-postgrest-rpc!
  (send-fake! :postgrest-rpc))

(defn send-fake-postgrest-query! [url opts]
  ((send-fake! :postgrest-query) {:url url
                                  :opts (->clj opts)}))


;; Controller for backend communication

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

(defmethod tuck-effect/process-effect :debounce [e! {:keys [event effect timeout id] :as foo}]
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

(defn api-token-header []
  (when @api-token
    {"Authorization" (str "Bearer " @api-token)}))

(defmethod tuck-effect/process-effect :rpc [e! {:keys [rpc args result-path result-event endpoint method] :as q}]
  (assert rpc "Must specify :rpc function to call")
  (assert (map? args) "Must specify :args map")
  (assert (or result-path result-event) "Must specify result-path or result-event")
  (assert endpoint "Must specify :endpoint for PostgREST server")
  (assert #{nil :GET :POST} "Must specify :method :GET or :POST (default) ")
  (if @test-mode?
    (send-fake-postgrest-rpc! q)
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
                        :headers (clj->js (merge
                                           (api-token-header)
                                           {"Content-Type" "application/json"
                                            "Accept" "application/json"}))
                        :body (-> args clj->js js/JSON.stringify)}))
       (.then #(.json %))
       (.then (fn [json]
                ;; FIXME: generic error handling
                (log/info "RESPONSE: " json)
                (let [data (js->clj json :keywordize-keys true)]
                  (if result-path
                    (e! (->RPCResponse result-path data))
                    (e! (result-event data)))))))))

(defn- check-query-and-args [query args]
  (assert (keyword? query)
          "Must specify :query keyword that names the query to run")
  (assert (some? args) "Must specify :args for query"))

(defmethod tuck-effect/process-effect :query [e! {:keys [query args result-path result-event method]
                                                  :as q
                                                  :or {method "POST"}}]
  (check-query-and-args query args)
  (assert (or result-path result-event) "Must specify :result-path or :result-event")
  (if @test-mode?
    (send-fake-query! q)
    (let [payload  (transit/clj->transit {:query query :args args})]
      (-> (case method
            "GET" (js/fetch (str "/query/?q=" (js/encodeURIComponent payload))
                            #js {:method "GET"
                                 :headers (clj->js (api-token-header))})
            "POST" (js/fetch "/query/"
                             #js {:method "POST"
                                  :headers (clj->js
                                            (merge (api-token-header)
                                                   {"Content-Type" "application/json+transit"}))
                                  :body payload}))
          (.then #(.text %))
          (.then (fn [text]
                   (let [data (transit/transit->clj text)]
                     (if result-path
                       (e! (->RPCResponse result-path data))
                       (e! (result-event data))))))))))

(defn query-url
  "Generate an URL to a query with the given args. This is useful for queries
  that return raw ring responses that the browser can load directly (eg. link href).

  For normal data returning queries, you should use the `:query` effect type."
  [query args]
  (check-query-and-args query args)
  ;; FIXME: link needs token as well
  (str "/query/"
       "?q=" (js/encodeURIComponent (transit/clj->transit {:query query :args args}))
       "&t=" (js/encodeURIComponent @api-token)))

(defmethod tuck-effect/process-effect :command! [e! {:keys [command payload result-path result-event] :as q}]
  (assert (keyword? command)
          "Must specify :command keyword that names the command to execute")
  (assert (some? payload)
          "Must specify :payload for the command")
  (assert (or result-path result-event) "Must specify :result-path or :result-event")
  (if @test-mode?
    (send-fake-command! q)
    (-> (js/fetch (str "/command/")
                  #js {:method "POST"
                       :headers (clj->js
                                 (merge (api-token-header)
                                        {"Content-Type" "application/json+transit"}))
                       :body (transit/clj->transit {:command command
                                                    :payload payload})})
        (.then #(.text %))
        (.then (fn [text]
                 (let [data (transit/transit->clj text)]
                   (if result-path
                     (e! (->RPCResponse result-path data))
                     (e! (result-event data)))))))))

(defmethod tuck-effect/process-effect :navigate [e! {:keys [page params query]}]
  (routes/navigate! page params query))

(defmethod tuck-effect/process-effect :set-api-token [_ {token :token}]
  (assert token "Must specify :token to set as new API token")
  (reset! api-token token))
