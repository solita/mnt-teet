(ns teet.login.login-api-token
  "Create JWT token for PostgREST API"
  (:import (java.security SecureRandom)
           (com.nimbusds.jose JWSAlgorithm JWSSigner JWSObject JWSHeader JWSVerifier Payload)
           (com.nimbusds.jose.crypto MACSigner MACVerifier)
           (com.nimbusds.jwt SignedJWT JWTClaimsSet)
           (java.util Date))
  (:require [cheshire.core :as cheshire]))

(def one-hour-ms (* 1000 60 60))

(defn- numeric-date [dt]
  (int (/ (.getTime dt) 1000)))

(defn create-token [shared-secret role {:keys [given-name family-name id person-id email] :as user}]
  (-> (JWSObject.
       (JWSHeader. JWSAlgorithm/HS256)
       (Payload. (cheshire/encode {:role role
                                   :given_name given-name
                                   :family_name family-name
                                   :id id
                                   :sub person-id
                                   :email email
                                   :iat (numeric-date (Date.))
                                   :exp (numeric-date (Date. (+ one-hour-ms (System/currentTimeMillis))))})))
      (doto (.sign (MACSigner. shared-secret)))
      .serialize))

(defn verify-token
  "Verify JWT token validity. Returns user info on success. Throws exception on failure."
  [shared-secret token]
  (let [signed-id-token (SignedJWT/parse token)
        claims-set (.getJWTClaimsSet signed-id-token)
        claims (.getClaims claims-set)]

    (when-not (.verify signed-id-token (MACVerifier. shared-secret))
      (throw (ex-info "Could not verify JWT signature"
                      {:invalid-token signed-id-token})))

    (when (.before (.getExpirationTime claims-set) (java.util.Date.))
      (throw (ex-info "JWT token is expired"
                      {:expiration-time (.getExpirationTime claims-set)})))

    (let [{:strs [sub given_name family_name role email id]} claims]
      (when-not (= role "teet_user")
        (throw (ex-info "Unexpected role in JWT token"
                        {:expected-role "teet_user"
                         :actual-role role})))
      #:user {:given-name given_name
              :family-name family_name
              :email email
              :person-id sub
              :id (java.util.UUID/fromString id)})))
