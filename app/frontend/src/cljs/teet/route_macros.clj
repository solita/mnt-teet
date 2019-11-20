(ns teet.route-macros
  "Macros for defining the app structure"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn read-route-defs []
  (read-string
   (slurp (io/resource "routes.edn"))))

(defmacro define-router [name]
  `(defonce ~name
     (bide.core/router
      [~@(for [[route-name {:keys [path]}] (read-route-defs)]
           `[~path ~route-name])])))

(defn- param-names [path]
  (map (comp keyword second) (re-seq #":([^/]+)" path)))

(defmacro define-main-page [fn-name]
  (let [defs (read-route-defs)]
    `(defn ~fn-name [~'e! {page# :page
                           params# :params
                           :as ~'app}]
       (let [~'params (:params ~'app)
             ~'query-params (:query ~'app)]
         (case page#
           ~@(mapcat
              (fn [[route-name {:keys [state view path skeleton role keep-query-params] :as route}]]
                [route-name
                 `(let [{:keys [~@(param-names path)]} ~'params
                        ~'state (get-in ~'app [:route ~route-name])
                        ~'refresh (get-in ~'app [:route ~(keyword (str (name route-name) "-refresh"))])]
                    (if-not (teet.user.user-controller/has-role? ~role)
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
                                   :breadcrumbs [~@(loop [crumbs (list) ;; Autogenerate breadcrumbs based on :parent links for route
                                                          route-name route-name
                                                          {crumb :crumb
                                                           parent :parent
                                                           path :path} route]
                                                     (if-not crumb
                                                       crumbs
                                                       (recur (conj crumbs
                                                                    {:title crumb
                                                                     :page route-name
                                                                     :params (into {}
                                                                                   (for [n (param-names path)]
                                                                                     [n `(get ~'params ~n)]))
                                                                     ;; FIXME: query params from :keep-query-params
                                                                     :query (if keep-query-params
                                                                              `(select-keys ~'query-params
                                                                                            ~keep-query-params)
                                                                              {})})
                                                              parent
                                                              (get defs parent))))]
                                   :state ~'state
                                   :state-path [:route ~route-name]
                                   :refresh ~'refresh}]

                                ;; Otherwise just call view with e! and app
                                `[~view ~'e! ~'app])}))])
              defs)

           [:div "Unrecognized page: " (str page#)])))))

(def path-split-pattern #"([^:]+)((:[^/]+)(.*))?")
(defn split-path [path]
  (loop [acc []
         path path]
    (if (str/blank? path)
      acc
      (let [[_ before _ param after] (re-matches path-split-pattern path)]
        (if param
          (recur (into acc [before (symbol (subs param 1))])
                 after)
          (conj acc path))))))

(defmacro define-url-functions
  "Define functions to generate URLs for all routes"
  []
  (let [defs (read-route-defs)]
    `(do
       ~@(for [[route-name {path :path}] defs
               :let [params (param-names path)
                     param-syms (map (comp symbol name) params)
                     fn-name (symbol (name route-name))]]
           (case (count params)
             0 `(defn ~fn-name [] ~(str "#" path))
             1 `(defn ~fn-name [~@param-syms]
                  (if (map? ~(first param-syms))
                    (~fn-name (~(first params) ~(first param-syms)))
                    (str "#" ~@(split-path path))))
             ;; more than 1 parameter
             `(defn ~fn-name
                ([{:keys [~@param-syms]}]
                 (~fn-name ~@param-syms))
                ([~@param-syms]
                 (str "#" ~@(split-path path)))))))))
