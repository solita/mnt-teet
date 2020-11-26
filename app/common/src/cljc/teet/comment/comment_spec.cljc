(ns teet.comment.comment-spec
  "Specs for comment data"
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            teet.util.datomic))

(s/def :comment/comment (s/and string? (complement str/blank?)))
(s/def :comment/visibility :teet.util.datomic/enum)
(s/def :comment/edit-comment-form (s/keys :req [:db/id
                                                :comment/comment
                                                :comment/visibility
                                                :comment/files]))

(s/def :comment/update (s/keys :req [:db/id
                                     :comment/comment
                                     :comment/visibility]))

(s/def :comment/set-status (s/keys :req [:db/id
                                         :comment/status]))
