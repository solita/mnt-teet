(ns teet.meeting.meeting-tx
  "Meeting related transaction functions"
  (:require [datomic.ion :as ion]
            [teet.meeting.meeting-db :as meeting-db]
            [clojure.string :as str]
            [teet.meta.meta-model :as meta-model]
            [teet.user.user-model :as user-model]))

(defn update-meeting
  [db user meeting-id tx-vec]
  (if (meeting-db/locked? db meeting-id)
    (ion/cancel {:cognitect.anomalies/category :cognitect.anomalies/conflict
                 :cognitect.anomalies/message "The meeting is already approved"
                 :teet/error :meeting-is-locked})
    (into tx-vec
          (concat
            [(merge
               {:db/id meeting-id}
               (meta-model/modification-meta user))]
            (meeting-db/review-retractions db meeting-id)))))

(defn add-participation [db user
                         {meeting :participation/in
                          participant :participation/participant
                          :as participation}]
  (let [teet-user? (not (string? (:db/id participant)))]
    (if (and teet-user?
             (meeting-db/user-is-participating? db participant meeting))
      (ion/cancel {:cognitect.anomalies/category :cognitect.anomalies/conflict
                   :cognitect.anomalies/message "User is already participating"
                   :teet/error :user-is-already-participant})
      ;; All constraint checks ok, return tx data
      (update-meeting db user meeting [participation]))))

(defn review-meeting
  [db user meeting-id review]
  (cond
    (meeting-db/locked? db meeting-id)
    (ion/cancel {:cognitect.anomalies/category :cognitect.anomalies/conflict
                 :cognitect.anomalies/message "The meeting is already approved"
                 :teet/error :meeting-is-locked})
    (not (meeting-db/has-decisions? db meeting-id))
    (ion/cancel {:cognitect.anomalies/category :cognitect.anomalies/conflict
                 :cognitect.anomalies/message "Meeting can't be reviewed because it has no decisions"
                 :teet/error :meeting-has-no-decisions})
    :else
    (let [review (merge {:db/id "new-review"
                         :review/reviewer (user-model/user-ref user)
                         :review/of meeting-id}
                        review
                        (meta-model/creation-meta user))]
      (into [review]
            (into
              (meeting-db/users-review-retractions db (user-model/user-ref user) meeting-id)
              (when (meeting-db/will-review-lock-meeting? db review)
                [{:db/id meeting-id
                  :meeting/locked? true}]))))))

(defn create-meeting
  "Create new meeting in activity. Automatically sets meeting number."
  [db activity-eid meeting]
  (let [meeting
        (merge meeting
               {:db/id "new-meeting"}
               {:meeting/number (meeting-db/next-meeting-number
                                 db
                                 activity-eid
                                 (:meeting/title meeting))})]
    [{:db/id activity-eid
      :activity/meetings [meeting]}
     {:participation/participant (or (get-in meeting [:meeting/organizer :db/id])
                                     (:meeting/organizer meeting))
      :participation/role :participation.role/organizer
      :participation/in "new-meeting"}]))
