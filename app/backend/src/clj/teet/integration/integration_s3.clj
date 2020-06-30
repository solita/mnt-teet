(ns teet.integration.integration-s3
  "Integration steps for S3"
  (:require [clojure.spec.alpha :as s]
            [teet.integration.integration-context :refer [defstep]]
            [cheshire.core :as cheshire]
            [cognitect.aws.client.api :as aws]
            [clojure.string :as str]))

(def ^:private s3-client (delay (aws/client {:api :s3})))

(defn- invoke [op req]
  (let [response (aws/invoke @s3-client
                             {:op op
                              :request req})]
    (if (:cognitect.anomalies/category response)
      (throw (ex-info (str "Error in " op " invocation.")
                      {:op op
                       :request req
                       :response response}))
      response)))

(defn- bucket-and-key [s3-data]
  {:bucket   (get-in s3-data [:bucket :name])
   :file-key (get-in s3-data [:object :key])})

(defn- s3-file-data [input]
  (-> input :Records first :s3 bucket-and-key))

(defn- valid-file-descriptor? [{:keys [bucket file-key]}]
  (and (string? bucket)
       (string? file-key)))

(s/def ::file-descriptor valid-file-descriptor?)
(s/def ::file-contents #(instance? java.io.InputStream %))

(s/def ::s3-trigger-event (s/keys :req-un [:s3-trigger-event/input]))
(s/def :s3-trigger-event/input string?)

(defstep read-trigger-event
  {:doc "Read file information from an S3 lambda trigger event"
   :in {lambda-event {:spec ::s3-trigger-event
                      :path-kw :event
                      :default-path [:event]}}
   :out {:spec ::file-descriptor
         :default-path [:s3]}}
  (-> lambda-event :input
      (cheshire/decode keyword)
      s3-file-data))

(defn get-object
  "Read S3 object. Returns input stream."
  [bucket file-key]
  {:pre [(string? bucket)
         (string? file-key)]
   :post [(instance? java.io.InputStream %)]}
  (:Body
   (invoke :GetObject {:Bucket bucket
                       :Key file-key})))

(defn put-object
  "Write S3 object. Returns response map."
  [bucket file-key body]
  {:pre [(string? bucket)
         (string? file-key)
         (instance? java.io.InputStream body)]}
  (invoke :PutObject {:Bucket bucket
                      :Key file-key
                      :Body body}))

(defstep load-file-from-s3
  {:ctx ctx
   :doc "Load file from S3. Result is an input stream."
   :in {fd {:spec ::file-descriptor
            :path-kw :s3
            :default-path [:s3]}}
   :out {:spec ::file-contents
         :default-path [:file]}}
  (let [{:keys [bucket file-key]} fd]
    (get-object bucket file-key)))

(defstep write-file-to-s3
  {:ctx ctx
   :doc "Write file to S3."
   :in {fd {:spec ::file-descriptor
            :path-kw :to
            :default-path [:to]}
        contents {:spec ::file-contents
                  :path-kw :contents
                  :default-path [:contents]}}
   :out {:spec some?
         :default-path [:write-result]}}
  (put-object (:bucket fd)
              (:file-key fd)
              contents))

(defn copy-object
  "Copy object within the same bucket."
  [bucket from-key to-key]
  {:pre [(string? bucket)
         (string? from-key)
         (string? to-key)]}
  (invoke :CopyObject
          {:Bucket bucket
           :CopySource (str "/" bucket "/" from-key)
           :Key to-key}))

(defn delete-object
  "Delete obejct in a bucket"
  [bucket key]
  {:pre [(string? bucket)
         (string? key)]}
  (invoke :DeleteObject {:Bucket bucket :Key key}))

;; Generate presigned URL:
;; - generate session token with STS
;;   - :GetSessionToken
;; - use session token access key to presign a URL

(def ^:private sts-client (delay (aws/client {:api :sts})))

(let [credentials (atom nil)]
  (defn- temp-credentials []
    (swap! credentials
           (fn [{exp :Expiration :as creds}]
             ;; If no expiration or less than 30 minutes until expiration,
             ;; request a new token
             (if (or (nil? exp)
                     (< (- (.getTime exp) (System/currentTimeMillis))
                        (* 1000 60 30)))
               ;; Get new token for 12h
               (:Credentials (aws/invoke @sts-client {:op :GetSessionToken
                                                      :request {:DurationSeconds (* 60 60 12)}}))

               ;; Use the existing credentials
               creds)))))

(def bucket-location
  (memoize (fn [bucket]
             (:LocationConstraint (invoke :GetBucketLocation {:Bucket bucket})))))

(defn- url-encode [x]
  (-> x
      (java.net.URLEncoder/encode "UTF-8")
      (.replace "+" "%20")))

(defn- s3-url [bucket key]
  (str "https://" bucket ".s3." (bucket-location bucket) ".amazonaws.com/"
       (url-encode key)))

(defn- date [d]
  (.format (java.text.SimpleDateFormat. "yyyyMMdd") d))

(let [fmt (doto (java.text.SimpleDateFormat. "yyyyMMdd'T'HHmmss'Z'")
            (.setTimeZone (java.util.TimeZone/getTimeZone "UTC")))]
  (defn- timestamp [d]
    (.format fmt d)))

(defn- hex [bytes]
  (str/join ""
            (map #(format "%02x" %) bytes)))

(defn- ->b [string]
  (.getBytes string "UTF-8"#_"US-ASCII"))

(defn- hmac-sha256 [key-bytes content-bytes]
  (let [secret-key (javax.crypto.spec.SecretKeySpec. key-bytes "HmacSHA256")
        mac (doto (javax.crypto.Mac/getInstance "HmacSHA256")
              (.init secret-key))]
    (.doFinal mac content-bytes)))

(defn- sha256 [bytes]
  (let [d (java.security.MessageDigest/getInstance "SHA-256")]
    (.digest d bytes)))

;; https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-query-string-auth.html#query-string-auth-v4-signing-example
(defn presigned-url
  ([method bucket key]
   (presigned-url {} method bucket key))
  ([{:keys [content-disposition expiration-seconds]
     :or {expiration-seconds 300}}
    method bucket key]
   {:pre [(or (= method "GET") (= method "PUT"))
          (string? bucket)
          (string? key)]}
   (let [now (java.util.Date.)
         url (s3-url bucket key)
         {:keys [AccessKeyId SecretAccessKey SessionToken]} (temp-credentials)
         region (bucket-location bucket)

         scope (str (date now) "/" region "/s3/aws4_request")
         x-amz-credential (str AccessKeyId "/" scope)

         query-string (str
                       "X-Amz-Algorithm=AWS4-HMAC-SHA256"
                       "&X-Amz-Credential=" (url-encode x-amz-credential)
                       "&X-Amz-Date=" (timestamp now)
                       "&X-Amz-Expires=" expiration-seconds
                       "&X-Amz-Security-Token=" (url-encode SessionToken)
                       "&X-Amz-SignedHeaders=host"
                       (when content-disposition
                         (str "&response-content-disposition="
                              (url-encode content-disposition))))

         ;; Canonical request for signature
         canonical-request (str method "\n"
                                "/" (url-encode key) "\n"
                                query-string "\n"
                                ;; canonical headers
                                "host:" bucket ".s3." region ".amazonaws.com\n" ; host signed header
                                "\n"
                                "host\n" ; signed headers
                                "UNSIGNED-PAYLOAD") ; hardcoded

         string-to-sign (str "AWS4-HMAC-SHA256\n"
                             (timestamp now) "\n"
                             scope "\n"
                             (hex (sha256 (->b canonical-request))))

         date-key (hmac-sha256 (->b (str "AWS4" SecretAccessKey)) (->b (date now)))
         date-region-key (hmac-sha256 date-key (->b region))
         date-region-service-key (hmac-sha256 date-region-key (->b "s3"))
         sign-key (hmac-sha256 date-region-service-key (->b "aws4_request"))

         signature (hex (hmac-sha256 sign-key (.getBytes string-to-sign "US-ASCII")))]

     (str url "?" query-string "&X-Amz-Signature=" signature))))
