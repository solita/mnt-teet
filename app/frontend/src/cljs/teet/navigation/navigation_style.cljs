(ns teet.navigation.navigation-style)

(defn drawer
  [open?]
  (let [w (if open?
            "200px"
            "80px")]
    {:min-width w
     :width w
     :flex-shrink 0
     :box-sizing "border-box"
     :padding "0.25rem"
     :white-space "nowrap"}))

(defn drawer-paper
  []
  {:background-color "red"})

(defn appbar-position [drawer-open?]
  ;; FIXME: can we do this without setting pixel positions?
  {:left (if drawer-open?
           "159px"
           "78px")})
