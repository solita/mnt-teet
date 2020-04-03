(ns teet.project.land-controller
  (:require [tuck.core :as t]
            [clojure.string :as str]
            [teet.util.collection :as cu]))

(defrecord SetCadastralInfo [response])

(extend-protocol t/Event
  SetCadastralInfo
  (process-event [{response :response} app]
    (let [page (:page app)

          data (->> response
                    :results
                    (mapv #(get % "properties"))
                    (mapv
                      #(cu/map-keys keyword %))
                    ;;Assoc teet-id for showing hovers on map
                    (mapv
                      #(assoc % :teet-id (str "2:" (:TUNNUS %)))))]

      (assoc-in app [:route page :thk.project/related-cadastral-units-info :results] data))))
