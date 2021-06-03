(ns teet.map.openlayers.layer
  "Määrittelee karttatason kaltaisen protokollan")

(defprotocol Layer
  (set-z-index [this z-index]
                 "Palauttaa uuden version tasosta, jossa z-index on asetettu")
  (extent [this] "Palauttaa tason geometrioiden extentin [minx miny maxx maxy]")
  (opacity [this]
           "Palauttaa tason opacityn 0 (täysin läpinäkyvä) - 1 välillä (ei läpinäkyvä lainkaan)")
  (selitteet [this] "Palauttaa tällä tasolla olevien asioiden selitteet")
  (aktiivinen? [this] "Tason arvio siitä, sisältääkö taso asioita vai ei.")
  (paivita
   [this ol3 ol-layer aiempi-paivitystieto]
   "Päivitä ol-layer tai luo uusi layer. Tulee palauttaa vektori, jossa on
ol3 Layer objekti ja tälle tasolle spesifinen päivitystieto. Palautettu
päivitystieto annettaan seuraavalla kerralla aiempi-paivitystieto
parametrina takaisin. Jos päivitys luo uuden ol layerin, tulee
sen lisätä se itse ol3 karttaan (addLayer)")

  (hae-asiat-pisteessa [this koordinaatti extent]
    "Hakee asiat annetulle klikkauspisteelle. Palauttaa kanavan, josta löytyneet
    asiat voi lukea. Koordinaatti annetaan [x y] vektorina.
    Extent on nykyisen kartan näkyvä koordinaattialue [x1 y1 x2 y2] vektorina."))

;; Wrapper raa'alle ol.layer tasolle joka toteuttaa Taso protokollan
(defrecord OpenLayersTaso [layer on-update]
  Layer
  (set-z-index [this z-index]
    (.setZIndex layer z-index)
    this)
  (extent [this] nil)
  (aktiivinen? [this] true)
  (opacity [this] 1)
  (selitteet [this] [])
  (paivita [this ol3 ol-layer aiempi-paivitystieto]
    (.log js/console "PÄIVITÄ OPENLAYERS TASO " this)
    (cond
      (nil? aiempi-paivitystieto)
      (do (.log js/console " -> LISÄTÄÄN LAYER")
          (.addLayer ol3 layer))

      (not= ol-layer layer)
      (do (js/console.log " -> VAIHDETAAN LAYER")
          (.removeLayer ol3 ol-layer)
          (.addLayer ol3 layer)))
    (when on-update
      (on-update ol3 layer))
    [layer :ok]))

(defn ol-layer
  "Create raw OpenLayers Layer that implements the Taso protocol.
  Optionally takes an on-update fn that is called when the layer is
  updated. The on-update takes 2 arguments: the ol3 map instance and
  the layer instance."
  ([layer]
   (ol-layer layer nil))
  ([layer on-update]
   (->OpenLayersTaso layer on-update)))
