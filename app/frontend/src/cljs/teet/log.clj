(ns teet.log
  (:require [taoensso.timbre :as timbre]))

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
