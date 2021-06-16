(ns hooks.db-api
  (:require [clj-kondo.hooks-api :as api]))

;; turn defcommand into something clj-kondo understands
;;
;; (defcommand :name {:context <ctx> :payload <payload>} <body>)
;; =>
;; (defn command1232 [payload345 context678]
;;   (let [<ctx> context678
;;         <payload> payload345]
;;     <body>
;;

(defn options-map [node]
  (let [options-map-nodes (:children (nth (:children node) 2))]
    (into {}
          (map (fn [[key-node val-node]]
                 [(:k key-node)
                  val-node]))
          (partition 2 options-map-nodes))))

(defn debug [out]
  (println "OUT: " (pr-str out))
  out)

(defn request [node binding-opts body-opt]
  (let [options (options-map node)
        fn-name (gensym "defrequest")]
    {:node
     (api/list-node
      (list (api/token-node 'defn)
            (api/token-node fn-name)
            (api/vector-node (mapv #(get options % (api/token-node '_))
                                   binding-opts))

            ;; Add preconditions
            (api/list-node
             (list* (api/token-node 'do)
                    (:children (:pre options))))

            ;; Add transact or body
            (or (and body-opt (get options body-opt))
                (api/list-node
                 (list* (api/token-node 'do)
                        (drop 3 (:children node)))))))}))

(defn defcommand [{node :node}]
  (request node [:context :payload :config] :transact))

(defn defquery [{node :node}]
  (request node [:context :args :config] nil))
