(ns twttr.api
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [twttr.middleware :refer [wrap-rest wrap-stream wrap-auth]]
            [twttr.endpoints :as endpoints]))

(defn- parse-body
  "Parse the HTTP response `body` as JSON or a string, depending on the 'content-type' header"
  [body headers]
  (let [{:strs [content-type]} headers]
    (if (str/starts-with? content-type "application/json")
      (json/read (io/reader body) :key-fn keyword :eof-error? false)
      (str/trim (slurp body)))))

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

(defn request-endpoint
  "Prepare and send an HTTP request to the Twitter API, signing with `credentials`
  (via OAuth as directed by the wrap-auth middleware), returning a deferred HTTP response.
  Options map:
  * :params - mapping from Endpoint :path placeholders to values,
              along with additional query parameters"
  ([endpoint credentials]
   (request-endpoint endpoint credentials {}))
  ([endpoint credentials {:keys [params]}]
   {:pre [(endpoints/Endpoint? endpoint)]}
   (let [{:keys [server-name request-method]} endpoint
         params-key (if (= request-method :post) :form-params :query-params)
         non-path-params (apply dissoc params (endpoints/path-placeholders endpoint))
         wrap-body (if (endpoints/streaming? endpoint) wrap-stream wrap-rest)
         middleware (fn [handler] (-> handler (wrap-auth credentials) wrap-body))]
     ; Prepare and send the HTTP request, signing with OAuth as directed by the wrap-auth middleware.
     ; we cannot add fields to #twttr.endpoints.Endpoint{...} to flesh out a ring request,
     ; since aleph expects (ring) requests that are plain maps, and thus work as functions,
     ; which records do not (for... reasons)
     (-> {:request-method request-method
          :scheme :https
          :server-name server-name
          :uri (endpoints/uri endpoint params)
          params-key non-path-params
          :middleware middleware}
         (http/request)
         (d/catch clojure.lang.ExceptionInfo
                  (fn [ex]
                    (throw (ex-twitter (ex-data ex)))))))))

(doseq [endpoint endpoints/all]
  (intern *ns* (symbol (endpoints/name endpoint))
          (fn [credentials & {:as options}]
            (deref (request-endpoint endpoint credentials options)))))
