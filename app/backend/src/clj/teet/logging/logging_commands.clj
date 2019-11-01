(ns teet.logging.logging-commands
  "Commands for logging frontend errors"
  (:require [teet.db-api.core :as db-api]
            [teet.log :as log]))

(defmethod db-api/command! :logging/log-error [_ msg]
  (log/metric :frontend-error 1 :count)
  (log/error msg))
