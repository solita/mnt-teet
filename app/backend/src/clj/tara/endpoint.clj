(ns tara.endpoint
  (:require [org.httpkit.client :as http]
            [tara.json :as json])
  (:import (com.nimbusds.jose.jwk JWKSet)))

(defn- load-openid-configuration [url]
  (->  (str url "/.well-known/openid-configuration")
       http/get deref :body
       json/parse))

(defn- load-jwks [jwks-uri]
  (-> jwks-uri
      (http/get {:as :stream})
      deref :body slurp
      JWKSet/parse))

(defn discover
  "Discover OpenID configuration and load JWKS public key."
  [oidc-url]
  (let [{:keys [jwks-uri] :as oidc-config} (load-openid-configuration oidc-url)]
    (when-not jwks-uri
      (throw (ex-info "No JWKS URI in configuration" {:loaded-configuration oidc-config})))
    (let [jwk (load-jwks jwks-uri)]
      (merge oidc-config
             {:public-key (-> jwk .getKeys (.get 0) .toRSAPublicKey)}))))
