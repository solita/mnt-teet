(ns teet.ui.url
  "Define functions to generate URLs and links for any route"
  (:require [clojure.string :as str]
            [teet.ui.context :as context]
            teet.ui.material-ui
            [teet.log :as log])
  (:require-macros [teet.route-macros :refer [define-url-functions]]))


(defn with-params [url & param-names-and-values]
  (str url "?" (str/join "&"
                         (map (fn [[param-name param-value]]
                                (str (name param-name) "=" (js/encodeURIComponent (str param-value))))
                              (partition 2 param-names-and-values)))))

(defn- format-params
  [params]
  (str/join "&"
            (keep (fn [[param-name param-value]]
                    (when param-value
                      (str (name param-name) "=" (js/encodeURIComponent (str param-value)))))
                  params)))

;; This defines functions for generating URLs to routes
;; For example a route named :file in routes.edn will generate
;; function file in this namespace.
;;
;; If the route has path parameters, the function will take arguments.
;; The path parameters can be specified as a map or separate arguments
;; in the same order as they appear in the path.
;;
;; In the map variant, if any parameter is omitted, the value is taken
;; from the current navigation info, so there is no need to pass all
;; parameters.
(declare route-url-fns)
(define-url-functions)

(def
  ^:private
  path-and-params-pattern
  "Regex pattern to split URL hash into path and parameters."
  #"([^\?]+)(\?.*)?$")

(def
  ^:private
  param-name-and-value-pattern
  "Regex pattern to extract parameter names and values."
  #"([\w-]+)=([^&]*)")

(defn- parse-hash [hash]
  (let [[_ path params-part] (re-matches path-and-params-pattern hash)
        params (when params-part
                 (re-seq param-name-and-value-pattern params-part))]
    {:path path
     :params (into {}
                   (map (fn [[_ param-name param-value]]
                          [(keyword param-name) (js/decodeURIComponent param-value)]))
                   params)}))

(defn remove-params
  []
  (let [[_ path _] (re-matches path-and-params-pattern js/window.location.hash)]
    path))

(defn remove-query-param
  [query-param-key]
  (let [hash js/window.location.hash
        {:keys [path params]} (parse-hash hash)
        params (dissoc params query-param-key)]
    (str path "?" (format-params params))))

(defn set-query-param
  [& param-names-and-values]
  (let [hash js/window.location.hash
        {:keys [path params]} (parse-hash hash)
        params (into params (map vec) (partition 2 param-names-and-values))]
    (str path "?" (format-params params))))

(defn provide-navigation-info
  "Provide navigation info map as context to child components.
  The navigation context contains :page, :params and :query and is
  used when generating URL links to routes."
  [context child]
  (context/provide :navigation-info context child))

(defn consume-navigation-info
  "Consume navigation info. Takes navigation info from context
  and calls component with the context provided value."
  [component-fn]
  (context/consume :navigation-info component-fn))

(defn Link
  "Convenience component to create Material UI Link to given page with params.
  Missing path parameters are filled from navigation context."
  [{:keys [page params query class component]
    :or {component teet.ui.material-ui/Link}}
   content]
  [consume-navigation-info
   (fn [{context-params :params}]
     (let [url-fn (route-url-fns page)
           url-fn-params (merge context-params params
                                (when query
                                  {::query query}))]
       (when (nil? url-fn)
         (log/error "No such page:" page))
       [component
        (do
          (log/debug "calling url-fn for" page "with params" url-fn-params "  - context-params was" context-params)
          (merge {:href (url-fn url-fn-params)}
                 (when class
                   {:class class})))
        content]))])


