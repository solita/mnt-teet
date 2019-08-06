(ns teet.ui.grid
  "CSS grid component"
  (:require [clojure.string :as str]
            [stylefy.core :as stylefy]))

(def ^:const grid-style {:display "grid"
                         :grid-gap "1rem"})

(defn- grid-columns-style [columns]
  (assert (or (number? columns)
              (vector? columns))
          "Grid columns must be a number (column count) or vector of column defs")

  (cond
    (number? columns)
    {:grid-template-columns (str/join " "
                                      (repeat columns "auto"))}

    (vector? columns)
    {:grid-template-columns (str/join " " columns)}))

(defn grid [{:keys [columns grid-style-opts] :as opts} & cells]
  [:div (stylefy/use-style
         (merge grid-style
                (or grid-style-opts {})
                (when columns
                  (grid-columns-style columns))))
   cells])

(defn- grid-row-style [row]
  (assert (or (number? row)
              (vector? row))
          "Grid row style must be number or vector")
  (cond
    (number? row)
    {:grid-row-start row}

    (vector? row)
    {:grid-row-start (first row)
     :grid-row-end (second row)}))

(defn- grid-col-style [col]
  (assert (or (number? col)
              (vector? col))
          "Grid col style must be number or vector")
  (cond
    (number? col)
    {:grid-column-start col}

    (vector? col)
    {:grid-column-start (first col)
     :grid-column-end (second col)}))

(defn cell
  ([content] [cell {} content])
  ([{:keys [row column style]} content]
   [:div (if (or row column style)
           (stylefy/use-style
            (merge style
                   (when row
                     (grid-row-style row))
                   (when column
                     (grid-col-style column)))))
    content]))
