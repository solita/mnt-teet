(ns teis.ui.info
  "Info display component."
  (:require [teis.ui.grid :as grid]))

(defn info-section [{:keys [data]} keys]
  [grid/grid {:columns ["1fr" "2fr"]}
   (mapcat (fn [key]
             [^{:key key}
              [grid/cell {} key]
              ^{:key (str key "-value")}
              [grid/cell {} (get data key)]])
           keys)])

(defn info [opts & sections-and-keys]
  [grid/grid {:columns ["1fr" "2fr"]}
   (mapcat (fn [{:keys [title keys]}]
             [^{:key title}
              [grid/cell {} title]
              ^{:key (str title "-keys")}
              [grid/cell {}
               [info-section opts keys]]])
           sections-and-keys)])
