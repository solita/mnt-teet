(ns teet.route-macros
  "Macros for defining the app structure"
  (:require [clojure.java.io :as io]))

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
       (let [~'params (:params ~'app)]
         (case page#
           ~@(mapcat
              (fn [[route-name {:keys [state view path role] :as route}]]
                [route-name
                 `(let [{:keys [~@(param-names path)]} ~'params
                        ~'state (get-in ~'app [:route ~route-name])
                        ~'refresh (get-in ~'app [:route ~(keyword (str (name route-name) "-refresh"))])]
                    (if-not (teet.user.user-controller/has-role? ~role)
                      {:page [:div "No such page"]}
                      {:page ~(if state
                                ;; If page has needed state, wrap it in the query component to fetch it
                                `[teet.ui.query/query {:e! ~'e!
                                                       :app (dissoc ~'app :route) ;; FIXME: select-keys
                                                       :query ~(:query state)
                                                       :args ~(:args state)
                                                       :view ~view
                                                       :breadcrumbs [~@(loop [crumbs (list) ;; Autogenerate breadcrumbs based on :parent links for route
                                                                              route-name route-name
                                                                              {crumb :crumb
                                                                               parent :parent
                                                                               path :path} route]
                                                                         (if-not crumb
                                                                           crumbs
                                                                           (recur (conj crumbs {:title crumb
                                                                                                :page route-name
                                                                                                :params (into {}
                                                                                                              (for [n (param-names path)]
                                                                                                                [n `(get ~'params ~n)]))})
                                                                                  parent
                                                                                  (get defs parent))))]
                                                       :state ~'state
                                                       :state-path [:route ~route-name]
                                                       :refresh ~'refresh}]

                                ;; Otherwise just call view with e! and app
                                `[~view ~'e! ~'app])}))])
              defs)

           [:div "Unrecognized page: " (str page#)])))))
