(ns teet.util.hash
  (:import (java.security MessageDigest))
  (:require [clojure.string :as str]))

(defn- ->bytes [x]
  (cond
    (bytes? x) x
    (string? x) (.getBytes x "UTF-8")
    :else (throw (ex-info "Don't know how to turn into bytes"
                          {:unknown-value x}))))

(defn sha256
  "Create SHA256 hash of byte array or string.
  Returns hash as a byte array."
  [x]
  (let [d (MessageDigest/getInstance "SHA-256")]
    (.digest d (->bytes x))))

(defn hex
  "Format byte array as a hex string"
  [bytes]
  (str/join ""
            (map #(format "%02x" %) bytes)))
