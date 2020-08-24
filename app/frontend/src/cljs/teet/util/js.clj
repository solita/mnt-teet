(ns teet.util.js
  "JS interop utils"
  (:require [clojure.walk :as walk]
            [clojure.string :as str]))

(defmacro js>
  "Protect JS method invocations from advanced compilation mangling without externs.

  Turns forms like

  (.someMethodCall obj arg1 arg2)

  to

  (.call (aget obj \"someMethodCall\") obj arg1 arg2)
  "
  [form]
  (walk/prewalk (fn [form]
                  (if (and (list? form)
                           (symbol? (first form))
                           (>= (count form) 2)
                           (str/starts-with? (name (first form)) "."))
                    (if (str/starts-with? (name (first form)) ".-")
                      ;; This is a translatable js field get
                      `(aget ~(second form) ~(subs (name (first form)) 2))

                      ;; This is a translatable js method invocation
                      (let [[method obj & args] form
                            obj-sym (gensym "obj")]
                        `(let [~obj-sym ~obj]
                           (.call (aget ~obj-sym ~(subs (name method) 1))
                                  ~obj-sym
                                  ~@args))))

                    ;; Something else (pass through as is)
                    form))
                form))
