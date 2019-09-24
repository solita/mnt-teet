(ns teet.document.document-spec
  "Specs for document data"
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(s/def :document/status keyword?)
(s/def :document/description (s/and string? (complement str/blank?)))
(s/def :document/new-document-form (s/keys :req [:document/status :document/description]))
