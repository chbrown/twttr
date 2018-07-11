(ns twttr.api
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [twttr.middleware :refer [parse-body wrap-body wrap-auth]]))

(defmulti http-message
  "Format a human-readable message describing a HTTP response from the Twitter API.
  https://developer.twitter.com/en/docs/basics/response-codes"
  :status)

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
   :format :json
   :middleware wrap-body})

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
  (let [{:keys [request-method version path format]} endpoint
        path-params (params-seq path)
        [params path] (reduce params-path-reducer [params path] path-params)
        ; Prepare the :uri value of a Ring request map from `endpoint`,
        ; adding an extension for :json requests
        uri (str version path (when (= format :json) ".json"))
        params-key (if (#{:post :put} request-method) :form-params :query-params)]
    (assoc endpoint
      :uri uri
      params-key params)))

;; HTTP request

(defn request-endpoint
  "Prepare and send an HTTP request to the Twitter API, signing with `credentials`
  (via OAuth as directed by the wrap-auth middleware), returning a deferred HTTP response.
  The `request-options` map is merged on top of `endpoint`, allowing custom middleware
  or paths to be set directly, with the exception of :params, which is treating specially.
  (:params request-options) is a mapping from :path placeholders to values, along with
  additional query/form parameters, which are auto-adapted based on the request-method."
  ([endpoint credentials]
   (request-endpoint endpoint credentials {}))
  ([endpoint credentials {:keys [params] :as request-options}]
   ; Prepare and send the HTTP request, signing with OAuth as directed by the wrap-auth middleware.
   (-> (merge endpoint (dissoc request-options :params))
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
  (let [url (str (name (:scheme endpoint)) "://" (:server-name endpoint)
                 (:version endpoint) (:path endpoint) (when (= (:format endpoint) :json) ".json"))
        method (str/upper-case (name (:request-method endpoint)))
        doc (str "Call the Twitter endpoint: " method " " url
                 "\n  adding `options-map` to the request and signing with `credentials`.")
        metadata {:arglists '([credentials] [credentials & {:as options-map}]) :doc doc}
        name (or (:name endpoint) (path->name (:path endpoint)))]
    (intern *ns* (with-meta (symbol name) metadata)
            (fn [credentials & {:as options}]
              (deref (request-endpoint endpoint credentials options))))))
