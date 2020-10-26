(ns teet.ui.icons
  (:require [clojure.string :as str]))

(defmacro define-font-icon [prefix name icon-component]
  (let [fn-base-name (str prefix "-" (str/replace name "_" "-"))
        fn-name (symbol fn-base-name)
        fn-name-outlined (symbol (str fn-base-name "-outlined"))]
    `(do (defn ~fn-name
           ([] (~fn-name {}))
           ([opts#]
            [~icon-component opts# ~name]))
         (defn ~fn-name-outlined
           ([] (~fn-name-outlined {}))
           ([opts#]
            [~icon-component (merge opts#
                                    {:class "material-icons-outlined"})
             ~name])))))

(defmacro define-font-icons [{:keys [prefix component]} & names]
  `(do
     ~@(for [name names]
         `(define-font-icon ~prefix ~name ~component))))
