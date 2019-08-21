(ns teet.navigation.navigation-style)

(def drawer-width {true 200   ; width when open
                   false 50}) ; width when closed

(defn drawer
  [open?]
  (let [w (drawer-width open?)]
    {:min-width (str w "px")
     :width (str w "px")}))

(defn appbar-position [drawer-open?]
  (let [dw (drawer-width drawer-open?)]
    {:z-index 10
     :width (str "calc(100% - " dw "px)")
     :margin-left (str dw "px")}))

(defn main-container [drawer-open?]
  (let [dw (drawer-width drawer-open?)]
    {:z-index 10
     :width (str "calc(100% - " dw "px)")
     :margin-left (str dw "px")
     :padding-left "2px"}))
