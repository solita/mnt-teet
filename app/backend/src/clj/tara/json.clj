(ns tara.json
  "Parse TARA response JSON"
  (:require [cheshire.core :as cheshire]
            [clojure.string :as str]))

(defn parse [text]
  (cheshire/decode text #(keyword (str/replace % "_" "-"))))
