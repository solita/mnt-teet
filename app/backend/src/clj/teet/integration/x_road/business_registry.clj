(ns teet.integration.x-road.business-registry
  "Query business registry for company details"
  (:require [teet.integration.x-road.core :as x-road]
            [clojure.data.zip.xml :as z]
            [teet.log :as log]
            [clojure.string :as str])
  (:import (java.time.format DateTimeFormatter)))

(defn detailandmed-request-xml [{business-id :business-id :as params}]
  (x-road/request-xml
   (x-road/request-envelope
    params
    [:prod:detailandmed_v1 {:xmlns:prod "http://arireg.x-road.eu/producer/"}
     [:prod:keha
      [:prod:ariregistri_kood business-id]
      [:prod:yandmed 1]

      ;; We currently only use the first item, don't return unused info
      [:prod:iandmed 0]
      [:prod:kandmed 0]
      [:prod:dandmed 0]
      [:prod:maarused 0]]])))

(defn business-information-request-xml
  "Different params used to find required company values for company creation"
  [{business-id :business-id :as params}]
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
        [:prod:maarused 1]
        [:prod:ainult_kehtivad 1]
        [:prod:staatused "R"]]])))

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
            :ehak-nimetus [:ns1:ehak_nimetus]
            :tanav-maja-korter [:ns1:tanav_maja_korter]
            :postiindeks [:ns1:postiindeks]
            :algus-kpv [:ns1:algus_kpv ->date]
            :lopp-kpv [:ns1:lopp_kpv ->date]}))

(def parse-contact-method
  (partial fields->map
           {:kirje-id [:ns1:kirje_id ->num]
            :type [:ns1:liik z/text #(case %
                                       "EMAIL" :email
                                       "MOB" :phone
                                       "TEL" :phone2
                                       %)]
            :type-text [:ns1:liik_tekstina]
            :content [:ns1:sisu]
            :lopp-kpv [:ns1:lopp_kpv ->date]}))

(defn parse-business-details [zipped-xml]
  (let [item (z/xml1-> zipped-xml :SOAP-ENV:Body :ns1:detailandmed_v1Response :ns1:keha :ns1:ettevotjad :ns1:item)]
    ;; (z/xml-> nil) raises exception
    (if item
      {:addresses (z/xml-> item :ns1:yldandmed :ns1:aadressid :ns1:item parse-address)
       :contact-methods (z/xml-> item :ns1:yldandmed :ns1:sidevahendid :ns1:item parse-contact-method)}
      {:addresses []
       :contact-methods []})))

(defn parse-business-information-details
  "Extract required company information from x-road response to create the company in TEET"
  [zipped-xml]
  (when-let [item (z/xml1-> zipped-xml :SOAP-ENV:Body :ns1:detailandmed_v1Response :ns1:keha :ns1:ettevotjad :ns1:item)]
    (let [contact-methods (z/xml-> item :ns1:yldandmed :ns1:sidevahendid :ns1:item parse-contact-method)
          company-name (z/xml1-> item :ns1:nimi z/text)
          emails (->> contact-methods
                      (filter
                        #(and (= (:type %) :email)
                              (:content %)))
                      (mapv
                        :content))
          phone-numbers (->> contact-methods
                             (filter
                               #(and (#{:phone :phone2} (:type %))
                                     (:content %)))
                             (mapv
                               :content))]
      (if company-name
        {:company/name company-name
         :company/email (str/join ", " emails)
         :company/phone-number (str/join ", " phone-numbers)}
        (log/warn "No company name found in the business registry response")))))

(def business-registry-params
  {:client {:subsystem-code "teeregister"
            :member-code "70001490"}
   :service {:member-code "70000310"
             :subsystem-code "arireg"
             :service-code "detailandmed_v1"
             :version "v1"}})

(defn perform-detailandmed-request [url params]
  {:pre [(contains? params :business-id)]}
  (->> params
       (merge business-registry-params)
       detailandmed-request-xml
       (x-road/perform-request url)
       x-road/string->zipped-xml
       parse-business-details))

(defn perform-business-information-request
  [url params]
  {:pre [(contains? params :business-id)]}
  (->> params
       (merge business-registry-params)
       business-information-request-xml
       (x-road/perform-request url)
       x-road/string->zipped-xml
       parse-business-information-details))
