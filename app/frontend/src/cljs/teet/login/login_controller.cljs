(ns teet.login.login-controller
  (:require [tuck.core :as t]
            [tuck.effect :as tuck-effect]
            [taoensso.timbre :as log]
            [teet.login.login-paths :as login-paths]))

(defrecord UpdateLoginForm [form])
(defrecord Login [user])
(defrecord SetTokens [tokens])
(defrecord SetUserInfo [user])

(defmethod tuck-effect/process-effect :login [e! {:keys [username password login-url]}]
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

  Login
  (process-event [{user :user} app]
    (log/info "->Login event: user:" (pr-str user))
    (t/fx (assoc-in app [:login :progress?] true)
          {::tuck-effect/type :query
           :query :user-session
           :args {:user user}
           :result-event (fn session-handler [result]
                           (log/info "RESULT: " result)
                           (if (contains? result "ok")
                             (->SetUserInfo user)
                             (->SetUserInfo nil)))}))

  SetTokens
  (process-event [{tokens :tokens} app]
    (log/info "TOKENS: " tokens)
    (t/fx (assoc-in app [:login :tokens] tokens)
          {::tuck-effect/type :fetch-user-info
           :token (get tokens "id_token")
           :api-url (get-in app [:config :api-url])}))

  SetToken
  (process-event [{token :token} app]
    (log/info "TOKEN: " token)
    (t/fx (assoc-in app login-paths/api-token token)
          {::tuck-effect/type :set-api-token
           :token token}
          {::tuck-effect/type :navigate
           :page :projects})))

(def mock-users [{:user/id #uuid "4c8ec140-4bd8-403b-866f-d2d5db9bdf74"
                  :user/person-id "1234567890"
                  :user/given-name "Danny"
                  :user/family-name "Design-Manager"
                  :user/email "danny.design-manager@example.com"
                  :user/organization "Maanteeamet"}

                 {:user/id #uuid "ccbedb7b-ab30-405c-b389-292cdfe85271"
                  :user/person-id "3344556677"
                  :user/given-name "Carla"
                  :user/family-name "Consultant"
                  :user/email "carla.consultant@example.com"
                  :user/organization "ACME Road Consulting, Ltd."}

                 {:user/id #uuid "fa8af5b7-df45-41ba-93d0-603c543c880d"
                  :user/person-id "9483726473"
                  :user/given-name "Benjamin"
                  :user/family-name "Boss"
                  :user/email "benjamin.boss@example.com"
                  :user/organization "Maanteeamet"}])
