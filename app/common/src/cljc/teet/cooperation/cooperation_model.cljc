(ns teet.cooperation.cooperation-model
  "Model for cooperation"
  (:require #?(:clj [clj-time.core :as t]
               :cljs [cljs-time.core :as t])
            #?(:clj [clj-time.coerce :as tc]
               :cljs [cljs-time.coerce :as tc])))

(defn valid-until
  "Calculate response valid-until date based on date and valid months."
  [{:cooperation.response/keys [date valid-months]}]
  (-> date
      tc/from-date
      (t/plus (t/months valid-months))
      tc/to-date))

(defn with-valid-until
  "Update valid-until value for cooperation response."
  [response]
  (if (and (contains? response :cooperation.response/date)
           (contains? response :cooperation.response/valid-months))
    (assoc response :cooperation.response/valid-until (valid-until response))
    (dissoc response :cooperation.response/valid-until)))
