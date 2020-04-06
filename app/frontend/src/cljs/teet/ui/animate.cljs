(ns teet.ui.animate)

(defn- elt-by-id [id]
  (js/document.getElementById (name id)))

(defn animate! [elt {:keys [duration-ms property to] :as _config}]
  (let [prop-name (name property)
        from (aget elt prop-name)
        animate! (fn animate! [start-ms now]
                   (let [start-ms (or start-ms now)]
                     (if (>= now (+ start-ms duration-ms))
                       (aset elt prop-name to)
                       (do
                         (aset elt
                               prop-name
                               (+ from
                                  (* (/ (- now start-ms)
                                        duration-ms)
                                     (- to from))))
                         (.requestAnimationFrame js/window
                                                 (partial animate! start-ms))))))]
    (.requestAnimationFrame js/window
                            (partial animate! nil))))

(defn animate-by-id! [id config]
  (animate! (elt-by-id id) config))

(defn scroll-into-view!
  ([elt]
   (.scrollIntoView elt))
  ([elt {:keys [behavior block inline]
         :or {behavior "auto"
              block    "start"
              inline   "nearest"}}]
   (.scrollIntoView elt #js {"behavior" (name behavior)
                             "block"    (name block)
                             "inline"   (name inline)})))

(defn scroll-into-view-by-id!
  ([id]
   (scroll-into-view! (elt-by-id id)))
  ([id config]
   (scroll-into-view! (elt-by-id id)
                      config)))

(defn focus!
  ([elt]
   (.focus elt))
  ([elt {:keys [prevent-scroll]
         :or {prevent-scroll false}}]
   (.focus elt #js {"preventScroll" prevent-scroll})))

(defn focus-by-id!
  ([id]
   (focus! (elt-by-id id)))
  ([id config]
   (focus! (elt-by-id id) config)))
