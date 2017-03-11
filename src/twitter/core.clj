(ns twitter.core
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.data.codec.base64 :as base64]
            [http.async.client :as http]
            [http.async.client.request :as http-request]
            [oauth.client :as oauth]
            [oauth.signature :refer [url-encode]]))

;; OAuth credentials

(defn- request-app-only-token
  "Request a 'Bearer' token from Twitter for app-only authentication"
  [consumer-key consumer-secret]
  (let [auth-string (str/join ":" (map url-encode [consumer-key consumer-secret]))
        headers {:Authorization (str "Basic " (String. (base64/encode (.getBytes auth-string))))
                 :Content-Type "application/x-www-form-urlencoded;charset=UTF-8"}
        req (http-request/prepare-request :post "https://api.twitter.com/oauth2/token"
                                          :headers headers
                                          :body "grant_type=client_credentials")
        client (http/create-client :follow-redirects false)
        response (http/await (http-request/execute-request client req))
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

(defn format-twitter-error-message
  "read an error response into a string error message"
  [response]
  (let [status-code (:code (http/status response))
        body (json/read-str (http/string response) :key-fn keyword)
        message (or (:message (first (:errors body))) (:error body))
        error-code (or (:code (first (:errors body))) status-code)
        body (:request body)]
    (cond
      (= 429 status-code) (format "Twitter responded with error 88: Rate limit exceeded. Next reset at %s (UTC epoch seconds)" (-> response http/headers :x-rate-limit-reset))
      (and body error-code message) (format "Twitter responded '%s' with error %d: %s" body error-code message)
      (and error-code message) (format "Twitter responded with error %d: %s" error-code message)
      message (format "Twitter responded with error: %s" message)
      :default "Twitter responded with an unknown error")))

(defn prepare-request
  "Prepares an HTTP request, signing with OAuth as directed by the credentials argument.
  You should not supply an {:auth ...} pair in the options map."
  [method url credentials {:keys [query] :as options}]
  (->> (assoc-in options [:headers :Authorization] (auth-header credentials method url query))
       ; prepare-request takes all the optional arguments as & rest-args, so we
       ; have to flatten here to get from a list of tuples (what (seq my-map) produces)
       ; to a flat list of alternating keyword/value items
       (apply concat) ; or (into [] cat)
       (apply http-request/prepare-request method url)))

(def ^:private default-client (delay (http/create-client :follow-redirects false)))

(defn execute-request
  "Execute the given HTTP request, using the default client if no client is supplied.
  :callbacks, if supplied, should be followed by a map with any of the following keys:
  :status :headers :part :completed :error, with values that are callback functions"
  [req {:keys [client callbacks] :or {client @default-client}}]
  (apply http-request/execute-request client req (apply concat callbacks)))

(defn prepare-and-execute-request
  [method url credentials options]
  (-> (prepare-request method url credentials options)
      (execute-request options)))

(defn transform-sync-response
  "this takes a response and returns a map of the headers, status, and body (as a string)"
  [response]
  (let [status (http/status response)]
    (if (< (:code status) 400)
      ; success! though maybe not yet complete
      ; we have to await the response; otherwise (http/string response) might return only part of the body
      (let [response (http/await response)]
        {:status status
         :headers (http/headers response)
         :body (json/read-str (http/string response) :key-fn keyword)})
      ; failure! throw Exception with custom-formatted message
      (throw (Exception. (format-twitter-error-message response))))))
