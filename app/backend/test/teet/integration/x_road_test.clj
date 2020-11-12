(ns teet.integration.x-road-test
  (:require [clojure.test :as test :refer [deftest is]]
            [teet.integration.x-road.property-registry :as property-registry]
            [teet.integration.x-road.core :refer [string->zipped-xml]]))

(def joint-resp "<?xml version=\"1.0\" encoding=\"utf-8\"?>
<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">
  <s:Header>
  </s:Header>
  <s:Body>
    <Kinnistu_DetailandmedResponse xmlns=\"http://kr.x-road.eu\">
      <detailandmed xmlns:a=\"http://schemas.datacontract.org/2004/07/KinnistuService.DTO\"
      xmlns:i=\"http://www.w3.org/2001/XMLSchema-instance\">
      </detailandmed>
      <jagu0 xmlns:a=\"http://schemas.datacontract.org/2004/07/KinnistuService.DTO\"
      xmlns:i=\"http://www.w3.org/2001/XMLSchema-instance\">
        <a:Jagu0>
        </a:Jagu0>
      </jagu0>
      <jagu1 xmlns:a=\"http://schemas.datacontract.org/2004/07/KinnistuService.DTO\"
      xmlns:i=\"http://www.w3.org/2001/XMLSchema-instance\">
        <a:Jagu1>
        </a:Jagu1>
      </jagu1>
      <jagu2 xmlns:a=\"http://schemas.datacontract.org/2004/07/KinnistuService.DTO\"
      xmlns:i=\"http://www.w3.org/2001/XMLSchema-instance\">
        <a:Jagu2>
          <a:registriosa_nr>987987987</a:registriosa_nr>
          <a:jao_nr>2</a:jao_nr>
          <a:kande_alus>&lt;root/&gt;</a:kande_alus>
          <a:kande_kehtivus>kehtiv</a:kande_kehtivus>
          <a:kande_nr>4</a:kande_nr>
          <a:omandiosad>
            <a:Jagu2.Omandiosa>
              <a:isikud>
                <a:KinnistuIsik>
                  <a:eesnimi>eesnimi1</a:eesnimi>
                  <a:isiku_koodid>
                    <a:Isiku_kood>
                      <a:r_kood>123123123</a:r_kood>
                      <a:r_riik>EST</a:r_riik>
                    </a:Isiku_kood>
                  </a:isiku_koodid>
                  <a:isiku_liik>Füüsiline isik</a:isiku_liik>
                  <a:kodakondsus>EST</a:kodakondsus>
                  <a:nimi>nimi1</a:nimi>
                  <a:synniaeg>1923-23-23T00:00:00</a:synniaeg>
                </a:KinnistuIsik>
                <a:KinnistuIsik>
                  <a:eesnimi>eesnimi2</a:eesnimi>
                  <a:isiku_koodid>
                    <a:Isiku_kood>
                      <a:r_kood>345345345</a:r_kood>
                      <a:r_riik>EST</a:r_riik>
                    </a:Isiku_kood>
                  </a:isiku_koodid>
                  <a:isiku_tyyp>Füüsiline isik</a:isiku_tyyp>
                  <a:kodakondsus>EST</a:kodakondsus>
                  <a:nimi>nimi2</a:nimi>
                  <a:synniaeg>1912-12-12T00:00:00</a:synniaeg>
                </a:KinnistuIsik>
              </a:isikud>
              <a:omandi_algus>
              2000-11-11T09:66:77.123</a:omandi_algus>
              <a:omandi_liik>K</a:omandi_liik>
              <a:omandi_liik_tekst>
              Kaasõigustatus</a:omandi_liik_tekst>
              <a:omandi_lopp i:nil=\"true\"></a:omandi_lopp>
              <a:omandiosa_lugeja>123</a:omandiosa_lugeja>
              <a:omandiosa_nimetaja>1231</a:omandiosa_nimetaja>
              <a:omandiosa_suurus>123/1231</a:omandiosa_suurus>
            </a:Jagu2.Omandiosa>
            <a:Jagu2.Omandiosa>
              <a:isikud>
                <a:KinnistuIsik>
                  <a:eesnimi i:nil=\"true\"></a:eesnimi>
                  <a:isiku_koodid>
                    <a:Isiku_kood>
                      <a:r_kood>8888</a:r_kood>
                      <a:r_riik>EST</a:r_riik>
                    </a:Isiku_kood>
                  </a:isiku_koodid>
                  <a:isiku_liik>liik-text</a:isiku_liik>
                  <a:isiku_liik_ID>liik-id</a:isiku_liik_ID>
                  <a:isiku_tyyp>Juriidiline isik</a:isiku_tyyp>
                  <a:kodakondsus>EST</a:kodakondsus>
                  <a:nimi>nimi3</a:nimi>
                  <a:synniaeg>1933-11-30T00:00:00</a:synniaeg>
                </a:KinnistuIsik>
              </a:isikud>
              <a:omandi_algus>
              2011-11-11T23:23:23.023</a:omandi_algus>
              <a:omandi_liik>K</a:omandi_liik>
              <a:omandi_liik_tekst>
              Kaasõigustatus</a:omandi_liik_tekst>
              <a:omandi_lopp i:nil=\"true\"></a:omandi_lopp>
              <a:omandiosa_lugeja>1010</a:omandiosa_lugeja>
              <a:omandiosa_nimetaja>2020</a:omandiosa_nimetaja>
              <a:omandiosa_suurus>1010/2020</a:omandiosa_suurus>
            </a:Jagu2.Omandiosa>
          </a:omandiosad>
        </a:Jagu2>
      </jagu2>
      <jagu3 xmlns:a=\"http://schemas.datacontract.org/2004/07/KinnistuService.DTO\"
      xmlns:i=\"http://www.w3.org/2001/XMLSchema-instance\">
        <a:Jagu3>
          <a:kande_alguskuupaev>
          2012-12-12T12:12:12.123</a:kande_alguskuupaev>
          <a:kande_liik>kande-liik-nr2</a:kande_liik>
          <a:kande_liik_tekst>kande-liik-text2
          </a:kande_liik_tekst>
          <a:kande_loppkuupaev i:nil=\"true\"></a:kande_loppkuupaev>
          <a:kande_tekst></a:kande_tekst>
          <a:registriosa_nr>55555</a:registriosa_nr>
          <a:hoonestusoiguse_registriosa_nr i:nil=\"true\">
          </a:hoonestusoiguse_registriosa_nr>
          <a:jao_nr>42</a:jao_nr>
          <a:kande_alus></a:kande_alus>
          <a:kande_alusdokumendid>
          </a:kande_alusdokumendid>
          <a:kande_kehtivus>kehtiv</a:kande_kehtivus>
          <a:kande_nr>3</a:kande_nr>
          <a:koormatise_rahaline_vaartus>
          43</a:koormatise_rahaline_vaartus>
          <a:koormatise_rahalise_vaartuse_valuuta i:nil=\"true\">
          </a:koormatise_rahalise_vaartuse_valuuta>
          <a:koormatise_tahtaeg_aastates>
          44</a:koormatise_tahtaeg_aastates>
          <a:koormatise_tahtaeg_kuudes>
          45</a:koormatise_tahtaeg_kuudes>
          <a:oigustatud_isikud>
            <a:KinnistuIsik>
              <a:eesnimi i:nil=\"true\"></a:eesnimi>
              <a:isiku_koodid>
                <a:Isiku_kood>
                  <a:r_kood>11050857</a:r_kood>
                  <a:r_riik>EST</a:r_riik>
                </a:Isiku_kood>
              </a:isiku_koodid>
              <a:isiku_liik>liik-text3</a:isiku_liik>
              <a:isiku_liik_ID>liik-id3</a:isiku_liik_ID>
              <a:isiku_tyyp>Juriidiline isik</a:isiku_tyyp>
              <a:kodakondsus>EST</a:kodakondsus>
              <a:nimi>nimi4</a:nimi>
              <a:synniaeg>2004-06-16T00:00:00</a:synniaeg>
            </a:KinnistuIsik>
          </a:oigustatud_isikud>
          <a:valitsev_registriosa_nr i:nil=\"true\">
          </a:valitsev_registriosa_nr>
        </a:Jagu3>
      </jagu3>
      <jagu4 xmlns:a=\"http://schemas.datacontract.org/2004/07/KinnistuService.DTO\"
      xmlns:i=\"http://www.w3.org/2001/XMLSchema-instance\">
        <a:Jagu4>
          <a:kande_alguskuupaev>
          2016-09-06T09:38:59.487</a:kande_alguskuupaev>
          <a:kande_liik>liik-nr5</a:kande_liik>
          <a:kande_liik_tekst>liik-text5</a:kande_liik_tekst>
          <a:kande_loppkuupaev i:nil=\"true\"></a:kande_loppkuupaev>
          <a:kande_tekst></a:kande_tekst>
          <a:registriosa_nr>9999999</a:registriosa_nr>
          <a:jao_nr>5050</a:jao_nr>
          <a:kande_alus>&lt;root/&gt;</a:kande_alus>
          <a:kande_kehtivus>kehtiv</a:kande_kehtivus>
          <a:kande_nr>1</a:kande_nr>
          <a:koormatise_rahaline_vaartus>
          123,00</a:koormatise_rahaline_vaartus>
          <a:koormatise_rahalise_vaartuse_valuuta>
          EUR</a:koormatise_rahalise_vaartuse_valuuta>
          <a:oiguse_seisund>A</a:oiguse_seisund>
          <a:oiguse_seisund_tekst>Aktiivne</a:oiguse_seisund_tekst>
        </a:Jagu4>
      </jagu4>
      <kood>0</kood>
      <teade>OK</teade>
    </Kinnistu_DetailandmedResponse>
  </s:Body>
</s:Envelope>
")



(deftest joint-ownership-parsing
  (let [parsed-resp (-> joint-resp
                        string->zipped-xml
                        property-registry/kinnistu-d-parse-response)
        omandiosad (:omandiosad parsed-resp)]
    (is (= :ok (:status parsed-resp)))
    
    
    (is (= "nimi1" (-> omandiosad first :isik first :nimi)))
    (is (= "nimi2" (-> omandiosad first :isik second :nimi)))
    (is (= "nimi33" (-> omandiosad second :isik first :nimi)))))
