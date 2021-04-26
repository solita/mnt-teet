(ns teet.integration.vektorio.vektorio-client
  "Wrapper for the Vektor.io HTTP API with minimal dependencies"
  (:require [teet.environment :as environment]
            [org.httpkit.client :as http]
            [clj-http.client :as clj-http]
            [cheshire.core :as cheshire]
            [teet.log :as log]))

(def vektorio-api-key-header-name "x-vektor-viewer-api-key")

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
                               (throw (ex-info "Failed to decode vektorio response"
                                               {:error :vektorio-response}))))]
    (log/info "Vektorio response " (assoc-in response [:opts :headers vektorio-api-key-header-name] "this is a secret 👀"))
    (if (request-success? (:status response))
      parsed-response
      (do
        (log/error "Vektorio error: " response)
        (throw (ex-info "Vektorio error"
                        {:msg "Vektorio error"
                         :vektorio-response response}))))))

(defn vektor-post!
  [{:keys [config api-key]} {:keys [endpoint payload headers]}]
  (let [{:keys [api-url]} config
        headers (merge {"x-vektor-viewer-api-key" api-key
                        "Content-Type" "application/json"}
                       headers)
        content-type (get headers "Content-Type")
        resp (try (clj-http/post (str api-url endpoint) ;; Using clj-http here because httpkit doesn't automatically use chunked encoding on streams
                                 {:headers headers
                                  :body (if (= content-type "application/octet-stream")
                                          payload
                                          (cheshire/encode payload))})
                  (catch Exception e
                    (log/fatal e "Exception in vektorio post for url " (str api-url endpoint))
                    (throw e)))]
    (vektor-message-handler resp)))

(defn vektor-delete!
  [{:keys [config api-key]} {:keys [endpoint]}]
  (let [{:keys [api-url]} config
        headers {"x-vektor-viewer-api-key" api-key}
        resp @(http/delete (str api-url endpoint)
                           {:headers headers})]
    (vektor-message-handler resp)))

(defn vektor-get
  [{:keys [config api-key]} endpoint]
  (let [{:keys [api-url]} config
        resp @(http/get (str api-url endpoint)
                          {:headers {vektorio-api-key-header-name api-key
                                     "Content-Type" "application/json"}})]
    (vektor-message-handler resp)))

(defn vektor-patch
  [{:keys [config api-key]} {:keys [endpoint payload headers]}]
  (let [{:keys [api-url]} config
        headers (merge {"x-vektor-viewer-api-key" api-key
                        "Content-Type" "application/json"}
                       headers)
        content-type (get headers "Content-Type")
        resp (try (clj-http/patch (str api-url endpoint) ;; Using clj-http here because httpkit doesn't automatically use chunked encoding on streams
                                 {:headers headers
                                  :body (if (= content-type "application/octet-stream")
                                          payload
                                          (cheshire/encode payload))})
                  (catch Exception e
                    (log/fatal e "Exception in vektorio PATCH for url " (str api-url endpoint))
                    (throw (ex-info "Vektorio error"
                                    {:msg "Vektorio error"
                                     :vektorio-response (ex-data e)}))))]
    (vektor-message-handler resp)))

(defn create-user!
  [vektor-conf {:keys [account name]}]
  (vektor-post! vektor-conf {:endpoint "users"
                             :payload {:account account :name name}}))

(def config (environment/config-value :vektorio))

(defn get-user-by-account
  [vektor-conf userid]
  (try
    (vektor-get vektor-conf (str "users/byAccount/" userid))
    (catch Exception e
      (if
        (= (get-in (ex-data e) [:vektorio-response :status]) 404)
        (do
          (log/info "User " userid " not found from Vektor.")
            nil)
        (throw e)))))

(defn create-project!
  [vektor-conf {:keys [name epsg epsg-x epsg-y]
                :or {epsg 3301
                     epsg-x 6587782.87
                     epsg-y 544077.31}                          ;;Estonian center coordinates
                }]
  (vektor-post! vektor-conf {:endpoint "projects"
                             :payload {:name name
                                       :epsg epsg
                                       :x epsg-x
                                       :y epsg-y}}))

(defn add-user-to-project!
  "Add the given user to the vektorio project"
  [conn user vektor-conf project-id user-id]
  (assert (some? project-id) "Must specify the project to which the user is added")
  (assert (some? user-id) "Must specify the user which is added to the project")
  (log/info "Adding vektorio user" user-id "to vektorio project:" project-id)
      (vektor-post! vektor-conf {:endpoint (str "projects/" project-id "/users")
                             :payload {:userId user-id}}))

(defn add-model-to-project!
  [vektor-conf {:keys [project-id model-file vektorio-filename vektorio-filepath]}]
  (log/info "Add model:" vektorio-filename "to project for project-id:" project-id)
  (vektor-post! vektor-conf {:endpoint (str "projects/" project-id "/models")
                             :headers {"x-viewer-api-model-filename" vektorio-filename
                                       "x-viewer-api-model-filepath" vektorio-filepath
                                       "Content-Type" "application/octet-stream"}
                             :payload model-file}))

(defn delete-model! [vektorio-config {:keys [vektorio/model-id vektorio/project-id]}]
  (assert (some? project-id))
  (assert (some? model-id))
  (vektor-delete! vektorio-config {:endpoint (str "projects/" project-id "/models/" model-id)}))

(defn instant-login
  [vektorio-conf {:keys [user-id]}]
  (let [resp (vektor-post! vektorio-conf {:endpoint (str "users/" user-id "/instantLogins")
                                          :headers {"Content-Type" "application/x-www-form-urlencoded"}})]
    resp))

(defn update-project! [vektorio-config vektorio-project-id project-name]
  (assert (some? vektorio-project-id))
  (assert (some? project-name))
  (log/info "Updating project in vektorio for project" vektorio-project-id project-name)
  (vektor-patch vektorio-config {:endpoint (str "projects/" vektorio-project-id)
                                 :payload {:name project-name}}))