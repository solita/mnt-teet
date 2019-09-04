(ns teet.document.document-specs
  (:require [clojure.spec.alpha :as s]))

(s/def :document/name string?)
(s/def :document/type string?)
(s/def :document/size integer?)

(s/def ::task-id integer?)

(s/def :document/upload (s/keys :req [:document/name :document/type :document/size :thk/id]
                                :opt-un [::task-id]))
