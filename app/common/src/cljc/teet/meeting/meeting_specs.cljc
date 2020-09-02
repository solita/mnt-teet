(ns teet.meeting.meeting-specs
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            teet.util.datomic))

(s/def :meeting/form-data
  (s/keys :req [:meeting/title
                :meeting/start
                :meeting/end
                :meeting/organizer]))

(s/def :meeting/agenda-form
  (s/keys :req [:meeting.agenda/topic]))

(s/def :meeting/create
  (s/keys :reg [:activity-eid
                :meeting/form-data]))
