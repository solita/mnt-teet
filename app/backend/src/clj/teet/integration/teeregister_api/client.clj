(ns teet.integration.teeregister-api.client
  "Basic API code for Teeregister REST API."
  (:require [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [clojure.set :as set])
  (:import (java.util Date)
           (java.net URLEncoder)))

(defrecord Client [endpoint username password credentials-atom])

(defn ->client [url username password]
  (->Client url username password (atom nil)))

(defn- token-request [{:keys [endpoint username password]}]
  (let [response
        (client/with-connection-pool {:insecure? true}
          (client/post (str endpoint "/oauth2/apitoken")
                       {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                        :body (str "user=" username "&password=" password)
                        :as :json}))]
    (if (= (:status response) 200)
      (let [{:keys [access_token expires_in]} (:body response)]
        {:token access_token
         :expires (Date. (+ (System/currentTimeMillis)
                            ;; Refresh after 90% of expiration time has passed
                            (long (* 0.9 expires_in 1000))))})
      (throw (ex-info "Error in Teeregister API authentication token request"
                      {:response response
                       :endpoint endpoint})))))

(defn- authenticate [{:keys [credentials-atom] :as client}]
  (let [{:keys [token expires] :as creds} @credentials-atom]
    (if (and token expires (.after expires (Date.)))
      creds
      (swap! credentials-atom (fn [_] (token-request client))))))

(defn- get-request [client api-path params]
  (let [{token :token} (authenticate client)
        response
        (client/with-connection-pool {:insecure? true}
          (client/get (str (:endpoint client) api-path)
                      {:headers {"Authorization" (str "Bearer " token)}
                       :query-params params
                       :as :json}))]
    (:body response)))

(def ^{:private true :const true}
  key-remapping
  {:teeNumber :road-nr
   :soiduteeNr :carriageway
   :teeNimi :road-name})

(defn road-by-geopoint [client x y dist]
  (into []
        (map #(set/rename-keys % key-remapping))
        (:qmaadressByPoint
         (get-request client "/api/road/bygeopoint"
                      {:x x
                       :y y
                       :dist dist}))))
