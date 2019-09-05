(ns teet.drtest
  (:require [drtest.core :as drt]))

(defmacro define-drtest [& args]
  `(drt/define-drtest ~@args))
