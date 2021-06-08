(ns teet.route-macros
  "Macros for defining the app structure"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [teet.authorization.authorization-check :refer [authorized?]]
            [clojure.set :as set]))

(defn read-route-defs []
  (read-string
   (slurp (io/resource "routes.edn"))))

(defmacro define-router [name]
  `(defonce ~name
     (bide.core/router
      [~@(for [[route-name {:keys [path]}] (read-route-defs)]
           `[~path ~route-name])])))

(defn- param-names [path]
  (map (comp keyword second) (re-seq #":([^/(]+)" path)))

(defn- breadcrumb-params
  "Extract all params in this route or any of its parents."
  [defs route-name]
  (when-let [{:keys [path parent]} (defs route-name)]
    (set/union (set (param-names path))
               (breadcrumb-params defs parent))))

(defn- breadcrumb-bindings
  "Return list of let bindings for all breadcrumb parameters."
  [defs route-name]
  (mapcat (fn [p]
            [(symbol p)
             `(or (get ~'params ~p)
                  (get-in ~'app [:route ~route-name :navigation ~p])

                  ;; If state is loaded, but no parameter value is available
                  ;; log error
                  (if ~'state
                    (teet.log/error "Missing route parameter: " ~p)
                    nil))])
          (breadcrumb-params defs route-name)))

(defn breadcrumbs [defs route-name keep-query-params]
  (when-let [{:keys [path parent crumb]} (get defs route-name)]
    (cons
     {:title crumb
      :page route-name
      :params (into {}
                    (for [n (param-names path)]
                      [n `(js/decodeURIComponent ~(symbol n))]))
      :query (if keep-query-params
               `(select-keys ~'query-params
                             ~keep-query-params)
               {})}
     (breadcrumbs defs parent keep-query-params))))


(defmacro define-main-page [fn-name]
  (let [defs (read-route-defs)]
    `(defn ~fn-name [~'e! {page# :page
                           params# :params
                           :as ~'app}]

       (let [~'params (:params ~'app)
             ~'query-params (:query ~'app)
             ~'tr teet.localization/tr
             ~'tr-enum teet.localization/tr-enum]
         (case page#
           ~@(mapcat
              (fn [[route-name {:keys [state view path skeleton permission keep-query-params title] :as route}]]
                [route-name
                 `(let [~'state (get-in ~'app [:route ~route-name])
                        ~@(breadcrumb-bindings defs route-name)
                        title# ~title
                        ~'refresh (get-in ~'app [:route ~(keyword (str (name route-name) "-refresh"))])]
                    (log/debug "defining route for title " title#)
                    (set! (.-title js/document) (or title# "TEET"))
                    (if-not (or (nil? ~permission) (authorized? @teet.app-state/user ~permission))
                      {:page [:div "No such page"]}
                      {:page ~(if state
                                ;; If page has needed state, wrap it in the query component to fetch it
                                `[teet.ui.query/query
                                  {:e! ~'e!
                                   :app (dissoc ~'app :route) ;; FIXME: select-keys
                                   :query ~(:query state)
                                   :args ~(:args state)
                                   :view ~view
                                   :skeleton ~skeleton
                                   :breadcrumbs
                                   ~(vec
                                     (reverse
                                      (breadcrumbs defs route-name keep-query-params)))
                                   :state ~'state
                                   :state-path [:route ~route-name]
                                   :refresh ~'refresh}]

                                ;; Otherwise just call view with e!, app
                                ;; and page state
                                `[~view ~'e! ~'app
                                  (get-in ~'app [:route ~route-name])])}))])
              defs)

           (let [err# (str "main-page: Unrecongnized page:" page#)]
             (log/debug err#)
             [:div err#]))))))

(def param-name-with-pattern #":([^(]+)(\(.+\))?")
(defn split-path [path]
  (map (fn [path-segment]
         (if (str/starts-with? path-segment ":")
           (let [[_ name _pattern] (re-matches param-name-with-pattern path-segment)]
             (symbol name))
           path-segment))
       (interleave
         (repeat "/")
         (str/split (subs path 1) #"/"))))

(defmacro define-url-functions
  "Define functions to generate URLs for all routes"
  []
  (let [defs (read-route-defs)]
    `(do
       ~@(for [[route-name {path :path}] defs
               :let [params (param-names path)
                     allowed-keys (set (conj params
                                             :teet.ui.url/query))
                     param-syms (map (comp symbol name) params)
                     fn-name (symbol (name route-name))]]
           (if (= 1 (count params))
             ;; exactly 1 parameter, check if it is map or value
             `(def ~fn-name
                (fn route# [~@param-syms]
                  (if (map? ~(first param-syms))
                    (do (assert (every? ~allowed-keys (keys ~(first param-syms)))
                                (str ~(name fn-name)
                                     ": Only allowed keys are "
                                     ~allowed-keys
                                     ", called with "
                                     ~(first param-syms)))
                        (str (route# (~(first params) ~(first param-syms)))
                             (when-let [query# (:teet.ui.url/query ~(first param-syms))]
                               (str "?" (teet.ui.url/format-params query#)))))
                    (str "#" ~@(split-path path)))))
             ;; more than 1 parameter, support 1 arity for map and
             ;; other for parameter count
             `(def ~fn-name
                (fn route#
                  ([{:keys [~@param-syms] query# :teet.ui.url/query :as params-map#}]
                   (assert (every? ~allowed-keys (keys params-map#))
                           (str ~(name fn-name)
                                ": Only allowed keys are "
                                ~allowed-keys
                                ", called with "
                                params-map#))
                   (str (route# ~@param-syms)
                        (when query#
                          (str "?" (teet.ui.url/format-params query#)))))
                  ([~@param-syms]
                   (str "#" ~@(split-path path)))))))
       (def ~'route-url-fns (hash-map
                             ~@(mapcat (fn [route-kw]
                                         [route-kw (symbol (name route-kw))])
                                       (keys defs)))))))
