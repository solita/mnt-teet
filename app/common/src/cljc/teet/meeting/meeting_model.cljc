(ns teet.meeting.meeting-model)

(defn meeting-title
  [{:meeting/keys [title number]}]
  (str title (when number
               (str " " "#" number))))
