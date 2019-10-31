(ns teet.user.user-spec
  (:require [clojure.spec.alpha :as s]))

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
       (re-matches estonian-person-id-pattern s)))

(s/def :user/person-id estonian-person-id?)

(s/def :admin/create-user (s/keys :req [:user/person-id]))
(s/def :admin/list-users (s/keys))
