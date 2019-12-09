(ns teet.ags.ags-parser
  "AGS format parser"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def f "example.ags")

(def ags-item-pattern #"^\"([^\"]*)\"(.*)")

(defn- parse-ags-line [input-line]
  (loop [items []
         input input-line]
    (if-let [[_ item input] (re-find ags-item-pattern input)]
      (let [items (conj items item)]
        (if (str/blank? input)
          items
          (recur items (subs input 1))))
      items)))

(def ^:private value-parser
  ;; FIXME: are these standardized?
  {"DT" (let [df (java.text.SimpleDateFormat. "yyyy-MM-dd")]
          #(when-not (str/blank? %)
             (.parse df %)))
   "2DP" #(when-not (str/blank? %)
            ;; PENDING: should parse as BigDecimal to get exact value?
            (Double/parseDouble %))})

(defn- group-data-parser [group heading unit type]
  (let [group-ns (second group)
        strip-prefix (str group-ns "_")
        fields (map (fn [field type]
                      (let [parser (value-parser type identity)]
                        [(keyword group-ns
                                   (if (str/starts-with? field strip-prefix)
                                     (subs field (count strip-prefix))
                                     field))
                         parser]))
                    (drop 1 heading)
                    (drop 1 type))]
    (fn [data-line]
      (let [field-values (parse-ags-line data-line)]
        (into {}
              (map (fn [[kw parser] value]
                     [kw (parser value)])
                   fields (drop 1 field-values)))))))

(defn parse-group [in-seq]
  (let [[group heading unit type] (map parse-ags-line (take 4 in-seq))
        in-seq (drop 4 in-seq)
        data (take-while #(not (str/blank? %)) in-seq)
        in-seq (drop (inc (count data)) in-seq)

        parse-data-fn (group-data-parser group heading unit type)]
    {:group {:group-name (nth group 1)
             :data (map parse-data-fn data)}
     :in-seq in-seq}))

(defn- groups-seq [in-seq]
  (let [{:keys [group in-seq]} (parse-group in-seq)]
    (lazy-seq
     (cons group
           (when (seq in-seq)
             (groups-seq in-seq))))))

(defn parse
  "Returns a lazy sequence of AGS data from given input.
  Caller is responsible for keeping the input open until all output
  has been consumed."
  [input]
  (groups-seq (line-seq (io/reader input))))

(defn t1 []
  (def g1 (with-open [in (io/reader f)] (doall (parse in)))))
