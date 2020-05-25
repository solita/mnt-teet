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
                :estate-procedure/priced-areas
                :estate-procedure/third-party-compensations
                :estate-procedure/process-fees]))

(s/def :land/estate-group-form
  (s/keys :req [:estate-procedure/pos
                :estate-procedure/type]))

(defn non-empty-string? [s]
  (and (string? s) (not (str/blank? s))))

(s/def :estate-procedure/pos non-empty-string?)

(s/def :estate-procedure/process-fees (s/coll-of :estate-procedure/process-fee))
(s/def :estate-procedure/process-fee
  (s/keys :req [:estate-process-fee/fee
                :estate-process-fee/recipient]
          :opt [:estate-process-fee/person-id
                :estate-process-fee/business-id]))

(s/def :estate-procedure/compensation (s/keys :opt [:estate-compensation/amount
                                                    :estate-compensation/description
                                                    :estate-compensation/reason]))

(s/def :estate-procedure/priced-area (s/keys :opt [:priced-area/area :priced-area/cadastral-unit-id
                                                      :priced-area/price-per-sqm]))


(s/def :estate-procedure/priced-areas (s/coll-of :estate-procedure/priced-area))
(s/def :estate-procedure/third-party-compensations (s/coll-of :estate-procedure/compensation))

(s/def :estate-procedure/compensations (s/coll-of :estate-procedure/compensation))
