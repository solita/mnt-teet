(ns teet.integration.integration-email
  "Email integration through AWS Simple Email Service"
  (:require [cognitect.aws.client.api :as aws]
            [clojure.string :as str]
            [teet.environment :as environment]
            postal.core
            [teet.localization :refer [with-language tr]])
  (:import (java.util Base64)))

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
                                         (if (string? to) [to] to))}]
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

(defn send-email-smtp!*
  "Send msg email using smtp config"
  [config msg]
  (postal.core/send-message config msg))

(defn- with-subject-prefix
  "Add prefix to subject (if configured)"
  [{prefix :subject-prefix} msg]
  (if prefix
    (update msg :subject #(str prefix " " %))
    msg))

(defn- get-msg-type [msg]
   (:type (first (:body msg))))

(defn- with-data-protection-footer
  "Add data protection clause to text parts"
  [{addr :contact-address} msg]
  (update
   msg :body
   (fn [parts]
     (mapv
      (fn [part]
        (let [type (get-msg-type msg)]
          (if (and type (str/starts-with? type "text/plain"))
            (let [footer #(tr [:email :footer] {:contact-mailto addr})]
              (update part :content #(str % "\n"
                                       (with-language :et (footer))
                                       (with-language :en (footer)))))
            part)))
      parts))))

(defn send-email! [msg]
  (let [config (environment/config-map
                {:subject-prefix [:email :subject-prefix]
                 :contact-address [:email :contact-address]})
        smtp-node-config (environment/config-value :email :server)]
    (send-email-smtp!* smtp-node-config
      (->> msg
          (with-subject-prefix config)
          (with-data-protection-footer config)))))
