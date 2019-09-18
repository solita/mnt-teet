(ns teet.theme.theme-spacing)

(def appbar-height "90px")

(def content-height (str "calc(100vh - " appbar-height ")"))

(defn fill-content []
  {:min-height content-height
   :max-height content-height})
