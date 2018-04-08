(ns twttr.api
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [twttr.middleware :refer [parse-body wrap-json wrap-stream wrap-auth]]))

(defmulti http-message
  "Format a human-readable message describing a HTTP response from the Twitter API.
  https://developer.twitter.com/en/docs/basics/response-codes"
  (fn [response] (:status response)))

(defmethod http-message 420
  [response]
  (str "Enhance Your Calm: "
       "the application is being rate limited for making too many requests. "
       (:body response)))

(defmethod http-message 429
  [response]
  ; the following *-time variables are java.lang.Long timestamps represented as epoch seconds
  (let [reset-time (Long/parseLong (get-in response [:headers "x-rate-limit-reset"]))
        now-time (quot (System/currentTimeMillis) 1000)
        reset-instant (java.time.Instant/ofEpochSecond reset-time)]
    (str "Too Many Requests: "
         "the application's rate limit has been exhausted for the requested resource. "
         "The rate limit will be reset in " (- reset-time now-time) " seconds, "
         "at " (str reset-instant))))

(defmethod http-message :default
  [response]
  (if-let [errors (get-in response [:body :errors])]
    (str/join "; " (map (fn [{:keys [message code]}] (format "%s (code=%d)" message code)) errors))
    (:body response)))

(defn- ex-twitter
  [response]
  (let [response (update response :body parse-body (:headers response))
        explanation (http-message response)
        msg (str "Twitter API Error: "
                 "HTTP " (:status response) " "
                 explanation)]
    (ex-info msg response)))

;; endpoint handling

(def endpoint-defaults
  {:scheme :https
   :request-method :get
   :server-name "api.twitter.com"
   :version "/1.1"
   :format :json})

(def endpoints
  (with-open [r (java.io.PushbackReader. (io/reader (io/resource "endpoints.edn")))]
    (for [endpoint (edn/read r)]
      (merge endpoint-defaults endpoint))))

(defn- params-seq
  "Return a sequence of the placeholders in `endpoint`'s :path, as keywords."
  ; (E.g., to distinguish querystring params from path params.)
  [path]
  (->> path (re-seq #":(\w+)") (map second) (map keyword)))

(defn- params-path-reducer
  "Replace named placeholder (`path-param`) in the pattern string `path`
  with the corresponding value from `params`, simultaneously removing it from `params`."
  [[params path] path-param]
  [(dissoc params path-param)
   (str/replace path (str path-param) (str (get params path-param)))])

(defn- prepare-request
  [endpoint params]
  (let [{:keys [request-method server-name version path format]} endpoint
        path-params (params-seq path)
        [params path] (reduce params-path-reducer [params path] path-params)
        ; Prepare the :uri value of a Ring request map from `endpoint`,
        ; adding an extension for :json requests
        uri (str version path (when (= format :json) ".json"))
        params-key (if (#{:post :put} request-method) :form-params :query-params)
        middleware (if (str/ends-with? server-name "stream.twitter.com") wrap-stream wrap-json)]
    (assoc endpoint
      :uri uri
      params-key params
      :middleware middleware)))

;; HTTP request

(defn request-endpoint
  "Prepare and send an HTTP request to the Twitter API, signing with `credentials`
  (via OAuth as directed by the wrap-auth middleware), returning a deferred HTTP response.
  Options map:
  * :params - mapping from :path placeholders to values,
              along with additional query parameters"
  ([endpoint credentials]
   (request-endpoint endpoint credentials {}))
  ([endpoint credentials {:keys [params]}]
   ; Prepare and send the HTTP request, signing with OAuth as directed by the wrap-auth middleware.
   (-> endpoint
       (prepare-request params)
       (update :middleware comp #(wrap-auth % credentials))
       (http/request)
       (d/catch clojure.lang.ExceptionInfo
                (fn [ex]
                  (throw (ex-twitter (ex-data ex))))))))

;; helper functions

(defn- path->name
  "chop off the leading '/' and replace all non-word characters with '-'s"
  [path]
  (-> path (subs 1) (str/replace #"[^a-zA-Z]+" "-")))

(doseq [endpoint endpoints]
  (intern *ns* (symbol (or (:name endpoint) (path->name (:path endpoint))))
          (fn [credentials & {:as options}]
            (deref (request-endpoint endpoint credentials options)))))
