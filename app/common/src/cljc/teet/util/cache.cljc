(ns teet.util.cache)

(defn cached
  "Return function to get deferred value. Succesfully created
   value is cached and returned with subsequent calls.
   If `value-fn` throws exception when creating value, it isn't cached.
   Use this instead of delay if `value-fn` can have intermittent failures."
  [value-fn]
  (let [val (atom nil)]
    (fn []
      (if-let [v @val]
        v
        (swap! val (fn [_] (value-fn)))))))
