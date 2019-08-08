(ns teet.login.login-controller
  (:require [tuck.core :as t]
            [tuck.effect :as tuck-effect]
            [taoensso.timbre :as log]))

(defrecord UpdateLoginForm [form])
(defrecord Login [])
(defrecord SetTokens [tokens])
(defrecord SetUserInfo [user])

(defmethod tuck-effect/process-effect :login [e! {:keys [username password  login-url]}]
  (-> (js/fetch login-url
                #js {:method "PUT"
                     :headers #js {"Content-Type" "application/json"}
                     :body (js/JSON.stringify #js {:username username
                                                   :password password})})
      (.then #(.json %))
      (.then (fn [json]
               (let [tokens (js->clj json)]
                 (e! (->SetTokens tokens)))))))

(defmethod tuck-effect/process-effect :fetch-user-info [e! {:keys [api-url token]}]
  (-> (js/fetch (str api-url "/rpc/whoami")
                #js {:headers #js {"Authorization" (str "Bearer " token)}})
      (.then #(.json %))
      (.then (fn [user-info]
               (log/info "Käyttjä" )
               (e! (->SetUserInfo (first (js->clj user-info :keywordize-keys true))))))))

(extend-protocol t/Event
  UpdateLoginForm
  (process-event [{form :form} app]
    (assoc-in app [:login :form] form))

  Login
  (process-event [_ app]
    (log/info "Log in")
    (t/fx (assoc-in app [:login :progress?] true)
          {::tuck-effect/type :login
           :username (get-in app [:login :form :username])
           :password (get-in app [:login :form :password])
           :login-url (get-in app [:config :login-url])
           :api-url (get-in app [:config :api-url])}))

  SetTokens
  (process-event [{tokens :tokens} app]
    (log/info "TOKENS: " tokens)
    (t/fx (assoc-in app [:login :tokens] tokens)
          {::tuck-effect/type :fetch-user-info
           :token (get tokens "id_token")
           :api-url (get-in app [:config :api-url])}))

  SetUserInfo
  (process-event [{user :user} app]
    (log/info "Authenticated user: " user)
    (t/fx (assoc app :user user)
          {::tuck-effect/type :navigate
           :page :projects})))
