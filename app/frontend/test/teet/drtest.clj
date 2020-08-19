(ns teet.drtest
  (:require [drtest.core :as drt]))

(defmacro define-drtest [name options & steps]
  `(drt/define-drtest
     ~name
     ~(merge {:screenshots? `(deref teet.drtest/take-screenshots?)}
             options)
     {:drtest.step/label "Test init"
      :drtest.step/type :init-tests}
     ~@steps))
