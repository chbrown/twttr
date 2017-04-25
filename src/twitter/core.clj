(ns twitter.core
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.data.codec.base64 :as base64]
            [http.async.client :as http]
            [http.async.client.request :as http-request]
            [oauth.client :as oauth]
            [oauth.signature :refer [url-encode]]))

;; Generic HTTP niceties

(def create-http-client http/create-client)

(defn execute-request
  "Execute the given HTTP request (an instance of com.ning.http.client.Request),
  creating and closing a new client if none is supplied, in which case the full
  HTTP response will be awaited (and the default request timeout of 60 seconds
  will be enforced). callbacks, if supplied, should be a map with the keys:
  :status :headers :part :completed :error, and values that are callback functions"
  [request {:keys [client callbacks]}]
  (if client
    (apply http-request/execute-request client request (apply concat callbacks))
    (with-open [client (http/create-client)]
      ; force waiting for complete response when client is not provided
      (http/await (execute-request request {:client client :callbacks callbacks})))))

;; OAuth credentials

(defn- request-app-only-token
  "Request a 'Bearer' token from Twitter for app-only authentication"
  [consumer-key consumer-secret]
  (let [auth-string (str/join ":" (map url-encode [consumer-key consumer-secret]))
        auth-base64 (String. ^bytes (base64/encode (.getBytes auth-string)))
        headers {:Authorization (str "Basic " auth-base64)
                 :Content-Type "application/x-www-form-urlencoded;charset=UTF-8"}
        request (http-request/prepare-request :post "https://api.twitter.com/oauth2/token"
                                              :headers headers
                                              :body "grant_type=client_credentials")
        response (execute-request request nil)
        body (http/string response)]
    (if (= (:code (http/status response)) 200)
      (get (json/read-str body) "access_token")
      (throw (Exception. (str "Failed to retrieve application-only token due to an unknown error: " body))))))

(def ^:private get-app-only-token (memoize request-app-only-token))

(defprotocol Credentials
  (auth-header [this request-method request-uri query]
    "Generate the string value for an Authorization HTTP header"))

(defrecord AppCredentials [consumer-key consumer-secret]
  Credentials
  (auth-header [_ _ _ _]
    (str "Bearer " (get-app-only-token consumer-key consumer-secret))))

(defrecord UserCredentials [consumer-key consumer-secret user-token user-token-secret]
  Credentials
  (auth-header [_ request-method request-uri query]
    (-> (oauth/make-consumer consumer-key
                             consumer-secret
                             "https://twitter.com/oauth/request_token"
                             "https://twitter.com/oauth/access_token"
                             "https://twitter.com/oauth/authorize"
                             :hmac-sha1)
        ; this will throw a NullPointerException if the consumer-key or -secret is nil
        (oauth/credentials user-token user-token-secret request-method request-uri query)
        (oauth/authorization-header "Twitter API"))))

;; API

(defn ^String format-twitter-error-message
  "read an error response into a string error message"
  [response]
  (let [status-code (:code (http/status response))
        headers (http/headers response)
        body (json/read-str (or (http/string response) "") :key-fn keyword :eof-error? false)
        first-error (or (first (:errors body)) (:error body))]
    (->> (list "Twitter API error response"
               (str "(#" (or (:code first-error) "N/A") ")")
               (when (= 429 status-code) ; rate limit exceeeded
                 (str "Rate limit exceeded; next reset at "
                      (:x-rate-limit-reset headers)
                      " (UTC epoch seconds)"))
               (or (:message first-error) first-error)
               (:request body))
         (remove nil?)
         (str/join " "))))

(defn prepare-request
  "Prepares an HTTP request, signing with OAuth as directed by the credentials argument.
  You should not supply an {:auth ...} pair in the options map.
  Returns an instance of com.ning.http.client.Request."
  [method url credentials {:keys [query] :as options}]
  (->> (assoc-in options [:headers :Authorization] (auth-header credentials method url query))
       ; prepare-request takes all the optional arguments as & rest-args, so we
       ; have to flatten here to get from a list of tuples (what (seq my-map) produces)
       ; to a flat list of alternating keyword/value items
       (apply concat) ; or (into [] cat)
       (apply http-request/prepare-request method url)))

(defn throw-if-response-failed?
  "throw Exception with custom-formatted message if the request/response failed"
  [response]
  (cond (http/failed? response)
          (throw (http/error response))
        (not= (:code (http/status response)) 200)
          (throw (Exception. (format-twitter-error-message response)))
        :else response))

(defn deref-response
  "awaits and derefs a response into a map of: headers, status, body (as string), and error"
  [response]
  {:status (http/status response)
   :headers (http/headers response)
   ; await the response; otherwise (http/string ...) might return only part of the body
   :body (http/string (http/await response))
   :error (http/error response)})

(defn parse-response-body
  "Parse the response body as JSON"
  [response]
  (update response :body json/read-str :key-fn keyword))

(defn throw-or-parse-response
  "if response failed, throw;
  otherwise, await the entire response and parse the body as json"
  [response]
  (-> response
      throw-if-response-failed?
      deref-response
      parse-response-body))
