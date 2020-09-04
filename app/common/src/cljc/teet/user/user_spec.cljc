(ns teet.user.user-spec
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(def estonian-person-id-pattern
  "Regular expression pattern for Estonian person id codes.
  Format GYYMMDDSSSC where:
  G = sex and century
  YYMMDD = date of birth
  SSS = serial number
  C = checksum

  See: https://en.wikipedia.org/wiki/National_identification_number#Estonia"
  #"^(\d)(\d{6})(\d{3})(.)$")

(defn estonian-person-id?
  "Check that given value is string and matches the format of
  Estonian person ID. Does not validate dates or checksum."
  [s]
  (and (string? s)
       (boolean (or (re-matches estonian-person-id-pattern s)
                    (and (str/starts-with? s "EE")
                         (re-matches estonian-person-id-pattern (subs s 2)))))))

(s/def :user/person-id estonian-person-id?)

(s/def :admin/create-user (s/keys :req [:user/person-id]))
(s/def :admin/list-users (s/keys))

;; User entity id
(s/def :user/eid (s/or :db-id integer?
                       :user-id ::user-id-ref
                       :person-id ::person-id-ref))

(s/def ::user-id-ref (s/and vector?
                            (s/cat :user-id-kw #(= :user/id %)
                                   :user-uuid uuid?)))

(s/def ::person-id-ref (s/and vector?
                              (s/cat :person-id-kw #(= :user/person-id %)
                                     :person-id estonian-person-id?)))

(defn non-empty-string? [s]
  (and (string? s) (not (str/blank? s))))

(defn email?
  "Naive email check"
  [s]
  (and (string? s)
       (boolean (re-matches #".+@.+" s))))

(s/def :user/given-name non-empty-string?)
(s/def :user/family-name non-empty-string?)
(s/def :user/email email?)
