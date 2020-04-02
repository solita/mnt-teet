(ns teet.task.task-spec
  "Specs for task data"
  (:require [clojure.spec.alpha :as s]
            [teet.file.file-model :as file-model]))

(defn- task-type-and-group-both-present-or-absent?
  "Check that both task type and group are present, or neither"
  [{:task/keys [type group]}]
  (= (boolean type)
     (boolean group)))

(s/def :task/type keyword?)
(s/def :db/ident keyword?)
(s/def :task/type (s/or :enum-keyword keyword?
                        :enum-ref-map (s/keys :req [:db/ident])))
(s/def :task/description string?)
(s/def :task/assignee (s/keys :req [:user/id]))
(s/def :task/send-to-thk? boolean?)

(s/def :task/new-task-form (s/keys :req [:task/group
                                         :task/type
                                         :task/description
                                         :task/assignee
                                         :task/estimated-start-date
                                         :task/estimated-end-date]))

(s/def :task/edit-task-form (s/keys))

(s/def :task/update (s/and (s/keys)
                           task-type-and-group-both-present-or-absent?))


(s/def :task/add-files (s/keys :req [:task/files]))

(s/def :task/file (s/and (s/keys :req [:file/type
                                       :file/name
                                       :file/size])
                         #(nil? (file-model/validate-file (file-model/type-by-suffix %)))))

(s/def :task/new-comment-form (s/keys :req [:comment/comment]))
