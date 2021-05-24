(ns teet.asset.assets-controller)

(defn assets-query [{:keys [fclass] :as _filters}]
  (when (seq fclass)
    {:query :assets/search
     :args {:fclass (into #{}
                          (map (comp :db/ident second))
                          fclass)}}))
