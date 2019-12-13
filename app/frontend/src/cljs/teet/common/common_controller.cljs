(ns teet.common.common-controller
  "Common UI controller code"
  (:require [clojure.string :as str]
            [cljs-bean.core :refer [->clj]]
            [reagent.core :as r]
            [teet.log :as log]
            [teet.routes :as routes]
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [tuck.core :as t]
            [tuck.effect :as tuck-effect]
            [teet.app-state :as app-state]
            [teet.login.login-paths :as login-paths]
            [teet.transit :as transit]
            [teet.localization :refer [tr tr-or]]
            postgrest-ui.impl.fetch))

(def api-token routes/api-token)
(defonce enabled-features (r/cursor app-state/app [:enabled-features]))

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

(defn query-param-atom [{:keys [page params query] :as _app} param-name read-param write-param]
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
(defrecord RPC [rpc-effect-params])
(defrecord RPCResponse [path data])
(defrecord Navigate [page params query])
(defrecord SetQueryParam [param value]) ; navigate to same page but set set single query param
(defrecord ResponseError [err]) ; handle errors in HTTP response

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

(defmulti on-server-error
  "Handle server error by dispatching on error type. Must return new app
  state or effect return, same as tuck.core/process-event"
  (fn [err _app]
    (-> err ex-data :error)))

(defn default-server-error-handler [err app]
  (snackbar-controller/open-snack-bar app (tr-or [:error (-> err ex-data :error)]
                                                 [:error :server-error]) :error))

(defmethod on-server-error :default [err app]
  (default-server-error-handler err app))

(defmethod on-server-error :authorization-failure [_ {:keys [page params query] :as _app}]
  (reset! api-token nil)
  (t/fx (-> app
            (assoc-in [:login :navigate-to] {:page page
                                             :params params
                                             :query query})
            (dissoc :user)
            (snackbar-controller/open-snack-bar (tr [:error :authorization-failure])
                                                :warning))
        {:tuck.effect/type :navigate
         :page :login}))

(extend-protocol t/Event
  DebounceEffect
  (process-event [{effect :effect} app]
    (t/fx app effect))

  RPC
  (process-event [{rpc :rpc-effect-params} app]
    (t/fx app
          (merge {:tuck.effect/type :rpc
                  :endpoint (get-in app [:config :api-url])
                  :method :GET}
                  rpc)))

  RPCResponse
  (process-event [{:keys [path data]} app]
    (assoc-in app path data))

  Navigate
  (process-event [{:keys [page params query]} app]
    (t/fx app
          {:tuck.effect/type :navigate
           :page page
           :params params
           :query query}))

  SetQueryParam
  (process-event [{:keys [param value]} {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page page
           :params params
           :query (assoc query param value)}))

  ResponseError
  (process-event [{err :err} app]
    (on-server-error err app)))

(defn check-response-status [response]
  (let [status (.-status response)]
    (case status
      (401 403)
      (throw (ex-info "Authorization failure" {:error :authorization-failure}))

      200
      response

      (throw (ex-info "Request failure"
                      {:error (or (some-> response
                                          .-headers
                                          (.get "X-TEET-Error")
                                          keyword)
                                  :unknown-server-error)})))))

(defn catch-response-error [e! error-event]
  (fn [err]
    (e! (if error-event
          (error-event err)
          (->ResponseError err)))))

(defn headers->map
  "Turn nil, map, js object and js Headers into Clojure map"
  [headers]
  (cond (nil? headers)
        {}

        (map? headers)
        headers

        (= (type headers)
           js/Object)
        (js->clj headers)

        (= (type headers)
           js/Headers)
        (loop [ks (.keys headers)
               headers-map {}]
          (let [{:strs [done value] :as n} (js->clj (.next ks))]
            (if done
              headers-map
              (recur ks
                     (assoc headers-map
                            (keyword value)
                            (.get headers value))))))))

(defn map->headers
  "turn Clojure map into js Headers object"
  [headers-map]
  {:pre [(map? headers-map)]}
  (let [headers (js/Headers.)]
    (doseq [[k v] headers-map]
      (.set headers (name k) v))
    headers))

(defn- add-authorization [headers]
  (-> headers
      headers->map
      (assoc :Authorization (str "Bearer " @api-token))
      map->headers))

(defn- fetch*
  "Call JS fetch API. Automatically adds response code check and error handling.
  Returns promise."
  [e! error-event & [url authless-arg-map-js]]
  (-> (js/fetch url
                (-> authless-arg-map-js
                    js->clj
                    (update "headers" add-authorization)
                    clj->js))
      (.then check-response-status)
      (.catch (catch-response-error e! error-event))))

(defmethod tuck-effect/process-effect :rpc [e! {:keys [rpc args result-path result-event
                                                       endpoint method loading-path
                                                       json? error-event] :as q}]
  (assert rpc "Must specify :rpc function to call")
  (assert (map? args) "Must specify :args map")
  (assert (or result-path result-event) "Must specify result-path or result-event")
  (assert endpoint "Must specify :endpoint for PostgREST server")
  (assert #{nil :GET :POST} "Must specify :method :GET or :POST (default) ")
  (when (some? loading-path)
    (e! (->RPCResponse loading-path {:loading? true})))
  (if @test-mode?
    (send-fake-postgrest-rpc! q)
    (-> (if (= method :GET)
          ;; GET request, add parameters to URL
          (fetch* e! error-event
                  (str endpoint "/rpc/" rpc "?"
                  (str/join "&"
                            (map (fn [[arg val]]
                                   (str (if (keyword? arg)
                                          (name arg)
                                          arg)
                                        "=" (js/encodeURIComponent (str val))))
                                 args))))

          ;; POST request, send parameters as JSON body
          (fetch* e! error-event
                  (str endpoint "/rpc/" rpc)
                  #js {:method "POST"
                       :headers (clj->js {"Content-Type" "application/json"
                                          "Accept" "application/json"})
                       :body (-> args clj->js js/JSON.stringify)}))
        (.then #(.json %))
        (.then (fn [json]
                (let [data (if json?
                             json
                             (js->clj json :keywordize-keys true))]
                  (if result-path
                    (e! (->RPCResponse result-path data))
                    (e! (result-event data)))))))))

(defn- check-query-and-args [query args]
  (assert (keyword? query)
          "Must specify :query keyword that names the query to run")
  (assert (some? args) "Must specify :args for query"))


(defmethod tuck-effect/process-effect :query [e! {:keys [query args result-path result-event method error-event]
                                                  :as q
                                                  :or {method "POST"}}]
  (check-query-and-args query args)
  (assert (or result-path result-event) "Must specify :result-path or :result-event")
  (if @test-mode?
    (send-fake-query! q)
    (let [payload  (transit/clj->transit {:query query :args args})]
      (-> (case method
            "GET" (fetch* e! error-event
                          (str "/query/?q=" (js/encodeURIComponent payload))
                          #js {:method "GET"})
            "POST" (fetch* e! error-event
                           "/query/"
                           #js {:method "POST"
                                :headers (clj->js
                                          {"Content-Type" "application/json+transit"})
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
  (str "/query/"
       "?q=" (js/encodeURIComponent (transit/clj->transit {:query query :args args}))
       "&t=" (js/encodeURIComponent @api-token)))

(defrecord Query [query-effect-map]
  t/Event
  (process-event [_ app]
    (t/fx app
          (assoc query-effect-map :tuck.effect/type :query))))

(defmethod tuck-effect/process-effect :command! [e! {:keys [command payload result-path result-event success-message error-event] :as q}]
  (assert (keyword? command)
          "Must specify :command keyword that names the command to execute")
  (assert (some? payload)
          "Must specify :payload for the command")
  (assert (or result-path result-event) "Must specify :result-path or :result-event")
  (if @test-mode?
    (send-fake-command! q)
    (-> (fetch* e! error-event
                (str "/command/")
                #js {:method "POST"
                     :headers (clj->js
                               {"Content-Type" "application/json+transit"})
                     :body (transit/clj->transit {:command command
                                                  :payload payload})})
        (.then #(.text %))
        (.then (fn [text]
                 (let [data (transit/transit->clj text)]
                   (when success-message
                     (e! (snackbar-controller/->OpenSnackBar success-message :success)))
                   (if result-path
                     (e! (->RPCResponse result-path data))
                     (e! (result-event data)))))))))

(defmethod tuck-effect/process-effect :navigate [_ {:keys [page params query]}]
  (routes/navigate! page params query))

(defmethod tuck-effect/process-effect :set-api-token [e! {token :token}]
  (assert token "Must specify :token to set as new API token")
  (reset! api-token token)
  (reset! postgrest-ui.impl.fetch/fetch-impl (partial fetch* e! nil)))

(defmethod tuck-effect/process-effect :clear-api-token [e! _]
  (reset! api-token nil))

(defmulti map-item-selected :map/type)

(defn refresh-page [app]
  (let [path [:route (keyword (str (name (:page app)) "-refresh"))]]
    (update-in app path (fnil inc 0))))

;; Refresh the current page query state
(defrecord Refresh []
  t/Event
  (process-event [_ app]
    ;; Update the refresh indicator value so query component will force a refetch
    (refresh-page app)))

(def refresh-fx
  "Tuck effect that refreshes the current page state from database."
  (fn [e!] (e! (->Refresh))))

(defn update-page-state
  "Apply update fn to path in current page state."
  [{page :page :as app} path update-fn & args]
  (update-in app (into [:route page] path)
             (fn [page-state]
               (apply update-fn page-state args))))

(defn page-state
  "Get the state of the current page.
  If path components are given, takes that path from page-state."
  [{page :page :as app} & path]
  (get-in app (into [:route page] path)))

(defn feature-enabled? [feature]
  {:pre [(keyword? feature)]}
  (boolean (feature @enabled-features)))

(defn when-feature [feature component]
  (when (feature-enabled? feature)
    component))
