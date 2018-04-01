(ns twttr.api
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [twttr.middleware :refer [wrap-rest wrap-stream]]
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
                            (str "(#" (get first-error :code "N/A") ")")
                            (when (= 429 (:status response))
                              (str "Rate limit exceeded; next reset at "
                                   (get-in response [:headers :x-rate-limit-reset])
                                   " (UTC epoch seconds)"))
                            (get first-error :message first-error)
                            (:request body))]
    (ex-info (->> message-parts (remove nil?) (str/join " ")) response)))

(defn request
  [request-method url query-params authorization options]
  (-> options
      (assoc :request-method request-method :url url :query-params query-params)
      (assoc-in [:headers :Authorization] authorization)
      (http/request)
      (d/catch clojure.lang.ExceptionInfo
               (fn [ex]
                 (throw (ex-twitter (ex-data ex)))))
      (deref)))

(defn request-endpoint
  [endpoint credentials options]
  (let [request-method (:request-method endpoint)
        params (:params options)
        url (endpoints/uri endpoint params)
        query-params (merge (:query options) params)
        ; Prepare the HTTP request, signing with OAuth as directed by credentials
        authorization (auth-header credentials request-method url query-params)
        middleware (if (endpoints/streaming? endpoint)
                     wrap-stream
                     wrap-rest)]
    (request request-method url query-params authorization (assoc options :middleware middleware))))

(doseq [endpoint endpoints/all]
  (intern *ns* (symbol (endpoints/name endpoint))
          (fn [credentials & {:as options}]
            (request-endpoint endpoint credentials options))))
