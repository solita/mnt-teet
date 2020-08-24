(ns teet.map.map-services
  "Client code to call WFS/WMS services"
  (:require [org.httpkit.client :as client]
            [clojure.string :as str]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as z]
            [hiccup.core :as hiccup]
            hiccup.compiler
            [teet.log :as log]))

(defn unexceptional-xml-parse [input]
  (try
    (xml/parse input)
    (catch Exception _
      ;; caller has to log error about input resulting in nil parse
      nil)))

(defn ogc-filter [content]
  (hiccup/html
   [:Filter {:xmlns "http://www.opengis.net/ogc"
             :xmlns:gml "http://www.opengis.net/gml"}
    content]))

(defn- raw [xml]
  (reify hiccup.compiler/HtmlRenderer
    (render-html [_] xml)))

(defn query-by-values [values]
  (into [:And]
        (map (fn [[key value]]
               (let [[op value] (if (vector? value)
                                  [(case (first value)
                                     :> :PropertyIsGreaterThan
                                     :>= :PropertyIsGreaterThanOrEqualTo
                                     :< :PropertyIsLessThan
                                     :<= :PropertyIsLessThanOrEqualTo
                                     := :PropertyIsEqualTo)
                                   (second value)]
                                  [:PropertyIsEqualTo value])]
                 [op
                  [:PropertyName (if (keyword? key)
                                   (name key) key)]
                  [:Literal value]])))
        values))

(defn query-by-coordinate [[x y] distance-m]
  (ogc-filter
   [:DWithin
    [:PropertyName "msGeometry"]
    [:Point {:xmlns "http://www.opengis.net/gml"}
     [:coordinates {:decimal "." :cs "," :ts " "}
      (str y "," x)]]
    [:Distance {:units "meter"} distance-m]]))


(defn read-feature-collection
  [feature-collection typename parse-feature]
  (sort-by
   :sequence-nr
   (z/xml->
    feature-collection
    :gml:featureMember
    (keyword typename)
    parse-feature)))

(declare parse-feature)

(defn handle-wfs-response [wfs-url typename request-delay custom-parse-feature]
  (let [{:keys [error body] :as response} @request-delay]
    (if error
      (throw (ex-info "Unable to fetch road parts from WFS"
                      {:status 500
                       :error :wfs-request-failed
                       :wfs-url wfs-url}
                      error))
      (if-let [zx (some-> body unexceptional-xml-parse zip/xml-zip)]
        (read-feature-collection zx
                                 typename
                                 (or custom-parse-feature parse-feature))
        (throw (ex-info "WFS response error"
                        {:status 500
                         :error :wfs-request-failed
                         :wfs-url wfs-url
                         :response response}))))))

(defn- cached [cache-atom cache-key fetch-fn]
  (if-let [cached-value (and cache-atom
                             (get @cache-atom cache-key))]
    cached-value
    (let [fetched-value (fetch-fn cache-key)]
      (when cache-atom
        (swap! cache-atom assoc cache-key fetched-value))
      fetched-value)))

(defn wfs-request [{:keys [cache-atom wfs-url]} query-params]
  (cached
   cache-atom
   query-params
   (fn [{custom-parse-feature ::parse-feature :as  query-params}]
     (let [query-params (merge {:SERVICE "WFS"
                                :REQUEST "GetFeature"
                                :VERSION "1.1.0"
                                :TYPENAME "ms:teeosa"
                                :SRSNAME "urn:ogc:def:crs:EPSG::3301"}
                               (dissoc query-params ::parse-feature))
           request-delay (client/get wfs-url
                                     {:connect-timeout 10000
                                      :query-params query-params
                                      :as :stream})]
       (handle-wfs-response wfs-url (:TYPENAME query-params)
                            request-delay (or custom-parse-feature parse-feature))))))

