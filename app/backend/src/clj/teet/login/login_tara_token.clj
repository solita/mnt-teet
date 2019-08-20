(ns teet.login.login-tara-token
  "Handle login with TARA authentication token. Takes a successfull TARA login
  claims set and extracts user information.

  TARA has the following claims we are interested in:

  sub                             Estonian person ID (EE10101010005)
  profile_attributes.given_name   first name (DEMO)
  profile_attributes.family_name  last name (SMART-ID)
  email                           email address (may not be present)
  "
  (:require [taoensso.timbre :as log]))

(defn tara-claims->user-info [claims-set]
  {:given-name (get-in claims-set ["profile_attributes" "given_name"])
   :family-name (get-in claims-set ["profile_attributes" "family_name"])
   :person-id (get claims-set "sub")
   :email (get claims-set "email")})

(defn tara-success-handler [base-url claims-set]
  (log/debug "TARA auth success, claims: " claims-set)
  {:status 302
   :session {:user (tara-claims->user-info claims-set)}
   :headers {"Location" base-url}})

(defn tara-error-handler [error]
  (log/error "TARA auth error" error)
  {:status 500
   :body "Error processing TARA auth"})
