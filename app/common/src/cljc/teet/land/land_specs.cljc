(ns teet.land.land-specs
  (:require [clojure.spec.alpha :as s]))

(s/def :land-acquisition/form
  (s/keys :req [:land-acquisition/impact]))
