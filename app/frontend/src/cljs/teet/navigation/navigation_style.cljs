(ns teet.navigation.navigation-style)

(def drawer-width {true 200   ; width when open
                   false 80}) ; width when closed

(defn drawer
  [open?]
  (let [w (drawer-width open?)]
    {:min-width (str w "px")
     :width (str w "px")}))

(defn appbar
  []
  {:display :flex
   :justify-content :space-between
   :min-height "90px"})

(defn appbar-position [drawer-open?]
  (let [dw (drawer-width drawer-open?)]
    {:z-index 10
     :height "90px"
     :width (str "calc(100% - " dw "px)")
     :margin-left (str dw "px")}))

(defn main-container [drawer-open?]
  (let [dw (drawer-width drawer-open?)
        body-margin 30]
    {:z-index 10
     :padding "0 24px"
     :width (str "calc(100% - " dw "px)")
     :margin-left (str dw "px")}))
