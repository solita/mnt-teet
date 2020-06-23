(ns teet.notification.notification-sender
  "Send notifications via email using Amazon Simple Email Service"
  (:require [cognitect.aws.client.api :as aws]
            [teet.environment :as environment]
            [datomic.client.api :as d]
            [teet.notification.notification-db :as notification-db]
            [teet.notification.notification-queries :as notification-queries]
            [teet.log :as log]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [teet.localization :refer [with-language tr-enum]]))

(def ^:private email (delay (aws/client {:api :email})))

(defn- send-email* [from to subject body]
  (aws/invoke
   @email
   {:op :SendEmail
    :request {:Message {:Subject {:Data subject :Charset "UTF-8"}
                        :Body {:Text {:Data body :Charset "UTF-8"}}}
              :Destination {:ToAddresses (if (vector? to)
                                           to
                                           [to])}
              :Source from }}))

(defn send-email [from to subject body]
  (try
    (send-email* from to subject body)
    (catch Exception e
      (log/warn e "Exception while sending email to" to))))

(defmacro route-data []
  (into {}
        (map (fn [[route {path :path}]]
               [route path]))
        (read-string (slurp (io/file "../frontend/resources/routes.edn")))))

(def routes (route-data))

(defn- navigation-info->url [base-url {:keys [page params]}]
  (let [path (routes page)]
    (str base-url "#" (str/replace path #":[^/]+"
                                   (fn [m]
                                     (params (keyword (subs m 1))))))))

(defn prepare-email [db base-url notification]
  ;; FIXME: we need email template/message based on notification type
  (let [nav-info (notification-queries/notification-navigation-info
                  db
                  (-> notification :notification/receiver :db/id)
                  (:db/id notification))]
    (with-language :et
      {:subject (str "TEET: " (tr-enum (:notification/type notification)))
       :body (str "Please see it here: " (navigation-info->url base-url nav-info))})))

(defn send-notifications-ion [_event]
  (let [conn (environment/datomic-connection)
        db (d/db conn)
        notifications (notification-db/notifications-to-send db)
        now (java.util.Date.)
        from (environment/ssm-param :email :from)
        base-url (environment/config-value :base-url)]

    (if (empty? notifications)
      (log/info "No pending notifications to send.")
      (do
        (log/info "Sending " (count notifications) " notification emails.")
        ;; Mark notification send time immediately
        (d/transact conn {:tx-data (vec (for [{id :db/id} notifications]
                                          {:db/id id
                                           :notification/email-sent-at now}))})

        ;; Send emails
        (doseq [notification notifications
                :let [to (get-in notification [:notification/receiver :user/email])
                      {:keys [subject body]} (prepare-email db base-url notification)]
                :when to]
          (log/info "sending to: " to ", subject: " subject ", body: " body)
          (send-email from to subject body))))
    "{\"success\": true}"))
