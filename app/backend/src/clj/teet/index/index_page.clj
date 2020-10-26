(ns teet.index.index-page
  "Index page for TEET"
  (:require [hiccup.core :as hiccup]
            [teet.util.build-info :as build-info]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn index-page
  "DEV MODE INDEX PAGE.

  If you add things here, also add them to index.html in frontend for production env."
  [{:keys [base-url mode]}]
  (let [dev? (= mode :dev)]
    [:html {:style "overflow-y: scroll;"
            :lang "et"}
     [:head
      [:meta {:charset "UTF-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      ;; Specify base-url if provided (local env needs this)
      (when base-url
        [:base {:href base-url}])

      ;; Fonts to support Material Design
      [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/css?family=Roboto:300,400,500,700&display=swap"}]
      ;; Icons to support Material Design
      [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/icon?family=Material+Icons|Material+Icons+Outlined"}]
      [:link {:rel "icon" :type "image/png" :sizes "32x32" :href "/img/favicon-32x32.png"}]
      [:link {:rel "icon" :type "image/png" :sizes "96x96" :href "/img/favicon-96x96.png"}]
      [:link {:rel "icon" :type "image/png" :sizes "16x16" :href "/img/favicon-16x16.png"}]

      ;; Custom font
      [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/css?family=Roboto+Condensed&display=swap"}]

      [:script {:type "text/javascript"}
       (str
        "window.teet_authz = \""
        (-> "authorization.edn" io/resource slurp (str/replace #"\n" " "))
        "\";\n"

        "new Promise((resolve, reject) => {
        window.resolveOnload = resolve;
     })
     .then(() => { teet.main.main(); });")]]

     [:body {:data-git-version (build-info/git-commit)
             :onload "resolveOnload()"}
      [:div#teet-frontend]
      [:script {:src "out/main.js"}]]]))

(defn index-route [config]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (str "<!DOCTYPE html>\n"
              (hiccup/html (index-page config)))})
