(ns teet.login.login-controller
  (:require [tuck.core :as t]
            [tuck.effect :as tuck-effect]
            [taoensso.timbre :as log]
            [teet.login.login-paths :as login-paths]
            [teet.user.user-info :as user-info]))

(defrecord StartLoginAttempt [demo-user])
(defrecord SetToken [after-login? navigate-data token])
(defrecord FailLoginAttempt [demo-user])
(defrecord LoginWithValidSession [navigate-data user])
(defrecord SetPassword [pw])
(defrecord RefreshToken [])

(def ^{:const true
       :doc "How often to refresh JWT token (15 minutes)"}
  refresh-token-timeout-ms (* 1000 60 15))

(extend-protocol t/Event
  StartLoginAttempt
  (process-event [{user :demo-user} app]
    (log/info "Log in as: " user)
    (let [navigate-data (get-in app [:login :navigate-to])
          site-password (get-in app [:login :password])]
      (t/fx (-> app
              (assoc-in [:login :progress?] true)
              ;; (assoc :user user)
              )
        {::tuck-effect/type :query
         :query :user-session
         :args {:user user :site-password site-password}
         :result-event (fn session-q-result-event [result]
                         (log/info "session reply:" result)
                         (if (get result "ok")
                           (->LoginWithValidSession navigate-data user)
                           (->FailLoginAttempt user)))})))

  SetPassword
  (process-event [{:keys [pw]} app]    
    (t/fx (assoc-in app [:login :password] pw)))

  FailLoginAttempt
  (process-event [{user :demo-user} app]
    (log/info "login attempt failed for user", user)
    (js/alert "Bad password or other login error")
    (t/fx (assoc-in app [:login :progress?] false)))
  
  SetToken
  (process-event [{:keys [token after-login? navigate-data]} app]
    (log/info "TOKEN: " token ", after-login? " after-login?)
    (let [effects [{::tuck-effect/type :set-api-token
                    :token token}
                   {::tuck-effect/type :debounce
                    :id :refresh-token
                    :timeout refresh-token-timeout-ms
                    :event ->RefreshToken}]
          effects (if after-login?
                    (conj effects (merge {::tuck-effect/type :navigate
                                          :page :projects}
                                    navigate-data))
                    effects)]
      (apply t/fx
             (-> app
                 (assoc-in login-paths/api-token token)
                 (assoc-in [:login :progress?] false))
             effects)))

  LoginWithValidSession
  ;; This is called after StartLoginAttempt gets a valid session and site password check is ok.
  ;; Will call :login command, and then fire off SetToken event on success.
  (process-event [{:keys [navigate-data user]} app]
    (log/info "doing login after session estabilished, user" user)
    (t/fx (-> app
               (assoc-in [:login :progress?] true)
               (assoc :user user))
    {::tuck-effect/type :command!
     :command :login
     :payload user
     :result-event (partial ->SetToken true navigate-data)}))

  RefreshToken
  (process-event [_ app]
    (t/fx app
          {::tuck-effect/type :command!
           :command :refresh-token
           :payload {}
           :result-event (partial ->SetToken false nil)})))

(def mock-users user-info/mock-users)
