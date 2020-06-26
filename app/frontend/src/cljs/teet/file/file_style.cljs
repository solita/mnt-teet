(ns teet.file.file-style)

(defn filter-sorter
  []
  {:display :flex
   :flex-direction :row
   :align-items :center
   :justify-content :space-between
   :padding "2px"
   :margin-bottom "0.25rem"})

(defn file-row-name [seen?]
  (if seen?
    {:font-weight :normal}
    {:font-weight :bold}))
