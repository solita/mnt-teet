(ns teet.logging
  (:require [clojure.string :as str]
            [taoensso.timbre :as timbre]))

(defmacro with-context
  [context & body]
  `(timbre/with-context
     (merge timbre/*context* ~context)
     ~@body))

(defn- output
  ([data] (output {:stacktrace-fonts {}} data))
  ([opts
    {:keys [level ?err msg_ ?ns-str timestamp_ context]}]
   (let [{:keys [user]} context]
     (str (-> level name str/upper-case)
          \space (force timestamp_) \space
          \[ user \] \space
          (or ?ns-str "unknown-namespace") " - "
          (force msg_)
          (when ?err (str \n (timbre/stacktrace ?err opts)))))))

;; TODO set loglevel from env
;; (timbre/set-level! :foo)

(def ts-format "yyyy-MM-dd HH:mm:ss.SSS")

(timbre/merge-config! {:timestamp-opts {:pattern ts-format
                                        :timezone :jvm-default}
                       :output-fn output})
