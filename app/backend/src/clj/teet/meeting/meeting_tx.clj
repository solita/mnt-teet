(ns teet.meeting.meeting-tx
  "Meeting related transaction functions"
  (:require [datomic.ion :as ion]
            [teet.meeting.meeting-db :as meeting-db]
            [clojure.string :as str]))

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
      [participation])))
