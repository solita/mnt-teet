(ns teet.navigation.navigation-style)

(def drawer-width {true 200   ; width when open
                   false 80}) ; width when closed

(defn drawer
  [open?]
  (let [w (drawer-width open?)]
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
  (let [dw (drawer-width drawer-open?)]
    {:z-index 10
     :width (str "calc(100% - " dw "px)")
     :margin-left (str dw "px")}))
