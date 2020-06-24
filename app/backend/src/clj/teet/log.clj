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
    {:keys [level ?err msg_ ?ns-str ?line timestamp_ context]}]
   (let [{:keys [user]} context]
     (str (-> level name str/upper-case)
          \space (force timestamp_) \space
          \[ user \] \space
          (or ?ns-str "unknown-namespace") \: ?line " - "
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
(defn- ion-appender [{:keys [level msg_ ?ns-str ?line ?err]}]
  ((case level
     (:error :warn :fatal) cast/alert
     :info cast/event
     cast/dev)
   (merge
    {:msg (force msg_)
     :source/location (str ?ns-str ":" ?line)}
    (when ?err
      {:exception/trace (str/join "\n" (.getStackTrace ?err))
       :exception/type (-> ?err .getClass .getName)
       :exception/message (.getMessage ?err)}))))

(defn enable-ion-cast-appender! []
  (timbre/merge-config!
   {:appenders {:println {:enabled? false}
                :ion {:enabled? true
                      :fn ion-appender}}}))

;;
;; Re-export selected timbre functions
;;
(intern 'teet.log
        (with-meta 'debug {:macro true})
        @#'timbre/debug)
(intern 'teet.log
        (with-meta 'info {:macro true})
        @#'timbre/info)
(intern 'teet.log
        (with-meta 'warn {:macro true})
        @#'timbre/warn)
(intern 'teet.log
        (with-meta 'error {:macro true})
        @#'timbre/error)
(intern 'teet.log
        (with-meta 'fatal {:macro true})
        @#'timbre/fatal)
(intern 'teet.log
        (with-meta 'spy {:macro true})
        @#'timbre/spy)

;;
;; Datomic cast event
;;
(defn event [event-name data]
  (cast/event (merge data
                     {:msg event-name}
                     (when-let [user-id (not-empty (:user timbre/*context*))]
                       {:request/user-id user-id}))))

(defn audit [user-id audit-event audit-event-args]
  {:pre [(uuid? user-id)
         (and (keyword? audit-event)
              (some? (namespace audit-event)))
         (map? audit-event-args)]}
  (event "Audit"
         {:audit/event audit-event
          :audit/user-id user-id
          :audit/event-args audit-event-args}))

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
