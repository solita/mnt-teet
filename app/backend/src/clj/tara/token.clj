(ns tara.token
  (:import (com.nimbusds.jwt SignedJWT JWTClaimsSet)
           (com.nimbusds.jose JWSVerifier)
           (com.nimbusds.jose.crypto RSASSAVerifier)))

(defn verify
  "Verify JWT token validity. Returns claims on success.
  Throws exception on failure."
  [{:keys [public-key issuer] :as tara-endpoint}
   {:keys [id-token] :as token}
   expected-state expected-audience]
  (let [signed-id-token (SignedJWT/parse id-token)
        claims-set (.getJWTClaimsSet signed-id-token)
        {:strs [state] :as claims} (.getClaims claims-set)]
    (when-not (.verify signed-id-token (RSASSAVerifier. public-key))
      (throw (ex-info "Could not verify JWT signature"
                      {:invalid-token signed-id-token
                       :public-key public-key})))
    (when-not (= expected-state state)
      (throw (ex-info "JWT claim state was not the expected value"
                      {:expected-state expected-state
                       :state state
                       :jwt signed-id-token})))
    (when-not (= (.getIssuer claims-set) issuer)
      (throw (ex-info "JWT claims set has unexpected issuer"
                      {:expected-issuer issuer
                       :issuer (.getIssuer claims-set)})))
    (when-not (some #(= expected-audience %) (.getAudience claims-set))
      (throw (ex-info "Expected audience is not included in JWT audience"
                      {:expected-audience expected-audience
                       :audience (.getAudience claims-set)})))
    (when (.before (.getExpirationTime claims-set) (java.util.Date.))
      (throw (ex-info "JWT token is expired"
                      {:expiration-time (.getExpirationTime claims-set)})))
    claims))
