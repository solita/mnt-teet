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

(def third-party-display-attrs
  "Attributes to pull for showing a 3rd party"
  [:db/id
   :cooperation.3rd-party/name
   :cooperation.3rd-party/phone
   :cooperation.3rd-party/email
   :cooperation.3rd-party/id-code])

(def application-overview-attrs
  "Attributes to pull for displaying an application overview.
  Includes fields from response and position."
  [:db/id
   :cooperation.application/type
   :cooperation.application/date
   :cooperation.application/response-type
   :cooperation.application/response-deadline
   {:cooperation.application/response
    [:cooperation.response/date
     :cooperation.response/valid-until]}])
