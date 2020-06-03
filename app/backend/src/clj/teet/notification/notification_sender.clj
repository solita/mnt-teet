(ns teet.notification.notification-sender
  "Send notifications via email using Amazon Simple Email Service"
  (:require [cognitect.aws.client.api :as aws]
            [teet.environment :as environment]
            [datomic.client.api :as d]
            [teet.notification.notification-db :as notification-db]
            [teet.notification.notification-queries :as notification-queries]
            [teet.log :as log]))

(def ^:private email (delay (aws/client {:api :email})))

(defn send-email [from to subject body]
  (aws/invoke
   @email
   {:op :SendEmail
    :request {:Message {:Subject {:Data subject :Charset "UTF-8"}
                        :Body {:Text {:Data body :Charset "UTF-8"}}}
              :Destination {:ToAddresses (if (vector? to)
                                           to
                                           [to])}
              :Source from }}))

(defn prepare-email [db notification]
  ;; FIXME: we need email template/message based on notification type
  (let [nav-info (notification-queries/notification-navigation-info
                  db
                  (-> notification :notification/receiver :db/id)
                  (:db/id notification))]
    {:hey-you-received (:notification/type notification)
     :with-nav-info nav-info
     :fixme "implement the actual message"}))

(defn send-notifications-ion [_event]
  (let [conn (environment/datomic-connection)
        db (d/db conn)
        notifications (notification-db/notifications-to-send db)
        now (java.util.Date.)
        from (environment/ssm-param :email :from)]

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
                      email (prepare-email db notification)]
                :when to]
          (log/info "sending to: " to ", email: " email))))
    :ok))
