(ns teet.notification.notification-sender
  "Send notifications via email using Amazon Simple Email Service"
  (:require [cognitect.aws.client.api :as aws]
            [teet.environment :as environment]))

(def ^:private email (delay (aws/client {:api :email})))

(defn send-email [to subject body]
  (let [from (environment/config-value :email :from)]
    (aws/invoke
     @email
     {:op :SendEmail
      :request {:Message {:Subject {:Data subject :Charset "UTF-8"}
                          :Body {:Text {:Data body :Charset "UTF-8"}}}
                :Destination {:ToAddresses (if (vector? to)
                                             to
                                             [to])}
                :Source from }})))
