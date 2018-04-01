(ns twttr.auth
  "OAuth credential management for both user and app-only authentication"
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [oauth.client :as oauth]
            [oauth.signature :refer [url-encode]]))

(defprotocol Credentials
  (auth-header [this request-method request-uri query]
    "Generate the string value for an Authorization HTTP header"))

(defn- map-values
  "Contruct a new map with all the values of the map kvs passed through f"
  [f kvs]
  (into {} (for [[k v] kvs] [k (f v)])))

;; app

(defn- extract-access-token
  "Extract the `access_token` value out of a JSON response
  from the Twitter API's `POST oauth2/token` endpoint."
  [{:keys [status body] :as response}]
  (if (= 200 status)
    (get (json/read (io/reader body)) "access_token")
    (throw (ex-info (str "Failed to extract access token. Error: " (slurp body)) response))))

(defn- request-app-only-token
  "Request a 'Bearer' token from Twitter for app-only authentication,
  returning a deferred aleph.http request.
  See documentation at https://developer.twitter.com/en/docs/basics/authentication/api-reference/token"
  [{:keys [consumer-key consumer-secret] :as credentials}]
  (-> {:request-method :post
       :url "https://api.twitter.com/oauth2/token"
       :form-params {"grant_type" "client_credentials"}
       :basic-auth (map url-encode [consumer-key consumer-secret])}
      (http/request)
      (d/chain extract-access-token)))

(defrecord AppCredentials [consumer-key consumer-secret]
  Credentials
  (auth-header [_ _ _ _]
    (str "Bearer " @(request-app-only-token {:consumer-key consumer-key :consumer-secret consumer-secret}))))

(defn env->AppCredentials
  "Create an AppCredentials instance from environment variables, defaulting to
  CONSUMER_KEY and CONSUMER_SECRET"
  ([]
   (env->AppCredentials {:consumer-key    "CONSUMER_KEY"
                         :consumer-secret "CONSUMER_SECRET"}))
  ([env-mapping]
   (map->AppCredentials (map-values #(System/getenv %) env-mapping))))

;; user

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

; overwrite defrecord-supplied constructor with version adding pre-conditions
; see (pprint (macroexpand-1 (read-string (source-fn '->UserCredentials))))
(defn ->UserCredentials
  "Positional factory function for class user.UserCredentials."
  [consumer-key consumer-secret user-token user-token-secret]
  {:pre [(some? consumer-key)
         (some? consumer-secret)
         (some? user-token)
         (some? user-token-secret)]}
  (new UserCredentials consumer-key consumer-secret user-token user-token-secret))

(defn env->UserCredentials
  "Create a UserCredentials instance from environment variables, defaulting to
  CONSUMER_KEY, CONSUMER_SECRET, ACCESS_TOKEN, and ACCESS_TOKEN_SECRET"
  ([]
   (env->UserCredentials {:consumer-key      "CONSUMER_KEY"
                          :consumer-secret   "CONSUMER_SECRET"
                          :user-token        "ACCESS_TOKEN"
                          :user-token-secret "ACCESS_TOKEN_SECRET"}))
  ([env-mapping]
   (map->UserCredentials (map-values #(System/getenv %) env-mapping))))
