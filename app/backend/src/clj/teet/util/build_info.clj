(ns teet.util.build-info
  (:require [clojure.java.shell :as sh]
            [clojure.string :as str]))

(defmacro git-commit []
  (str/trim (:out (sh/sh "git" "rev-parse" "HEAD"))))
