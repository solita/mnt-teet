(ns teet.document.document-spec
  "Specs for document data"
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(s/def :document/status keyword?)
(s/def :document/name (s/and string? (complement str/blank?)))
(s/def :document/description (s/and string? (complement str/blank?)))
(s/def :document/new-document-form (s/keys :req [:document/name :document/status :document/description
                                                 :document/status]))

(s/def :phase/phase-name keyword?)
(s/def :phase/status keyword?)
(s/def :phase/estimated-end-date inst?)
(s/def :phase/estimated-start-date inst?)
(s/def :document/new-phase-form (s/keys :req [:phase/phase-name :phase/estimated-start-date :phase/estimated-end-date]))

(s/def :comment/comment (s/and string? (complement str/blank?)))
(s/def :document/new-comment-form (s/keys :req [:comment/comment]))

(s/def :document/document-id integer?)
(s/def :document/comment (s/keys :req-un [:comment/comment :document/document-id]))
