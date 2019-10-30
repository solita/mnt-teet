(ns teet.log
  (:require [clojure.string :as str]
            [datomic.ion.cast :as cast]
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

;;
;; Ion appender
;;
(defn- ion-appender [{:keys [level _output]}]
  ((case level
     (:error :warn :fatal) cast/alert
     cast/dev)
   {:msg (force _output)}))

(defn enable-ion-cast-appender! []
  (timbre/merge-config!
   {:appenders {:println {:enabled? false}
                :ion {:enabled? true
                      :fn ion-appender}}}))

;;
;; Re-export selected timbre functions
;;
(defmacro debug [& args]
  `(timbre/debug ~@args))
(defmacro info [& args]
  `(timbre/info ~@args))
(defmacro warn [& args]
  `(timbre/warn ~@args))
(defmacro error [& args]
  `(timbre/error ~@args))
(defmacro fatal [& args]
  `(timbre/fatal ~@args))
(defmacro spy [& args]
  `(timbre/spy ~@args))

;;
;; Datomic cast event
;;
(defn event [event-name data]
  (cast/event (merge data
                     {:msg event-name}
                     (when-let [user-id (not-empty (:user timbre/*context*))]
                       {:request/user-id user-id}))))

(defn metric
  "Casts a metric to Amazon CloudWatch.
  `metric-name`: a keyword
  `value`: a number can be cast via Clojure double
  `units`: one of `:msec`, `:bytes`, `:kb`, `:mb`, `:gb`, `:sec`, `:count`"
  [metric-name value units]
  (cast/metric {:name metric-name
                :value value
                :units units}))

(defn redirect-ion-casts! [target]
  (cast/initialize-redirect target))
