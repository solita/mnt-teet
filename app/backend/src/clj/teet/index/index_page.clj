(ns teet.index.index-page
  "Index page for TEET"
  (:require [hiccup.core :as hiccup]
            [teet.util.build-info :as build-info]
            [clojure.java.io :as io]))

(defn index-page
  "DEV MODE INDEX PAGE.

  If you add things here, also add them to index.html in frontend for production env."
  [{:keys [base-url mode]}]
  (let [dev? (= mode :dev)]
    [:html {:style "overflow-y: scroll;"}
     [:head
      [:meta {:charset "UTF-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      ;; Specify base-url if provided (local env needs this)
      (when base-url
        [:base {:href base-url}])

      ;; Fonts to support Material Design
      [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/css?family=Roboto:300,400,500,700&display=swap"}]
      ;; Icons to support Material Design
      [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/icon?family=Material+Icons"}]

      ;; Custom font
      [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/css?family=Roboto+Condensed&display=swap"}]

      [:script {:type "text/javascript"}
       (str
        "window.teet_authz = \""
        (-> "authorization.edn" io/resource slurp)
        "\";\n"

        "new Promise((resolve, reject) => {
        window.resolveOnload = resolve;
     })
     .then(() => { teet.main.main(); });")]]

     [:body {:data-git-version (build-info/git-commit)
             :onload "resolveOnload()"}
      [:div#teet-frontend]
      [:script {:src (if dev? "cljs-out/dev-main.js" "main.js")}]

      [:script (if dev?
                 {:src "https://unpkg.com/@material-ui/core@latest/umd/material-ui.development.js"
                  :crossorigin "anonymous"}
                 {:src "material-ui.production.min.js"})]]]))

(defn index-route [config]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (str "<!DOCTYPE html>\n"
              (hiccup/html (index-page config)))})
