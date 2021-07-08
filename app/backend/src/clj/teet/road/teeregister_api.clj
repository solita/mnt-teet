(ns teet.road.teeregister-api
  "Basic API code for Teeregister REST API."
  (:require [clj-http.client :as client]
            [clojure.set :as set]
            [clojure.string :as str]
            [cheshire.core :as cheshire]
            [teet.road.road-model :as road-model])
  (:import (java.util Date)))

(defrecord Client [endpoint username password credentials-atom])

(defn ->client [url username password]
  (->Client url username password (atom nil)))

(def cached-client (memoize ->client))

(defn create-client
  "Create client from client configuration."
  [{:keys [endpoint username password]}]
  (cached-client endpoint username password))

(defn- token-request
  "Issue an authentication request to get API token and expiration."
  [{:keys [endpoint username password] :as client}]
  (try
    (let [response
          (client/post (str endpoint "/oauth2/apitoken")
                       {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                        :body (str "user=" username "&password=" password)
                        :as :json})
          {:keys [access_token expires_in]} (:body response)]
      {:token access_token
       :expires (Date. (+ (System/currentTimeMillis)
                          ;; Refresh after 90% of expiration time has passed
                          (long (* 0.9 expires_in 1000))))})
    (catch Exception e
      (throw (ex-info "Error in Teeregister API authentication token request"
                      {:cause e
                       :client client})))))

(defn- authenticate
  "Authenticate client if needed. Returns credentials.
  Does a token request if client does not already have a valid token."
  [{:keys [credentials-atom] :as client}]
  (let [{:keys [token expires] :as creds} @credentials-atom]
    (if (and token expires (.after expires (Date.)))
      creds
      (swap! credentials-atom (fn [_] (token-request client))))))

(defn- get-request [client api-path params]
  (let [{token :token} (authenticate client)]
    (try
      (:body (client/get (str (:endpoint client) api-path)
                         {:headers {"Authorization" (str "Bearer " token)}
                          :query-params params
                          :as :json}))
      (catch Exception e
        (let [d (ex-data e)
              error (when (str/starts-with? (get-in d [:headers "Content-Type"])
                                            "application/json")
                      (cheshire/decode (:body d) keyword))]
          (throw (ex-info "Exception in Teeregister API call"
                          (merge {:cause e}
                                 (when error
                                   {:teet/error :teeregister-api-error
                                    :teet/error-message (:errorMessage error)})))))))))

(def ^{:private true :const true}
  key-remapping
  {:teeNumber [:road-nr]
   :soiduteeNr [:carriageway]
   :teeNimi [:road-name]
   :algus [:start-km road-model/m->km]
   :lopp [:end-km road-model/m->km]
   :kaugus [:distance]
   :maadress [:km road-model/m->km]})

(def ^:private remap-response-keys-xf
  (map #(into {}
              (map (fn [[key val]]
                     (let [[new-key process-fn] (key-remapping key)
                           new-key (or new-key key)
                           process-fn (or process-fn identity)]
                       [new-key (process-fn val)])))
              %)))

(defn road-by-geopoint
  "Return roads within given distance of point [x y]."
  [client distance [x y]]
  (into []
        remap-response-keys-xf
        (:qmaadressByPoint
         (get-request client "/api/road/bygeopoint"
                      {:x x
                       :y y
                       :dist distance}))))

(defn road-by-2-geopoints
  "Get road sections with line geometry for 2 geopoints within distance."
  [client distance [x1 y1] [x2 y2]]
  (into []
        remap-response-keys-xf
        (:qlineBy2Points
         (get-request client "/api/road/by2geopoint"
                      {:x1 x1 :y1 y1
                       :x2 x2 :y2 y2
                       :dist distance}))))

(defn point-by-road
  "Get [x y] point for the given road address."
  [client road carriageway start-m]
  (:coordinates
   (get-request client "/api/geo/roadpoint"
                {:teeNumber road
                 :soiduteeNr carriageway
                 :mAadress start-m})))

(defn line-by-road
  "Get linestring [[x1 y1] ... [xN yN]] geometry for a road section."
  [client road carriageway start-m end-m]
  (:coordinates
   (get-request client "/api/geo/roadline"
                {:teeNumber road
                 :soiduteeNr carriageway
                 :mAadress1 start-m
                 :mAadress2 end-m})))

(defn road-search
  "Search roads by name.
  Default for max-results is 50."
  ([client search-text]
   (road-search client search-text 50))
  ([client search-text max-results]
   (into []
         remap-response-keys-xf
         (get-request client "/api/road/like/nimetus"
                      {:searchText search-text
                       :maxResults max-results}))))

(defn road-name
  "Fetch road-name for road number and carriageway."
  [client road-nr carriageway]
  (some #(when (and (= road-nr (:teeNumber %))
                    (= carriageway (:soiduteeNr %)))
           (:teeNimi %))
        (get-request client "/api/road/like/nimetus"
                     {:searchText (str road-nr)
                      :maxResults 100})))

;; PENDING: following not implemented
;; /api/road/hooldaja
;; /api/road/hoole
;; /api/road/seisunditase

;; riigiteel määratud punkt ei ole arvestuslik
;; => municipal road in this same place
;;   covers area of the state road
;;
;; should use the municipal road location
;; arvestuslik = "countable"
;;
;;
