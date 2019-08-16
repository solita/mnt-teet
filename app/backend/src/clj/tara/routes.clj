(ns tara.routes
  (:require [compojure.core :refer [GET routes]]
            [org.httpkit.client :as client]
            [cheshire.core :as cheshire]
            [tara.json :as json]
            [taoensso.timbre :as log])
  (:import (java.util UUID Base64)
           (java.net URLEncoder)))

(defn- enc [data]
  (URLEncoder/encode data))

(defn auth-request [{:keys [authorization-endpoint] :as tara-endpoint}
                    {:keys [base-url client-id cookie-name] :as  client-properties}
                    {:keys [params] :as req}]
  (println "TARA REQ: " (pr-str req))
  (let [state (str (UUID/randomUUID))
        nonce (str (UUID/randomUUID))
        scope (get params "scope" "openid")
        url (str authorization-endpoint
                 "?scope=" (enc scope)
                 "&response_type=code"
                 "&client_id=" (enc client-id)
                 "&redirect_uri=" (enc (str base-url "/oauth2/idpresponse"))
                 "&state=" (enc state)
                 "&nonce=" (enc nonce)
                 (when-let [acr (get params "acr_values")]
                   (str "&acr_values=" (enc acr)))
                 (when-let [locales (get params "ui_locales")]
                   (str "&ui_locales=" (enc locales))))]
    (println "FORWARDING TO TARA AUTH: " url)
    {:status 302
     :headers {"Location" url
               "Set-Cookie" (str (or cookie-name "TARAClient") "=" state "; Secure; HttpOnly")}
     :body "Redirecting to TARA login"}))

(defn- base64-encode [string]
  (String. (.encode (Base64/getEncoder)
                    (.getBytes string))))

(defn auth-response [{:keys [token-endpoint] :as tara-endpoint}
                     {:keys [client-id client-secret base-url on-error on-success] :as client-properties}
                     {params :params :as req}]
  (log/info "TARA RESPONSE:" (pr-str req))
  (let [{:strs [code error error_description]} params]
    (if error
      ;; Returned from TARA with error
      (on-error {:error error
                 :description error_description})

      ;; Auth succeeded, request token
      (let [token-response @(client/post token-endpoint
                                         {:headers {"Authorization" (str "Basic "
                                                                         (base64-encode (str client-id ":" client-secret)))
                                                    "Content-Type" "application/x-www-form-urlencoded"}
                                          :body (str "grant_type=authorization"
                                                     "&code=" (enc code)
                                                     "&redirect_uri=" (enc base-url))
                                          :as :text})
            token (-> token-response :body json/parse)]
        (on-success token)))))

(defn tara-routes
  "Return a handler for authentication routes for the given TARA endpoint config map and
  client properties map."
  [tara-endpoint client-properties]
  (routes
   (GET "/oauth2/request" req
        (auth-request tara-endpoint client-properties req))
   (GET "/oauth2/idpresponse" req
        (auth-response tara-endpoint client-properties req))))
