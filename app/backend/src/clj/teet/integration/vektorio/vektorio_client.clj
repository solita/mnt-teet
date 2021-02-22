(ns teet.integration.vektorio.vektorio-client
  (:require [teet.environment :as environment]
            [org.httpkit.client :as client]
            [cheshire.core :as cheshire]
            [teet.log :as log]
            [teet.db-api.core :as db-api]))


(def user-id 15)                                            ;;; TODO REMOVE THIS

(def project-id 36)                                         ;;TODO

(def succes-statuses
  #{200 201 204 203})

(defn request-success?
  [status]
  (boolean
    (succes-statuses status)))

(defn vektor-message-handler
  [response]
  (let [parsed-response (try (cheshire/decode (:body response) keyword)
                             (catch Exception _
                               (log/error "Failed to decode Vektorio response: " (:body response))
                               (db-api/fail! {:msg "Failed to decode vektorio response"
                                              :error :vektorio-response})))]
    (log/info "Vektorio response " response)
    (if (request-success? (:status response))
      parsed-response
      (do
        (log/error "Vektorio error: " response)
        (db-api/fail! {:msg "Vektorio error"
                       :response response})))))

(defn vektor-post!
  [{:keys [config api-key]} {:keys [endpoint payload headers]}]
  (let [{:keys [api-url]} config
        headers (merge {"x-vektor-viewer-api-key" api-key
                        "Content-Type" "application/json"}
                       headers)
        content-type (get headers "Content-Type")
        resp @(client/post (str api-url endpoint)
                           {:headers (merge {"x-vektor-viewer-api-key" api-key
                                             "Content-Type" "application/json"}
                                            headers)
                            :body (if (= content-type "application/octet-stream")
                                    payload
                                    (cheshire/encode payload))})]
    (vektor-message-handler resp)))

(defn vektor-get!
  [{:keys [config api-key]} endpoint]
  (let [{:keys [api-url]} config
        resp @(client/get (str api-url endpoint)
                          {:headers {"x-vektor-viewer-api-key" api-key
                                     "Content-Type" "application/json"}})]
    (vektor-message-handler resp)))

(defn create-user!
  [vektor-conf {:keys [account name]}]
  (let [resp (vektor-post! vektor-conf {:endpoint "users"
                                        :payload {:account account :name name}})]
    resp))

(def config (environment/config-value :vektorio))

(defn get-user-by-account!
  [vektor-conf email]
  (let [resp (vektor-get! vektor-conf (str "users/byAccount/" email))]
    resp))

(defn create-project!
  [vektor-conf {:keys [name lat long]
                :or {lat 58.5953
                     long 25.0136}                          ;;Estonian center coordinates
                }]
  (let [resp (vektor-post! vektor-conf {:endpoint "projects"
                                        :payload {:name name
                                                  :latitude lat
                                                  :longitude long}})]
    (println "resp: " resp)
    resp))

(defn add-user-to-project!
  [vektor-conf {:keys [project-id user-id]}]
  (let [resp (vektor-post! vektor-conf {:endpoint (str "projects/" project-id "/users")
                                        :payload {:userId user-id}})]
    resp))

(defn add-model-to-project!
  [vektor-conf {:keys [project-id model-file vektorio-filename vektorio-filepath]}]
  (let [resp (vektor-post! vektor-conf {:endpoint (str "projects/" project-id "/models")
                                        :headers {"x-viewer-api-model-filename" vektorio-filename
                                                  "x-viewer-api-model-filepath" vektorio-filepath
                                                  "Content-Type" "application/octet-stream"}
                                        :payload model-file})]

    (println "RESPONSE FROM VEKTO FILE POST: " resp)
    ;; TODO Assert that this succeeded

    resp))

