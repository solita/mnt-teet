(ns teet.comment.comment-spec
  "Specs for comment data"
  (:require [clojure.spec.alpha :as s]))

(s/def :comment/comment string?)
(s/def :comment/edit-comment-form (s/keys :req [:db/id
                                                :comment/comment]))

(s/def :comment/update (s/keys :req [:db/id
                                     :comment/comment]))
