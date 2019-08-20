(ns teet.main
  (:require [org.httpkit.server :as server]
            [teet.routes :as routes]
            [tara.routes :as tara-routes]
            [tara.endpoint :as tara-endpoint]
            [compojure.core :refer [routes]]
            [ring.middleware.params :as params]
            [ring.middleware.cookies :as cookies]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as session-cookie]
            [taoensso.timbre :as log])
  (:gen-class))

(def server nil)

(defn start []
  (let [port 3000]
    (alter-var-root
     #'server
     (fn [_]
       (log/info "Starting TEET service in port " port)
       (-> (routes
            (tara-routes/tara-routes (tara-endpoint/discover "https://tara-test.ria.ee/oidc")
                                     {:client-id (System/getenv "TARA_CLIENT_ID")
                                      :client-secret (System/getenv "TARA_CLIENT_SECRET")
                                      :base-url (System/getenv "BASE_URL")
                                      :scopes ["openid" "email"]
                                      :on-error #(do
                                                   (log/error "TARA auth error" %)
                                                   {:status 500
                                                    :body "Error processing TARA auth"})
                                      :on-success #(do
                                                     (log/info "TARA auth success, token: " %)
                                                     {:status 302
                                                      ;; FIXME: create token for postgrest
                                                      :session {:user {:given-name (get-in % ["profile_attributes"])} %}
                                                      :headers {"Location" (System/getenv "BASE_URL")}})})
            (routes/teet-routes))
           params/wrap-params
           cookies/wrap-cookies
           (session/wrap-session {:store (session-cookie/cookie-store {:key (.getBytes "FIXME:USE PARAMS")})})
           (server/run-server {:ip "0.0.0.0"
                               :port port}))))))

(defn stop []
  (server))

(defn restart []
  (when server
    (stop))
  (start))

(defn -main [& args]
  (start))
