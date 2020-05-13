(ns teet.land.land-specs
  (:require [clojure.spec.alpha :as s]))

(s/def :land-acquisition/form
  (s/keys :req [:land-acquisition/impact]))

(s/def :land/create-estate-procedure
  (s/keys :req [:estate-procedure/pos
                :estate-procedure/type
                :estate-procedure/estate-id
                :thk.project/id]
          :opt [:estate-procedure/reason
                :db/id
                :estate-procedure/urgent-bonus
                :estate-procedure/compensations
                :estate-procedure/motivation-bonus
                :estate-procedure/land-exchanges
                :estate-procedure/third-party-compensations]))

(s/def :estate-procedure/compensation (s/keys :opt [:estate-compensation/amount
                                                    :estate-compensation/description
                                                    :estate-compensation/reason]))

(s/def :estate-procedure/land-exchange (s/keys :opt [:land-exchange/area :land-exchange/cadastral-unit-id
                                                      :land-exchange/price-per-sqm]))


(s/def :estate-procedure/land-exchanges (s/coll-of :estate-procedure/land-exchange))
(s/def :estate-procedure/third-party-compensations (s/coll-of :estate-procedure/compensation))

(s/def :estate-procedure/compensations (s/coll-of :estate-procedure/compensation))