(defn wfs-get-feature [{:keys [wfs-url]} typename ogc-filter]
  (let [payload [:wfs:GetFeature {:xmlns:wfs "http://www.opengis.net/wfs"
                                  :xmlns:gml "http://www.opengis.net/gml"
                                  :version "1.1.0"
                                  :service "WFS"}
                 [:wfs:Query {:typeName typename}
                  [:Filter {:xmlns "http://www.opengis.net/ogc"}
                   ogc-filter]]]
        payload-xml (str "<?xml version=\"1.0\"?>\n"
                         (hiccup/html payload))
        request-delay (client/post wfs-url
                                   {:as :stream
                                    :body payload-xml
                                    :headers {"Content-Type" "text/xml"}})]
    (handle-wfs-response wfs-url typename request-delay parse-feature)))

(defn- flipc [[c1 c2]]
  [c2 c1])

(defn- split-positions [pos-list-str]
  (partition 2
             (map #(Double/parseDouble %)
                  (str/split pos-list-str #" "))))

(defn- gml->geojson [node]
  (or (z/xml1-> node :gml:LineString :gml:posList z/text
                (fn [positions]
                  {:type "LineString"
                   :coordinates
                   (mapv flipc (split-positions positions))}))
      (z/xml1-> node :gml:Point :gml:pos z/text
                (fn [pos]
                  {:type "Point"
                   :coordinates (flipc (first (split-positions pos)))}))
      (do
        (log/warn "Unknown geometry from Teeregister: "
                  (zip/node node))
        nil)))

(defn parse-feature
  "Parse any WFS feature member"
  [f]
  (into {:geometry (z/xml1-> f :ms:msGeometry gml->geojson)}
        (keep (fn [child]
                (let [{:keys [tag content]} (zip/node child)
                      tag-name (name tag)]
                  ;; Teeregister fields, take the content value
                  (when (and (not= tag :ms:msGeometry)
                             (str/starts-with? tag-name "ms:"))
                    [tag (str/join " " content)]))))
        (dz/children f)))

(defn fetch-intersecting-objects-of-type
  "Fetch road objects for given typename intersecting the given area."
  [config typename gml-geometry]
  (wfs-get-feature config
                   typename
                   [:Intersects
                    [:PropertyName "msGeometry"]
                    (raw gml-geometry)]))

(defn fetch-capabilities [url service]
  (log/info "fetch" service "capabilities from" url)
  (let [{:keys [status body] :as response}
        @(client/get url
                     {:as :stream
                      :query-params {:version "1.3.0"
                                     :request "GetCapabilities"
                                     :service service}})]
    (if (not= status 200)
      (throw (ex-info "Unable to fetch WMS capabilities"
                      {:url url
                       :service service
                       :response response}))
      (if-let [parsed (unexceptional-xml-parse body)]
        (zip/xml-zip parsed)
        (do
          (log/error "couldn't parse WMS capabilities xml:" body)
          (throw (ex-info "Unable parse capability query response from WFS"
                          {:url url
                           :service service
                           :response response})))))))

(defn fetch-wms-capabilities
  "Fetch WMS capabilities XML. Returns XML zipper."
  [{wms-url :wms-url}]
  (fetch-capabilities wms-url "WMS"))

(defn fetch-wfs-capabilities
  "Fetch WFS capabilities XML. Returns XML zipper."
  [{wfs-url :wfs-url}]
  (fetch-capabilities wfs-url "WFS"))

(defn- text-of [node child-element]
  (z/xml1-> node child-element z/text))

(defn- parse-wms-layer [node]
  (let [name (text-of node :Name)
        title (text-of node :Title)]
    {:name name
     :title title
     :queryable? (= "1" (z/xml1-> node (z/attr :queryable)))
     :legend-url (z/xml1-> node :Style :LegendURL :OnlineResource (z/attr :xlink:href))
     :layers (z/xml-> node dz/children :Layer parse-wms-layer)}))

(defn fetch-wms-layers [config]
  (let [c (fetch-wms-capabilities config)]
    (z/xml-> c :Capability :Layer parse-wms-layer)))

(defn- parse-wfs-feature-type [ft]
  {:name (text-of ft :Name)
   :title (text-of ft :Title)})

(defn fetch-wfs-feature-types [config]
  (let [c (fetch-wfs-capabilities config)]
    (z/xml-> c :FeatureTypeList :FeatureType parse-wfs-feature-type)))
