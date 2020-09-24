(ns teet.meeting.meeting-specs
  (:require [clojure.spec.alpha :as s]
            teet.user.user-spec
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

(s/def :meeting/decision-form
  (s/keys :req [:meeting.decision/body]))

(s/def :meeting/add-non-teet-user-form
  (s/keys :req [:user/given-name
                :user/family-name
                :user/email]))

(s/def :meeting/add-teet-user-form
  (s/keys :req [:participation/role
                :participation/participant]))

(s/def :meeting/add-participant-form
  (s/keys :req [(or (and :user/given-name
                         :user/family-name
                         :user/email)
                     (and
                      :participation/role
                      :participation/participant))]))

(s/def :meeting/review-form
  (s/keys :req [:review/decision]))
