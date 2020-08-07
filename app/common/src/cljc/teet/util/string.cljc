(ns teet.util.string
  "String utilities"
  (:require [clojure.string :as str]))

(defn- interpolate-get [parameters param-name]
  (str
   (get parameters (keyword param-name)
        (str "[MISSING PARAMETER " param-name "]"))))

(defn interpolate
  "Replace parameter references in curly braces in the input string with values from parameters.
  Example: \"Hello, {name}!\" would replace \"{name}\" with the :name parameter."
  ([string parameters] (interpolate string parameters {}))
  ([string parameters special-fns]
   (str/replace string
                #"\{([^\}]+)\}"
                (fn [[_ param-name]]
                  (if (str/includes? param-name ":")
                    (let [[fn-name & args] (str/split param-name #":")]
                      (if-let [special-fn (get special-fns (keyword fn-name))]
                        (apply special-fn args)
                        (interpolate-get parameters param-name)))
                    (interpolate-get parameters param-name))))))
