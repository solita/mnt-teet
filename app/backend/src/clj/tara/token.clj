(ns tara.token
  (:import (com.nimbusds.jwt SignedJWT JWTClaimsSet)
           (com.nimbusds.jose JWSVerifier)
           (com.nimbusds.jose.crypto RSASSAVerifier)))

;; https://dev-teet.solitacloud.fi/oauth2/idpresponse?code=OC-106--t8TLuViZkr8mfvx1G18gU4AioH-BA7q&state=ZXlKMWMyVnlVRzl2YkVsa0lqb2laWFV0WTJWdWRISmhiQzB4WDFRMFFrMHpWRGhTVnlJc0luQnliM1pwWkdWeVRtRnRaU0k2SW5SaGNtRXRkR1Z6ZEM1eWFXRXVaV1VpTENKamJHbGxiblJKWkNJNklqTndkalpsYUdKa01XNXNaREEyT1dGc2MyWmlNMkpxT0dFMklpd2ljbVZrYVhKbFkzUlZVa2tpT2lKb2RIUndjem92TDJGd2NDMWtaWFl0ZEdWbGRDNXpiMnhwZEdGamJHOTFaQzVtYVNJc0luSmxjM0J2Ym5ObFZIbHdaU0k2SW5SdmEyVnVJaXdpY0hKdmRtbGtaWEpVZVhCbElqb2lUMGxFUXlJc0luTmpiM0JsY3lJNld5SmhkM011WTI5bmJtbDBieTV6YVdkdWFXNHVkWE5sY2k1aFpHMXBiaUlzSW05d1pXNXBaQ0lzSW5CeWIyWnBiR1VpWFN3aWMzUmhkR1VpT201MWJHd3NJbU52WkdWRGFHRnNiR1Z1WjJVaU9tNTFiR3dzSW1OdlpHVkRhR0ZzYkdWdVoyVk5aWFJvYjJRaU9tNTFiR3dzSW01dmJtTmxJam9pUXpSbmRVbElVbWd3WHpSUWFtbEpaMkozYkhBeVF6VmlOVk5YY0VKRE5ERTRZVkJ5UTJ4QlRsOUpkbVoyUkRkNlRYbDJWazk2U1VaM2EwdDNRVlpyU2xOTlNHVldNVU00VGxONk0zTXdTM2s0U1ZwNFZYZHBNamxMU1drNVFYUlNaVU5CWDJGSk5sUmFWRGRsUjJ3dFZWcE5kazFRWjNCVmVqZENWRWx0VDBaS1FqSlBYMDlzUTNBelZHaFJUMjFGWkZaWGNFVXRXSGh0VnpaamFURkxVRFE0ZDJwek5FbzJWMEpOSWl3aWMyVnlkbVZ5U0c5emRGQnZjblFpT2lKa1pYWXRkR1ZsZEM1emIyeHBkR0ZqYkc5MVpDNW1hU0lzSW1OeVpXRjBhVzl1VkdsdFpWTmxZMjl1WkhNaU9qRTFOalUyT1RRNU5USXNJbk5sYzNOcGIyNGlPbTUxYkd3c0luVnpaWEpCZEhSeWFXSjFkR1Z6SWpwdWRXeHNMQ0pwYzFOMFlYUmxSbTl5VEdsdWEybHVaMU5sYzNOcGIyNGlPbVpoYkhObGZRPT06TTJhZzUwMTJyN1VHR0tUK29PRVNJcEtDUGVwaFNBMUN3WUx5VVNoZWZ3UT0%3D

(defn verify [{:keys [public-key] :as tara-endpoint} {:keys [id-token] :as token} expected-state]
  (let [signed-id-token (SignedJWT/parse id-token)
        {:strs [state] :as claims} (.getClaims (.getJWTClaimsSet signed-id-token))]
    (when-not (.verify signed-id-token (RSASSAVerifier. public-key))
      (throw (ex-info "Could not verify JWT signature")))
    (when-not (= expected-state state)
      (throw (ex-info "JWT claim state was not the expected value"
                      {:expected-state expected-state
                       :state state
                       :jwt signed-id-token})))
    ;(when-not (= ))
    )
  )
