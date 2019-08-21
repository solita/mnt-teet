(ns teet.navigation.navigation-style)

(defn drawer
  [open?]
  {:width (if open?
            "200px"
            "80px")
   :flex-shrink 0
   :box-sizing "border-box"
   :padding "0.25rem"
   :white-space "nowrap"})

(defn drawer-paper
  []
  {:background-color "red"})
