(ns teet.login.login-fake-routes
  "Routes for local development mode fake login."
  (:require [compojure.core :refer [routes GET]]))

(defn fake-login-routes []
  (routes
   ;; PENDING: we could add a simple for to select what dummy user identity to use
   (GET "/oauth2/request" _
        {:status 302
         :headers {"Location" "/"}
         :session {:user {:given-name "John"
                          :family-name "Doe"
                          :person-id "EE1122334455"
                          :email "john.doe@example.com"}}})))
