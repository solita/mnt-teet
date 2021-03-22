(ns teet.land.owner-opinion-specs
  (:require [clojure.spec.alpha :as s]
            teet.user.user-spec
            teet.util.datomic))

(s/def :land-owner-opinion/form
  (s/keys :req [:land-owner-opinion/activity
                :land-owner-opinion/type
                :land-owner-opinion/respondent-name
                :land-owner-opinion/body]))

(s/def :land-owner-opinion/export
  (s/keys :req [:land-owner-opinion/activity
                :land-owner-opinion/type]))
