(ns teet.land.land-specs
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(defn decimal-number?
  [s]
  (re-matches #"^\d+((,|\.)\d+)?$" s))

(s/def :land-acquisition/price-per-sqm decimal-number?)
(s/def :estate-procedure/motivation-bonus decimal-number?)
(s/def :estate-procedure/urgent-bonus decimal-number?)
(s/def :estate-process-fee/fee decimal-number?)

(s/def :land-acquisition/form
  (s/keys :req [:land-acquisition/impact]
          :opt [:land-acquisition/price-per-sqm]))

(s/def :land/estate-procedure-form
  (s/keys :req [(or :estate-procedure/type :estate-procedure/third-party-compensations)]
          :opt [:estate-procedure/type
                :estate-procedure/reason
                :db/id
                :estate-procedure/urgent-bonus
                :estate-procedure/compensations
                :estate-procedure/motivation-bonus
                :estate-procedure/land-exchanges
                :estate-procedure/third-party-compensations
                :estate-procedure/process-fees]))

(s/def :land/estate-procedure
  (s/keys :req [:estate-procedure/estate-id
                :thk.project/id
                (or :estate-procedure/type :estate-procedure/third-party-compensations)]
          :opt [:estate-procedure/type
                :estate-procedure/reason
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

(s/def :estate-procedure/process-fees (s/coll-of :estate-procedure/process-fee))
(s/def :estate-procedure/process-fee
  (s/keys :opt [:estate-process-fee/fee
                :estate-process-fee/recipient
                :estate-process-fee/person-id
                :estate-process-fee/business-id]))

(s/def :estate-procedure/compensation (s/keys :opt [:estate-compensation/amount
                                                    :estate-compensation/description
                                                    :estate-compensation/reason]))

(s/def :estate-procedure/compensation-or-blank
  (s/or :blank #(every? str/blank? (vals %))
        :compensation :estate-procedure/compensation))

(s/def :estate-compensation/description non-empty-string?)

(s/def :estate-compensation/amount non-empty-string?)

(s/def :estate-procedure/land-exchange (s/keys :opt [:land-exchange/area
                                                     :land-exchange/cadastral-unit-id
                                                     :land-exchange/price-per-sqm]))


(s/def :estate-procedure/land-exchanges (s/coll-of :estate-procedure/land-exchange))
(s/def :estate-procedure/third-party-compensations (s/coll-of :estate-procedure/compensation-or-blank))

(s/def :estate-procedure/compensations (s/coll-of :estate-procedure/compensation))

(s/def :land/fetch-land-acquisitions (s/keys :req-un [::project-id]))

(s/def :land/fetch-estate-compensations (s/keys :req [:thk.project/id]))
