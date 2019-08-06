(ns teet.ui.info
  "Info display component."
  (:require [teet.ui.grid :as grid]
            [teet.localization :as localization]))

(defn info-section [{:keys [data table]} keys]
  [grid/grid {:columns ["1fr" "2fr"]}
   (reduce concat
           (map-indexed
            (fn [i key]
              (let [[label value] (if (fn? key)
                                    ;; This is a function to access the value, expect it to return label and value
                                    (key data)

                                    ;; String key, translate key to label and get value
                                    ;; FIXME: translate
                                    [(localization/label-for-field table key) (get data key)])]
                [^{:key i}
                 [grid/cell {} label]
                 ^{:key (str i "-value")}
                 [grid/cell {} value]]))
            keys))])

(defn info [opts & sections-and-keys]
  [grid/grid {:columns ["1fr" "2fr"]}
   (mapcat (fn [{:keys [title keys]}]
             [^{:key title}
              [grid/cell {} title]
              ^{:key (str title "-keys")}
              [grid/cell {}
               [info-section opts keys]]])
           sections-and-keys)])
