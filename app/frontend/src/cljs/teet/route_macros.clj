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
       (case page#
         ~@(mapcat
            (fn [[route-name {:keys [state crumb parent view path]}]]
              [route-name
               `(let [{:keys [~@(param-names path)]} (:params ~'app)
                      ~'state (get-in ~'app [:route ~route-name])
                      ~'refresh (get-in ~'app [:route ~(keyword (str (name route-name) "-refresh"))])]
                  {:page ~(if state
                            `[teet.ui.query/query {:e! ~'e!
                                                   :app ~'app ;; FIXME: select-keys
                                                   :query ~(:query state)
                                                   :args ~(:args state)
                                                   :view ~view
                                                   :state ~'state
                                                   :state-path [:route ~route-name]
                                                   :refresh ~'refresh}]
                            `[~view ~'e! ~'app])})
               ])
            defs)

         [:div "Unrecognized page: " (str page#)]))))
