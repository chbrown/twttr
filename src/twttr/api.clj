(ns twttr.api
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [twttr.middleware :refer [wrap-rest wrap-stream wrap-auth]]
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

(defn request-endpoint
  "Prepare and send an HTTP request to the Twitter API, signing with `credentials`
  (via OAuth as directed by the wrap-auth middleware), returning a deferred HTTP response.
  Options map:
  * :params - mapping from Endpoint :path placeholders to values
  * :query-params - additional query parameters"
  ([endpoint credentials]
   (request-endpoint endpoint credentials {}))
  ([endpoint credentials {:keys [params query-params]}]
   {:pre [(endpoints/Endpoint? endpoint)]}
   (let [{:keys [server-name request-method]} endpoint
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
          :query-params query-params
          :middleware middleware}
         (http/request)
         (d/catch clojure.lang.ExceptionInfo
                  (fn [ex]
                    (throw (ex-twitter (ex-data ex)))))))))

(doseq [endpoint endpoints/all]
  (intern *ns* (symbol (endpoints/name endpoint))
          (fn [credentials & {:as options}]
            (deref (request-endpoint endpoint credentials options)))))
