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
            [teet.transit :as transit]
            [teet.localization :refer [tr tr-or]]
            [alandipert.storage-atom :refer [local-storage]]
            postgrest-ui.impl.fetch
            [goog.math.Long
             :refer [fromString fromNumber]
             :rename {fromString string->long
                      fromNumber number->long}]
            [clojure.tools.reader :as reader]))

(defn ->long [x]
  (cond
    (instance? goog.math.Long x)
    x

    (string? x)
    (string->long x)

    (number? x)
    (number->long x)

    :else
    (throw (ex-info "Can't coerce to goog.math.Long. Expected Long instance, string or JS number."
                    {:value x}))))

;; Track how many requests are in flight to show progress
(def in-flight-requests (r/atom 0))


(defn in-flight-requests?
  "Returns true if there are currently any requests in flight."
  []
  (not (zero? @in-flight-requests)))

(def api-token routes/api-token)
(defonce enabled-features (r/cursor app-state/app [:enabled-features]))
(defonce navigation-data-atom (local-storage (atom nil) :navigation-data))

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

(defrecord DebounceEffect [effect])
(defrecord RPC [rpc-effect-params])
(defrecord AuthorizationResult [cache-key result])
(defrecord RPCResponse [path data])
(defrecord Navigate [page params query])
(defrecord NavigateWithExistingAsDefault [page params query])
(defrecord NavigateWithSameParams [page])
(defrecord QueryUserAccess [action entity result-event])
(defrecord SetQueryParam [param value]) ; navigate to same page but set set single query param
(defrecord ResponseError [err]) ; handle errors in HTTP response
(defrecord ModalFormResult [close-event response])          ; Handle the submit result of form-modal-button

(defonce debounce-timeouts (atom {}))

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
                                                 (or (-> err ex-data :error-message)
                                                     [:error :server-error]))
                                      :error))

(defmethod on-server-error :default [err app]
  (default-server-error-handler err app))

(defmethod on-server-error :authorization-failure [_ app]
  (reset! api-token nil)
  (when-not (= :login (:page app))
    (reset! navigation-data-atom (select-keys app [:page :params :query])))
  (t/fx (-> app
            (dissoc :user)
            (snackbar-controller/open-snack-bar (tr [:error :authorization-failure])
                                                :warning))
        {:tuck.effect/type :navigate
         :page :login}))

(defmethod on-server-error :forbidden-resource [_ app]
  (assoc app :page :unauthorized)                           ;; Don't actually navigate because we want to show the unauthorized resources url
  )

(defn route-params
  "Return map of current route params for the given paramater names."
  [{:keys [route page params] :as _app} param-names]
  (into {}
        (map (fn [p]
               [p (or (get params p)
                      (get-in route [page :navigation p]))]))
        param-names))

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

  NavigateWithSameParams
  (process-event [{page :page} {params :params :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page page
           :params params
           :query {}}))

  QueryUserAccess
  (process-event [{action :action
                   entity :entity
                   result-event :result-event} app]
    (let [entity-type (cond                                 ;; TODO need to figure out entity type better somehow
                        (get-in app [:query :partner])
                        :company
                        (get-in app [:params :contract-ids])
                        :contract
                        (get-in app [:params :task])
                        :target
                        (get-in app [:params :activity])
                        :target
                        (get-in app [:params :file])
                        :file
                        (get-in app [:params :project])
                        :target
                        :else
                        nil)]
      (t/fx app
            {:tuck.effect/type :query
             :query :authorization/user-is-permitted?
             :args (merge {:entity-id (:db/id entity)
                           :project-id (get-in app [:params :project])
                           :action action}
                          (when entity-type
                            {entity-type (:db/id entity)}))
             :result-event result-event})))

  AuthorizationResult
  (process-event [{cache-key :cache-key
                   result :result} app]
    (update app :authorization/contract-permissions assoc cache-key result))

  SetQueryParam
  (process-event [{:keys [param value]} {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page page
           :params params
           :query (if (nil? value)
                    (dissoc query param)
                    (assoc query param value))}))

  NavigateWithExistingAsDefault
  (process-event [{new-page :page new-params :params new-query :query} {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page (or new-page page)
           :params (or new-params params)
           :query (or new-query query)}))


  ResponseError
  (process-event [{err :err} app]
    (on-server-error err app))

  ModalFormResult
  (process-event [{close-event :close-event} app]
    (t/fx app
          (fn [e!]
            (e! (close-event)))
          refresh-fx)))

(defn check-response-status [response]
  (let [status (.-status response)]
    (case status
      401
      (throw (ex-info "Authorization failure" {:error :authorization-failure}))

      403
      (throw (ex-info "Unauthorized to access this resource" {:error :forbidden-resource}))

      504
      (throw (ex-info "Gateway timeout" {:error :request-timeout}))

      200
      (do
        (swap! in-flight-requests dec)
        response)

      (throw (ex-info "Request failure"
                      (merge
                       {:error (or (some-> response
                                           .-headers
                                           (.get "X-TEET-Error")
                                           keyword)
                                   :unknown-server-error)}
                       (when-let [msg (some-> response .-headers
                                              (.get "X-TEET-Error-Message")
                                              js/decodeURIComponent)]
                         {:error-message msg})))))))

