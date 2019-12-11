(ns teet.util.string
  "String utilities"
  (:require [clojure.string :as str]))

(defn interpolate
  "Replace parameter references in curly braces in the input string with values from parameters.
  Example: \"Hello, {name}!\" would replace \"{name}\" with the :name parameter."
  [string parameters]
  (str/replace string
               #"\{([^\}]+)\}"
               (fn [[_ param-name]]
                 (str
                  (get parameters (keyword param-name)
                       (str "[MISSING PARAMETER " param-name "]"))))))
