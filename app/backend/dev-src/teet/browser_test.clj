(ns teet.browser-test
  (:require [teet.environment :as environment]
            [teet.main :as main]
            [clojure.java.io :as io]))

(defn -main [& args]
  (let [config-file (first args)]
    (environment/load-local-config! (io/file config-file))
    (user/make-mock-users!)
    (user/give-admin-permission [:user/id user/manager-uid])
    (main/restart (io/file config-file))))