(defn catch-response-error [e! error-event]
  (fn [err]
    (swap! in-flight-requests dec)
    (e! (if error-event
          (error-event err)
          (->ResponseError err)))
    (.reject js/Promise (new js/Error (str err)))))

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
          (let [{:strs [done value]} (js->clj (.next ks))]
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

(defn- fetch-or-timeout
  "Fetch request or timeout if response doesn't arrive in `timeout-ms`"
  [url options timeout-ms]
  (js/Promise.race #js
   [(js/fetch url options)
    (js/Promise. (fn [_ reject]
                   (js/setTimeout (fn []
                                    (reject (ex-info "Server timeout"
                                                     {:error :request-timeout})))
                                  timeout-ms)))]))

(def ^:private default-fetch-timeout-ms 30000)

(defn fetch*
  "Call JS fetch API. Automatically adds response code check and error handling.
  Returns a Promise yielding a response data or app-state map."
  [e! error-event & [url authless-arg-map-js fetch-timeout-ms]]
  {:post [(instance? js/Promise %)]}
  (swap! in-flight-requests inc)
  (-> (fetch-or-timeout url
                        (-> authless-arg-map-js
                            js->clj
                            (update "headers" add-authorization)
                            clj->js)
                        (or fetch-timeout-ms
                            default-fetch-timeout-ms))
      (.then check-response-status)
      (.catch (catch-response-error e! error-event))))

(defmethod tuck-effect/process-effect :rpc [e! {:keys [rpc args result-path result-event
                                                       endpoint method loading-path
                                                       json? error-event timeout-ms] :as q}]
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
                                 args))
                  timeout-ms))

          ;; POST request, send parameters as JSON body
          (fetch* e! error-event
                  (str endpoint "/rpc/" rpc)
                  #js {:method "POST"
                       :headers (clj->js {"Content-Type" "application/json"
                                          "Accept" "application/json"})
                       :body (-> args clj->js js/JSON.stringify)}
                  timeout-ms))
        (.then #(when % (.json %)))
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


(defmethod tuck-effect/process-effect :query [e! {:keys [query args result-path
                                                         result-event method error-event
                                                         timeout-ms]
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
                          #js {:method "GET"}
                          timeout-ms)
            "POST" (fetch* e! error-event
                           "/query/"
                           #js {:method "POST"
                                :headers (clj->js
                                          {"Content-Type" "application/json+transit"})
                                :body payload}
                           timeout-ms))
          (.then #(.text %))
          (.then (fn [text]
                   (let [data (transit/transit->clj text)]
                     (if result-path
                       (e! (->RPCResponse result-path data))
                       (e! (result-event data))))))))))

(defn- fetch-deploy-status
  "Checks if a new version of the app is currently being deployed by
  requesting the `js/deploy.json` file and checking the `status` of
  the JSON response. Returns a boolean promise."
  []
  (-> (fetch-or-timeout "js/deploy.json"
                        (clj->js {"Content-Type" "application/json"
                                  "Accept" "application/json"})
                        10000)
      (.then #(.json %))
      (.then (fn [json-response]
               (js/Promise.resolve (js->clj json-response))))))

;; Check deploy status, show corresponding snackbar message
(defmethod tuck-effect/process-effect :deploy-status [e! {:keys [result-event]}]
  (-> (fetch-deploy-status)
      (.then #(e! (result-event (= "deploying" (get % "status")))))))

(defrecord DeployStatusResponse [deploying?]
  t/Event
  (process-event [{:keys [deploying?]} app]
    (if deploying?
      (snackbar-controller/open-snack-bar {:app app
                                           :message (tr [:warning :deploying])
                                           :variant :warning
                                           :hide-duration nil})
      (snackbar-controller/open-snack-bar {:app app
                                           :message (tr [:error :request-timeout])
                                           :variant :error
                                           :hide-duration nil}))))

;; disabled 2020-03-02 pending debugging
#_(defmethod on-server-error :request-timeout [_ app]
  (t/fx app {:tuck.effect/type :deploy-status
             :result-event ->DeployStatusResponse}))

(def ^:private poll-timeout-ms 30000)

(defonce version-seen-on-startup (atom nil))
(defn poll-version
  "Polls whether the client's version of the app is the same as the one
  currently deployed. If not, shows a warning that a new version is
  being, or has been, deployed."
  [e!]
  (-> (fetch-deploy-status)
      (.then (fn [{:strs [commit status]}]
               (when (and (some? commit)
                          (nil? @version-seen-on-startup))
                 (reset! version-seen-on-startup commit))

               (js/setTimeout #(poll-version e!) poll-timeout-ms)
               (cond
                 (= status "deploying")
                 (e! (snackbar-controller/->OpenSnackBarWithOptions
                      {:message (tr [:warning :deploying])
                       :variant :warning
                       :hide-duration nil}))

                 (and (some? commit) (not= commit @version-seen-on-startup))
                 (e! (snackbar-controller/->OpenSnackBarWithOptions
                      {:message (tr [:warning :version-mismatch])
                       :variant :warning
                       :hide-duration nil})))))))

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


(defmethod tuck-effect/process-effect :command! [e! {:keys [command payload result-path
                                                            result-event success-message error-event
                                                            timeout-ms] :as q}]
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
                                                  :payload payload})}
                timeout-ms)
        (.then #(.text %))
        (.then (fn [text]
                 (let [data (transit/transit->clj text)]
                   (when success-message
                     (e! (snackbar-controller/->OpenSnackBar success-message :success)))
                   (if result-path
                     (e! (->RPCResponse result-path data))
                     (when (not= :ignore result-event)
                       (e! (result-event data))))))))))

