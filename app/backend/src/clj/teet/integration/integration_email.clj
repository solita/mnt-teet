(ns teet.integration.integration-email
  "Email integration through AWS Simple Email Service"
  (:require [cognitect.aws.client.api :as aws]
            [clojure.string :as str])
  (:import (java.util Base64)))

(def ^:private email (delay (aws/client {:api :email})))

(def boundary-digits "0123456789abcdefghijklmnopqrstuvwxyz")

(defn- boundary []
  (str/join (repeatedly 32 #(rand-nth boundary-digits))))

(defn- ->b64 [text]
  (.encodeToString (Base64/getEncoder)
                   (.getBytes text "UTF-8")))

(defn- quote-header [text]
  (str "=?utf-8?B?" (->b64 text) "?="))

(defn- header-lines [headers]
  (str/join "\r\n"
            (for [[header value] headers]
              (str header ": " value))))

(defn- message-part [b {:keys [headers body]}]
  (str "\r\n--" b "\r\n"
       (header-lines (merge headers {"Content-Transfer-Encoding" "base64"}))
       "\r\n\r\n"
       (->b64 body)))

(defn raw-message [{:keys [from to subject parts]}]
  (assert (pos? (count parts))
          "Specify at least one message part in :parts")
  (let [message-headers {"MIME-version" "1.0"
                         "From" from
                         "Sender" from
                         "Subject" (quote-header subject)
                         "BCC" (str/join ","
                                         (if (string? to) [to] to))

                    }]
    (if (> (count parts) 1)
      ;; Multipart message, separate parts with boundary
      (let [b (boundary)]

        (str (header-lines (merge message-headers
                                  {"Content-Type" (str "multipart/mixed; boundary=\"" b "\"")}))
             "\r\n"
             (str/join (map (partial message-part b) parts))
             "\r\n--" b "--"))

      ;; Single part, add part headers to email headers
      (let [{:keys [headers body]} (first parts)]
        (str (header-lines (merge message-headers
                                  {"Content-Type" (headers "Content-Type")
                                   "Content-Transfer-Encoding" "base64"}))
             "\r\n\r\n"
             (->b64 body))))))

(defn send-email! [msg]
  (aws/invoke
   @email
   {:op :SendRawEmail
    :request {:RawMessage {:Data (.getBytes (raw-message msg) "UTF-8")}}}))
