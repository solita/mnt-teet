(ns teet.util.build-info
  (:require [clojure.java.io :as io]))

(defonce build-info (delay
                      (some-> "build-info.edn"
                              io/resource
                              slurp
                              read-string)))

(defmacro git-commit []
  (:git-commit @build-info))
