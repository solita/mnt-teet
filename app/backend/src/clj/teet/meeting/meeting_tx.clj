(ns teet.meeting.meeting-tx
  "Meeting related transaction functions"
  (:require [datomic.ion :as ion]
            [teet.meeting.meeting-db :as meeting-db]
            [clojure.string :as str]
            [teet.meta.meta-model :as meta-model]
            [teet.user.user-model :as user-model]))

(defn update-meeting
  [db meeting-id tx-vec]
  (if (meeting-db/locked? db meeting-id)
    (ion/cancel {:cognitect.anomalies/category :cognitect.anomalies/conflict
                 :cognitect.anomalies/message "The meeting is already approved"
                 :teet/error :meeting-is-locked})
    (into tx-vec
          (meeting-db/review-retractions db meeting-id))))

(defn add-participation [db {meeting :participation/in
                             participant :participation/participant
                             :as participation}]
  (let [teet-user? (not (string? (:db/id participant)))]
    (if (or
         ;; If user is TEET user, check that they are not already participating
         (and teet-user?
              (meeting-db/user-is-participating? db participant meeting))

         ;; Check that email is not in use by some participant
         (let [emails (into #{}
                            (comp
                             (map :user/email)
                             (remove nil?)
                             (map str/lower-case))
                            (meeting-db/participants db meeting))
               new-email (:user/email participant)]
           ;; If some participant already has the email being added
           (and new-email (emails (str/lower-case new-email)))))
      (ion/cancel {:cognitect.anomalies/category :cognitect.anomalies/conflict
                   :cognitect.anomalies/message "User is already participating"
                   :teet/error :user-is-already-participant})
      ;; All constraint checks ok, return tx data
      (update-meeting db meeting [participation]))))

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
               {:meeting/number (meeting-db/next-meeting-number
                                 db
                                 activity-eid
                                 (:meeting/title meeting))})]
    [{:db/id activity-eid
      :activity/meetings [meeting]}]))
