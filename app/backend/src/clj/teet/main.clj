(ns teet.main
  (:require [org.httpkit.server :as server]
            [teet.routes :as routes]
            [tara.routes :as tara-routes]
            [tara.endpoint :as tara-endpoint]
            [compojure.core :refer [routes]]
            [ring.middleware.params :as params]
            [taoensso.timbre :as log])
  (:gen-class))

(def server nil)

(defn start []
  (alter-var-root
   #'server
   (fn [_]
     (log/info "Starting TEET service in port 4000")
     (server/run-server
      (params/wrap-params
       (routes
        (routes/teet-routes)
        (tara-routes/tara-routes (tara-endpoint/discover "https://tara-test.ria.ee/oidc")
                                 {:client-id "TARA-Demo"
                                  :base-url "https://dev-teet.solitacloud.fi"})))
      {:ip "0.0.0.0"
       :port 4000}))))

(defn stop []
  (server))

(defn restart []
  (when server
    (stop))
  (start))

(defn -main [& args]
  (start))
