(ns teet.main
  (:require [org.httpkit.server :as server]
            [teet.routes :as routes]
            [taoensso.timbre :as log])
  (:gen-class))


(defn -main [& args]
  (log/info "Starting TEET service in port 3000")
  (server/run-server (routes/teet-routes) {:ip "0.0.0.0"
                                           :port 3000}))
