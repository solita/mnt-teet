(ns teet.document.document-spec
  "Specs for document data"
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(s/def :document/status keyword?)
(s/def :document/name (s/and string? (complement str/blank?)))
(s/def :document/description (s/and string? (complement str/blank?)))
(s/def :document/category keyword?)
(s/def :document/sub-category keyword?)
(s/def :document/new-document-form (s/keys :req [:document/category
                                                 :document/sub-category
                                                 :document/name
                                                 :document/status
                                                 :document/description
                                                 :document/status]))

(s/def :activity/name keyword?)
(s/def :activity/status keyword?)
(s/def :activity/estimated-end-date inst?)
(s/def :activity/estimated-start-date inst?)
(s/def :activity/estimated-date-range (s/coll-of inst? :count 2))
(s/def :document/new-activity-form (s/keys :req [:activity/name :activity/estimated-date-range :activity/status]))

(s/def :comment/comment (s/and string? (complement str/blank?)))
(s/def :document/new-comment-form (s/keys :req [:comment/comment]))

(s/def :document/document-id integer?)
(s/def :document/comment (s/keys :req-un [:comment/comment :document/document-id]))
