(ns teet.integration.integration-id
  "Utilities for working with :integration/id UUID numbers."
  (:require [datomic.client.api :as d]))

(defn uuid->number [uuid]
  (let [bb (java.nio.ByteBuffer/wrap (byte-array 16))]
    (.putLong bb (.getMostSignificantBits uuid))
    (.putLong bb (.getLeastSignificantBits uuid))
    (BigInteger. 1 (.array bb))))

(defn number->uuid [n]
  (if (= n (.longValue n))
    ;; Fits into long type, this is old :db/id value
    (java.util.UUID. 0 n)

    ;; Otherwise new full UUID
    (let [lsb (.longValue n)
          msb (.longValue (.shiftRight n 64))]
      (java.util.UUID. msb lsb))))

(defonce random (java.security.SecureRandom.))


(defn random-small-uuid
  "Create a UUID that only has 64bits number. This is because some
  systems (like THK) have id numbers that are only int8 sized and cannot
  fit a full UUID."
  []
  (let [n (.nextLong random)]
    (if (pos? n)
      (number->uuid n)
      (recur))))

(defn- used-integration-id? [db id]
  (boolean
   (seq
    (d/q '[:find ?e :where [?e :integration/id ?v] :in $ ?v] db id))))

(defn unused-random-small-uuid [db]
  (first
   (drop-while (partial used-integration-id? db)
               (repeatedly random-small-uuid))))
