(ns twitter.oauth
  (:require [clojure.data.codec.base64 :as base64]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [http.async.client :as http]
            [http.async.client.request :refer [execute-request
                                               prepare-request]]
            [oauth.client :as oauth]
            [oauth.signature :refer [url-encode]]))

(defn- request-app-only-token
  "Request a 'Bearer' token from Twitter for app-only authentication"
  [consumer-key consumer-secret]
  (let [auth-string (string/join ":" (map url-encode [consumer-key consumer-secret]))
        req (prepare-request :post "https://api.twitter.com/oauth2/token"
                             :headers {:Authorization (str "Basic " (String. (base64/encode (.getBytes auth-string))))
                                       :Content-Type "application/x-www-form-urlencoded;charset=UTF-8"}
                             :body "grant_type=client_credentials")
        client (http/create-client :follow-redirects false :request-timeout -1)
        response (http/await (execute-request client req))
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
        (oauth/credentials user-token user-token-secret request-method request-uri query)
        (oauth/authorization-header "Twitter API"))))

(defn make-oauth-creds
  "Create a Credentials-implementing object.
  If only consumer-key and consumer-secret are supplied, this function will return an AppCredentials instance.
  If user-key and user-token-secret are also supplied, it will return a UserCredentials instance."
  ([consumer-key consumer-secret]
   (AppCredentials. consumer-key consumer-secret))
  ([consumer-key consumer-secret user-token user-token-secret]
   (UserCredentials. consumer-key consumer-secret user-token user-token-secret)))
