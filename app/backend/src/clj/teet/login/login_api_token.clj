(ns teet.login.login-api-token
  "Create JWT token for PostgREST API"
  (:import (java.security SecureRandom)
           (com.nimbusds.jose JWSAlgorithm JWSSigner JWSObject JWSHeader JWSVerifier Payload)
           (com.nimbusds.jose.crypto MACSigner MACVerifier)
           (java.util Date))
  (:require [cheshire.core :as cheshire]))

(def one-hour-ms (* 1000 60 60))

(defn- numeric-date [dt]
  (int (/ (.getTime dt) 1000)))

(defn create-token [shared-secret role {:keys [given-name family-name person-id email] :as user}]
  (-> (JWSObject.
       (JWSHeader. JWSAlgorithm/HS256)
       (Payload. (cheshire/encode {:role role
                                   :given_name given-name
                                   :family_name family-name
                                   :sub person-id
                                   :email email
                                   :iat (numeric-date (Date.))
                                   :exp (numeric-date (Date. (+ one-hour-ms (System/currentTimeMillis))))})))
      (doto (.sign (MACSigner. shared-secret)))
      .serialize))
