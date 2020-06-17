(ns teet.land.land-model)

(defn estate-land-acquisitions
  "Return land acquisitions related to given estate with the associated land unit information"
  [estate-id land-units land-acquisitions]
  (mapv
    (fn [la]
      (let [teet-id (:land-acquisition/cadastral-unit la)
            land-unit (some
                        (fn [unit]
                          (when (= teet-id (:teet-id unit))
                            unit))
                        land-units)]
        (merge la {:unit-data land-unit})))
    (filterv
      (fn [la]
        (= (:land-acquisition/estate-id la) estate-id))
      land-acquisitions)))

(defn estate-procedure-costs
  "Return formatted list of cost cost maps for estate procedure"
  [{:estate-procedure/keys [compensations third-party-compensations motivation-bonus urgent-bonus]}]
  (let [parse-compensation (fn [{:estate-compensation/keys [description reason amount]}]
                             {:reason reason
                              :description description
                              :amount amount})
        motiv-bonus (when motivation-bonus
                      [{:reason :estate-procedure/motivation-bonus
                        :amount motivation-bonus}])
        u-bonus (when urgent-bonus
                  [{:reason :estate-procedure/urgent-bonus
                    :amount urgent-bonus}])
        parsed-comps (map parse-compensation compensations)
        parsed-tpc (map parse-compensation third-party-compensations)]
    (concat parsed-comps
            parsed-tpc
            motiv-bonus
            u-bonus)))

(defn total-estate-cost
  [estate-procedure land-acquisitions]
  (let [estate-total (reduce
                       (fn [total {:keys [amount]}]
                         (+ total (int amount)))
                       0
                       (estate-procedure-costs estate-procedure))
        land-exchange (reduce
                        (fn [total {:land-exchange/keys [area price-per-sqm]}]
                          (+ total (* area price-per-sqm)))
                        0
                        (:estate-procedure/land-exchanges estate-procedure))
        la-total (reduce
                   (fn [total {:land-acquisition/keys [area-to-obtain price-per-sqm]}]
                     (+ total (* price-per-sqm area-to-obtain)))
                   0
                   land-acquisitions)]
    (- (+ estate-total la-total) land-exchange)))


(def common-procedure-keys
  [:estate-procedure/type :estate-procedure/estate-id :db/id :estate-procedure/project])

(def procedure-type-options
  {:estate-procedure.type/urgent
   {:keys
    [:estate-procedure/urgent-bonus
     :estate-procedure/motivation-bonus
     :estate-procedure/third-party-compensations]}

   :estate-procedure.type/acquisition-negotiation
   {:keys [:estate-procedure/compensations
           :estate-procedure/motivation-bonus
           :estate-procedure/third-party-compensations
           :estate-procedure/process-fees]}

   :estate-procedure.type/expropriation
   {:keys [:estate-procedure/compensations
           :estate-procedure/motivation-bonus
           :estate-procedure/third-party-compensations]}

   :estate-procedure.type/property-rights
   {:keys [:estate-procedure/third-party-compensations]}

   :estate-procedure.type/property-trading
   {:keys [:estate-procedure/land-exchanges
           :estate-procedure/third-party-compensations]}})


(defn estate-compensation
  "Returns only valid keys for estate type for
   estate compensation for the given estate id"
  [estate-id estate-compensations]
  (let [estate-comp (some
                      (fn [ec]
                        (when (= (:estate-procedure/estate-id ec) estate-id)
                          ec))
                      estate-compensations)
        type (:estate-procedure/type estate-comp)]
    (select-keys estate-comp (concat common-procedure-keys
                                     (get-in procedure-type-options
                                             [type :keys])))))

(defn publicly-owned?
  "Check if estate is fully publicly owner.
  Returns true if there is ownership information and every owner is a
  public legal entity."
  [{omandiosad :omandiosad :as _estate}]
  (and (seq omandiosad)
       (every? (comp (partial = "Avalik-õiguslik juriidiline isik") :isiku_tyyp)
               omandiosad)))
