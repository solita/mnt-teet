(ns teet.ui.animate)

(defn animate! [{:keys [duration-ms] :as _config} elt prop-name to]
  (let [prop-name (name prop-name)
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

(defn animate-by-id! [config id prop-name to]
  (animate! config (js/document.getElementById (name id)) prop-name to))
