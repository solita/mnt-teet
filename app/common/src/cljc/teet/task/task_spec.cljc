(ns teet.task.task-spec
  "Specs for task data"
  (:require [clojure.spec.alpha :as s]
            [teet.file.file-model :as file-model]))

(s/def :task/type keyword?)
(s/def :task/description string?)
(s/def :task/assignee (s/keys :req [:user/id]))

(s/def :task/new-task-form (s/keys :req [:task/group
                                         :task/type
                                         :task/description
                                         :task/assignee
                                         :task/estimated-start-date
                                         :task/estimated-end-date]))

(s/def :task/add-files (s/keys :req [:task/files]))

(s/def :task/file (s/and (s/keys :req [:file/type
                                       :file/name
                                       :file/size])
                         #(nil? (file-model/validate-file (file-model/type-by-suffix %)))))
