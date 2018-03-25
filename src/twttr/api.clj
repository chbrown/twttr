(ns twttr.api
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [aleph.http :as http]
            [byte-streams :as bs]
            [manifold.deferred :as d]
            [twttr.endpoints :as endpoints]
            [twttr.auth :refer [auth-header]]))

(defn- ex-twitter
  "read an error response into a string error message"
  [response]
  (let [response (update response :body slurp)
        body (try
               (json/read-str (:body response) :key-fn keyword)
               ; java.lang.Exception: JSON error
               (catch Exception _
                 (:body response)))
        first-error (or (first (:errors body)) (:error body))
        message-parts (list "Twitter API error response"
                            (str "(#" (or (:code first-error) "N/A") ")")
                            (when (= 429 (:status response)) ; rate limit exceeeded
                              (str "Rate limit exceeded; next reset at "
                                   (get-in response [:headers :x-rate-limit-reset])
                                   " (UTC epoch seconds)"))
                            (get first-error :message first-error)
                            (:request body))]
    (ex-info (->> message-parts (remove nil?) (str/join " ")) response)))

(defn request
  "the argument to middleware should be a function that takes a handler
  (which turns a request into a response), and returns a function which takes a request,
  potentially modifies that request, runs request through the given handler to get a response,
  and then returns the response, potentially after modifying it."
  [request-method url query-params authorization options]
  (-> options
      (assoc :request-method request-method :url url :query-params query-params)
      (assoc-in [:headers :Authorization] authorization)
      (http/request)
      (d/catch clojure.lang.ExceptionInfo
               (fn [ex]
                 (throw (ex-twitter (ex-data ex)))))
      (deref)))

(defn- wrap-rest-middleware
  "REST middleware for parsing response body as single JSON document"
  [handler]
  (fn rest-middleware-handler [request]
    (d/chain (handler request)
             (fn [response]
               (with-meta (-> (:body response)
                              (io/reader)
                              (json/read :key-fn keyword :eof-error? false)) response)))))

(defn- wrap-stream-middleware
  "Streaming middleware for parsing response as infinite sequence of JSON documents,
  separated by newlines"
  [handler]
  (fn stream-middleware-handler [request]
    (d/chain (handler request)
             (fn [response]
               (with-meta (->> (:body response)
                               (bs/to-line-seq)
                               ; Twitter will send lots of newlines as keep-alive signals if the stream is sparse
                               (remove empty?)
                               (map #(json/read-str % :key-fn keyword))) response)))))

(defn request-endpoint
  [endpoint credentials options]
  (let [request-method (:request-method endpoint)
        params (:params options)
        url (endpoints/uri endpoint params)
        query-params (merge (:query options) params)
        ; Prepare the HTTP request, signing with OAuth as directed by credentials
        authorization (auth-header credentials request-method url query-params)
        middleware (if (endpoints/streaming? endpoint)
                     wrap-stream-middleware
                     wrap-rest-middleware)]
    (request request-method url query-params authorization (assoc options :middleware middleware))))

(doseq [endpoint endpoints/all]
  (intern *ns* (symbol (endpoints/name endpoint))
          (fn [credentials & {:as options}]
            (request-endpoint endpoint credentials options))))
