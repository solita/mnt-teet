(ns ^:db teet.land.land-queries-test
  (:require [clojure.test :refer :all]
            [clojure.zip :as zip]
            [teet.integration.x-road.core :as x-road]
            teet.land.land-queries
            [teet.test.utils :as tu]))

(use-fixtures :each
  tu/with-environment
  (tu/with-db)
  tu/with-global-data)

(def mock-x-road-response
  "This response does not contain address information"
  [{:tag :SOAP-ENV:Envelope,
    :attrs
    {:xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance",
     :xmlns:xsd "http://www.w3.org/2001/XMLSchema",
     :xmlns:ZSI "http://www.zolera.com/schemas/ZSI/",
     :xmlns:SOAP-ENV "http://schemas.xmlsoap.org/soap/envelope/",
     :xmlns:SOAP-ENC "http://schemas.xmlsoap.org/soap/encoding/"},
    :content
    [{:tag :SOAP-ENV:Header,
      :attrs {:xmlns:ns1 "http://x-road.eu/xsd/xroad.xsd"},
      :content
      [{:tag :ns1:userId, :attrs nil, :content ["EE12345678"]}
       {:tag :ns1:id,
        :attrs nil,
        :content ["uuid"]}
       {:tag :ns1:requestHash,
        :attrs
        {:algorithmId "http://www.w3.org/2001/04/xmlenc#sha512"},
        :content
        ["base64"]}
       {:tag :ns1:protocolVersion, :attrs nil, :content ["4.0"]}
       {:tag :ns1:client,
        :attrs
        {:ns2:objectType "SUBSYSTEM",
         :xmlns:ns2 "http://x-road.eu/xsd/identifiers"},
        :content
        [{:tag :ns2:xRoadInstance, :attrs nil, :content ["ee-dev"]}
         {:tag :ns2:memberClass, :attrs nil, :content ["GOV"]}
         {:tag :ns2:memberCode, :attrs nil, :content ["0000000"]}
         {:tag :ns2:subsystemCode,
          :attrs nil,
          :content ["teeregister"]}]}
       {:tag :ns1:service,
        :attrs
        {:ns2:objectType "SERVICE",
         :xmlns:ns2 "http://x-road.eu/xsd/identifiers"},
        :content
        [{:tag :ns2:xRoadInstance, :attrs nil, :content ["ee-dev"]}
         {:tag :ns2:memberClass, :attrs nil, :content ["GOV"]}
         {:tag :ns2:memberCode, :attrs nil, :content ["0000000"]}
         {:tag :ns2:subsystemCode, :attrs nil, :content ["arireg"]}
         {:tag :ns2:serviceCode,
          :attrs nil,
          :content ["detailandmed_v1"]}
         {:tag :ns2:serviceVersion, :attrs nil, :content ["v1"]}]}]}
     {:tag :SOAP-ENV:Body,
      :attrs {:xmlns:ns1 "http://arireg.x-road.eu/producer/"},
      :content
      [{:tag :ns1:detailandmed_v1Response,
        :attrs nil,
        :content
        [{:tag :ns1:paring,
          :attrs nil,
          :content
          [{:tag :ns1:ariregistri_kood,
            :attrs nil,
            :content ["1234567"]}
           {:tag :ns1:yandmed, :attrs nil, :content ["true"]}
           {:tag :ns1:iandmed, :attrs nil, :content ["false"]}
           {:tag :ns1:kandmed, :attrs nil, :content ["false"]}
           {:tag :ns1:dandmed, :attrs nil, :content ["false"]}
           {:tag :ns1:maarused, :attrs nil, :content ["false"]}]}
         {:tag :ns1:keha,
          :attrs nil,
          :content
          [{:tag :ns1:ettevotjad, :attrs nil, :content nil}
           {:tag :ns1:leitud_ettevotjate_arv,
            :attrs nil,
            :content ["0"]}]}]}]}]}
   nil])

(deftest estate-owner-info
  (testing "estate-owner-info resturns empty contact details when they're not present in the x-road response"
    (with-redefs [x-road/perform-request (fn [_url _request-xml]
                                           (zip/xml-zip mock-x-road-response))]
      (is (= (tu/local-query tu/mock-user-boss
                             :land/estate-owner-info
                             {:thk.project/id "11111" :business-id "1234"})
             {:addresses []
              :contact-methods []})))))
