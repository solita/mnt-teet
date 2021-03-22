(ns tara.routes
  (:require [compojure.core :refer [GET routes]]
            [org.httpkit.client :as http]
            [tara.json :as json]
            [teet.log :as log]
            [tara.token :as token]
            [clojure.string :as str])
  (:import (java.util UUID Base64)
           (java.net URLEncoder)))

(defn- enc [data]
  (URLEncoder/encode data))

(defn auth-request [{:keys [authorization-endpoint]}
                    {:keys [base-url client-id cookie-name scopes]}
                    {:keys [params] :as req}]
  (println "TARA REQ: " (pr-str req))
  (let [state (str (UUID/randomUUID))
        nonce (str (UUID/randomUUID))
        scopes (or scopes ["openid"])
        url (str authorization-endpoint
                 "?scope=" (enc (str/join " " scopes))
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
  (.encodeToString (Base64/getEncoder)
                   (.getBytes string "UTF-8")))

(defn auth-response [{:keys [token-endpoint] :as tara-endpoint}
                     {:keys [client-id client-secret base-url on-error on-success cookie-name]
                      :as client-properties}
                     {params :params :as req}]
  (log/info "TARA RESPONSE:" (pr-str req))
  (log/info "CLIENT PROPERTIES: " (pr-str client-properties))
  (log/info "TARA ENDPOINT: " (pr-str tara-endpoint))
  (let [{:strs [code error error_description]} params]
    (if error
      ;; Returned from TARA with error
      (on-error {:error error
                 :description error_description})

      ;; Auth succeeded, request token
      (let [token-request {:headers {"Authorization" (str "Basic "
                                                          (base64-encode (str client-id ":" client-secret)))
                                     "Content-Type" "application/x-www-form-urlencoded"}
                           :body (str "grant_type=authorization_code"
                                      "&code=" (enc code)
                                      "&redirect_uri=" (enc (str base-url "/oauth2/idpresponse")))
                           :as :text}
            _ (log/info "TOKEN REQUEST: " (pr-str token-request) ", ENDPOINT: " token-endpoint)
            token-response @(http/post token-endpoint token-request)
            _ (log/info "TOKEN RESPONSE: " token-response)
            token (-> token-response :body json/parse)]
        (if (contains? token :id-token)
          (try
            (let [claims (token/verify tara-endpoint
                                       token
                                       (get-in req [:cookies (or cookie-name "TARAClient") :value])
                                       client-id)]
              (log/info "GOT CLAIMS: " claims)
              (on-success claims))
            (catch Exception e
              (on-error {:error "Exception thrown while verifying JWT token"
                         :exception e})))

          (on-error {:error "Received invalid token"
                     :received-token token}))))))

(defn tara-routes
  "Return a handler for authentication routes for the given TARA endpoint config map and
  client properties map."
  [tara-endpoint client-properties]
  (routes
   (GET "/oauth2/request" req
        (auth-request (tara-endpoint) client-properties req))
   (GET "/oauth2/idpresponse" req
        (auth-response (tara-endpoint) client-properties req))))