(defmethod tuck-effect/process-effect :navigate [_ {:keys [page params query]}]
  (routes/navigate! page params query))

(defmethod tuck-effect/process-effect :set-api-token [e! {token :token}]
  (assert token "Must specify :token to set as new API token")
  (reset! api-token token)
  (reset! postgrest-ui.impl.fetch/fetch-impl (partial fetch* e! nil)))

(defmethod tuck-effect/process-effect :clear-api-token [_e! _]
  (reset! api-token nil))

(defmulti map-item-selected :map/type)

(defn update-page-state
  "Apply update fn to path in current page state."
  [{page :page :as app} path update-fn & args]
  (update-in app (into [:route page] path)
             (fn [page-state]
               (apply update-fn page-state args))))

(defn assoc-page-state
  "Assoc value(s) to current page state."
  [{page :page :as app} & paths-and-values]
  (reduce (fn [app [path value]]
            (assoc-in app (into [:route page] path) value))
          app
          (partition 2 paths-and-values)))

(defn page-state
  "Get the state of the current page.
  If path components are given, takes that path from page-state."
  [{page :page :as app} & path]
  (get-in app (into [:route page] path)))

(defn page-state-path
  "Return path in app state for the page state."
  [{page :page} & path]
  (into [:route page] path))

(defn feature-enabled? [feature]
  {:pre [(keyword? feature)]}
  (boolean (feature @enabled-features)))

(defn when-feature [feature component]
  (when (feature-enabled? feature)
    component))

(defmethod on-server-error :bad-request [err app]
  (let [error (-> err ex-data :error)]
    ;; General error handler for when the client sends faulty data.
    ;; Commands can fail requests with :error :bad-request to trigger this
    (t/fx (snackbar-controller/open-snack-bar app (tr [:error error]) :warning))))


(defn internal-state
  "Use component internal state that is not in global app.
  Returns vector of [current-value-atom update-event].
  The current-value-atom can be dereferenced to get the current value of the internal state.
  The update-event is a function that takes new state and returns tuck event that sets it
  when processed.

  Use with reagent.core/with-let"
  ([initial-value]
   (internal-state initial-value {:merge? false}))
  ([initial-value {:keys [merge?]}]
   (let [internal-state-atom (r/atom initial-value)]
     [internal-state-atom
      (fn [new-value]
        (reify t/Event
          (process-event [_ app]
            (if merge?
              (swap! internal-state-atom merge new-value)
              (reset! internal-state-atom new-value))
            app)))])))


(defrecord UndoDelete [id undo-result-event]
  t/Event
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :meta/undo-delete
           :payload {:db/id id}
           :result-event (fn [result]
                           (or (and undo-result-event
                                    (undo-result-event result))
                               (->Refresh)))})))


;; Generic form save

(defn prepare-form-data
  "Generic form data preparation. Trims strings."
  [form]
  (reduce-kv
   (fn [form k v]
     (assoc form k
            (if (string? v)
              (str/trim v)
              v)))
   {} form))

(defrecord SaveFormResponse [on-success-fx response]
  t/Event
  (process-event [_ app]
    (log/info "successfx: " on-success-fx ", response: " response)
    (if-let [fx (if on-success-fx
                  (on-success-fx response)
                  refresh-fx)]
      (do
        (log/info "returning fx " fx)
        (t/fx app fx))
      app)))

(defrecord SaveForm
  [command form-data on-success-fx]
  t/Event
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :command!
           :command command
           :payload form-data
           :result-event (partial ->SaveFormResponse on-success-fx)})))

(defrecord SaveFormWithConfirmation
  [command form-data on-success-fx confirmation-message]
  t/Event
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :command!
           :command command
           :payload form-data
           :success-message confirmation-message
           :result-event (partial ->SaveFormResponse on-success-fx)})))


(defn ^:export test-command
  [command-name payload]
  (let [e! (t/control app-state/app)]
    (e! (reify t/Event
          (process-event [_ app]
            (t/fx app
              {::tuck-effect/type :command!
               :command (reader/read-string command-name)
               :payload (reader/read-string payload)
               :result-event #(reify t/Event
                                (process-event [result app]
                                  (println result)
                                  app))} ))))))
