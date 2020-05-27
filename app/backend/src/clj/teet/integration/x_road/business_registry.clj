(ns teet.integration.x-road.business-registry
  "Query business registry for company details"
  (:require [teet.integration.x-road.core :as x-road]
            [clojure.data.zip.xml :as z])
  (:import (java.time.format DateTimeFormatter)))

(defn detailandmed-request-xml [{business-id :business-id :as params}]
  (x-road/request-xml
   (x-road/request-envelope
    params
    [:prod:detailandmed_v1 {:xmlns:prod "http://arireg.x-road.eu/producer/"}
     [:prod:keha
      [:prod:ariregistri_kood business-id]
      [:prod:yandmed 1]
      [:prod:iandmed 1]
      [:prod:kandmed 1]
      [:prod:dandmed 1]
      [:prod:maarused 1]]])))

(defn- ->num [node]
  (z/xml1-> node z/text #(Long/parseLong %)))

(let [fmt (DateTimeFormatter/ofPattern "yyyy-MM-dd'Z'")]
  (defn- ->date [node]
    (z/xml1-> node z/text #(java.time.LocalDate/parse % fmt))))

(defn- fields->map [fields item]
  (reduce (fn [m [key path]]
            (let [path (if (fn? (last path))
                         path
                         (into path [z/text]))
                  val (apply z/xml1-> item path)]
              (if val
                (assoc m key val)
                m)))
          {}
          fields))

(def parse-address
  (partial fields->map
           {:kirje-id [:ns1:kirje_id ->num]
            :kaardi-piirkond [:ns1:kaardi_piirkond]
            :kaardi-nr [:ns1:kaardi_nr ->num]
            :kaardi-tyyp [:ns1:kaardi_tyyp]
            :kande-nr [:ns1:kande_nr ->num]
            :ehak [:ns1:ehak]
            :ehak-nimetys [:ns1:ehak_nimetys]
            :tanav-maja-korter [:ns1:tanav_maja_korter]
            :postiindeks [:ns1:postiindeks]
            :algus-kpv [:ns1:algus_kpv ->date]
            :lopp-kpv [:ns1:lopp-kpv ->date]}))

(def parse-contact-method
  (partial fields->map
           {:kirje-id [:ns1:kirje_id ->num]
            :type [:ns1:liik z/text #(case %
                                       "EMAIL" :email
                                       "MOB" :phone
                                       %)]
            :type-text [:ns1:liik_tekstina]
            :content [:ns1:sisu]
            :lopp-kpv [:ns1:lopp_kpv ->date]}))

(defn parse-business-details [zipped-xml]
  (let [body (z/xml1-> zipped-xml :SOAP-ENV:Body)
        item (z/xml1-> body :ns1:detailandmed_v1Response :ns1:keha :ns1:ettevotjad :ns1:item)]
    {:addresses (z/xml-> item :ns1:yldandmed :ns1:aadressid :ns1:item parse-address)
     :contact-methods (z/xml-> item :ns1:yldandmed :ns1:sidevahendid :ns1:item parse-contact-method)}))

(defn perform-detailandmed-request [url params]
  (->> params
       (merge {:client {:subsystem-code "teeregister"
                        :member-code "70001490"}
               :service {:member-code "70000310"
                         :subsystem-code "arireg"
                         :service-code "detailandmed_v1"
                         :version "v1"}})
       detailandmed-request-xml
       (x-road/perform-request url)
       parse-business-details))
