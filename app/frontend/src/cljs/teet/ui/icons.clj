(ns teet.ui.icons
  (:require [clojure.string :as str]))

(defmacro define-font-icon [prefix name icon-component]
  (let [fn-name (symbol (str prefix "-" (str/replace name "_" "-")))]
    `(defn ~fn-name
       ([] (~fn-name {}))
       ([opts#]
        [~icon-component opts# ~name]))))

(defmacro define-font-icons [{:keys [prefix component]} & names]
  `(do
     ~@(for [name names]
         `(define-font-icon ~prefix ~name ~component))))
