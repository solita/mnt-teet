(ns teet.land.land-specs
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

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
                :estate-procedure/third-party-compensations
                :estate-procedure/process-fees]))

(s/def :land/estate-group-form
  (s/keys :req [:estate-procedure/pos
                :estate-procedure/type]))

(defn non-empty-string? [s]
  (and (string? s) (not (str/blank? s))))

(s/def :estate-procedure/pos (or non-empty-string?))

(s/def :estate-procedure/process-fees (s/coll-of :estate-procedure/process-fee))
(s/def :estate-procedure/process-fee
  (s/keys :req [:estate-process-fee/fee
                :estate-process-fee/recipient]
          :opt [:estate-process-fee/person-id
                :estate-process-fee/business-id]))

(s/def :estate-procedure/compensation (s/keys :opt [:estate-compensation/amount
                                                    :estate-compensation/description
                                                    :estate-compensation/reason
                                                    :db/id]))

(s/def :estate-procedure/land-exchange (s/keys :opt [:land-exchange/area
                                                     :land-exchange/cadastral-unit-id
                                                     :land-exchange/price-per-sqm
                                                     :db/id]))


(s/def :estate-procedure/land-exchanges (s/coll-of :estate-procedure/land-exchange))
(s/def :estate-procedure/third-party-compensations (s/coll-of :estate-procedure/compensation))

(s/def :estate-procedure/compensations (s/coll-of :estate-procedure/compensation))

(s/def :land/fetch-land-acquisitions (s/keys :req-un [::project-id]))

(s/def :land/fetch-estate-compensations (s/keys :req [:thk.project/id]))
