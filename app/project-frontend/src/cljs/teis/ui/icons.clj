(ns teis.ui.icons
  (:require [clojure.string :as str]))

(defmacro define-font-icon [prefix name]
  (let [fn-name (symbol (str prefix "-" (str/replace name "_" "-")))]
    `(defn ~fn-name
       ([] (~fn-name {}))
       ([style#] (~fn-name style# {}))
       ([style# opts#]
        [:div (merge
               {:style (merge {:display "inline-block"
                               :font "normal normal 20px/20px Material Icons"
                               :text-transform "lowercase"}
                              style#)}
               opts#)
         ~name]))))

(defmacro define-font-icons [{prefix :prefix :as opts} & names]
  `(do
     ~@(for [name names]
         `(define-font-icon ~prefix ~name))))
