(ns teet.integration.integration-context
  "Common integration context handling"
  (:require [clojure.stacktrace :as stacktrace]
            [clojure.spec.alpha :as s]))

(defn- stack-trace [e]
  (with-out-str (stacktrace/print-stack-trace e 10)))

(defn ctx-exception [ctx message & [e]]
  (ex-info message
           (assoc ctx :error {:message (str message
                                            (when e
                                              (str ": "
                                                   (.getMessage e))))
                              :stack-trace (when e
                                             (stack-trace e))})))
(defmacro ctx->
  "Pipe ctx through steps, wrapping all steps in exception handling"
  [ctx & steps]
  `(-> ~ctx
       ~@(for [step steps]
           `((fn [~'ctx]
               (try
                 ~(if (list? step)
                    (concat (take 1 step)
                            (list 'ctx)
                            (drop 1 step))
                    (list step 'ctx))
                 (catch Exception e#
                   (throw (ctx-exception ~'ctx ~(or (:doc (meta step))
                                                    (str step)) e#)))))))))

(defn- validate-spec-and-path [{:keys [default-path spec]} validate-for]
  (assert (vector? default-path) (str "Expected :default-path vector in " validate-for))
  (assert (try
            (s/spec (if (symbol? spec)
                      (eval spec)
                      spec))
            (catch Exception _ nil))
          (str "Expected valid :spec in " validate-for)))

(defn validate [spec message value ctx]
  (when-not (s/valid? spec value)
    (throw (ex-info message
                    {:explain-data (s/explain-data spec value)
                     :ctx ctx})))
  value)

(defmacro defstep
  "Define an integration step. Yields a function that takes the
  context as the first argument and an optional customization map.


  The context is a map of values that is threaded through the integration
  execution. Each step will take some values from it and add its results to
  it.


  The optional customization map can be used to override the default paths
  that are taken from and put into the context.


  Example:
  (defstep my-add-one
    {:ctx the-ctx
     :doc \"Increments :input-number in ctx by one, stores result in :output-number\"
     :in {:as the-number
          :spec number?
          :default-path [:input-number]}
     :out {:default-path [:output-number]
           :spec number?}}
   (inc the-number))
  "
  [step-name {:keys [doc ctx in out]
              :or {ctx 'ctx}}
   & body]
  (let [{out-path :default-path
         out-spec :spec} out]
    (assert (string? doc) "Provide documentation for this step in :doc key")
    (assert (every? symbol? (keys in))
            "Expected :in map to be a mapping from symbols to definitions")
    (doseq [[in-sym in-def] in]
      (assert (keyword? (:path-kw in-def))
              (str "Specify :path-kw keyword for " (name in-sym) " to override default path."))
      (validate-spec-and-path in-def (str (name in-sym) " input definition")))
    (validate-spec-and-path out ":out map")

    `(defn ~step-name ~doc
       ([~ctx] (~step-name ~ctx {}))
       ([~ctx ~'overrides]
        (let [~@(mapcat
                 (fn [[in-sym {:keys [path-kw default-path spec]}]]
                   [in-sym `(validate ~spec ~(str "input validation for " (name in-sym))
                                      (get-in ~ctx (or (~path-kw ~'overrides)
                                                       ~default-path)))])
                 in)]
          (let [out# (do ~@body)]
            (when-not (s/valid? ~out-spec out#)
              (throw (ex-info ~(str (name step-name) ": output validation failed.")
                              {:explain-data (s/explain-data ~out-spec out#)
                               :ctx ~ctx})))
            (assoc-in ~ctx
                      (or (:out-path ~'overrides)
                          ~out-path)
                      out#)))))))
