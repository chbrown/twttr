(ns twitter.auth
  "OAuth credential management for both user and app-only authentication"
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.data.codec.base64 :as base64]
            [aleph.http :as http]
            [oauth.client :as oauth]
            [oauth.signature :refer [url-encode]]))

(defn- request-app-only-token
  "Request a 'Bearer' token from Twitter for app-only authentication"
  [consumer-key consumer-secret]
  (let [auth-string (str/join ":" (map url-encode [consumer-key consumer-secret]))
        auth-base64 (String. ^bytes (base64/encode (.getBytes auth-string)))
        req {:request-method :post
             :url "https://api.twitter.com/oauth2/token"
             :headers {:Authorization (str "Basic " auth-base64)
                       :Content-Type "application/x-www-form-urlencoded;charset=UTF-8"}
             :body "grant_type=client_credentials"}
        {:keys [status body]} @(http/request req)]
    (if (= status 200)
      (get (json/read (io/reader body)) "access_token")
      (throw (Exception. (str "Failed to retrieve application-only token due to an unknown error: " (slurp body)))))))

(def ^:private get-app-only-token (memoize request-app-only-token))

(defprotocol Credentials
  (auth-header [this request-method request-uri query]
    "Generate the string value for an Authorization HTTP header"))

(defrecord AppCredentials [consumer-key consumer-secret]
  Credentials
  (auth-header [_ _ _ _]
    (str "Bearer " (get-app-only-token consumer-key consumer-secret))))

(defn env->AppCredentials
  "Create an AppCredentials instance from the environment variables:
  CONSUMER_KEY and CONSUMER_SECRET"
  []
  (->> ["CONSUMER_KEY" "CONSUMER_SECRET"]
       (map #(System/getenv %))
       (apply ->AppCredentials)))

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

(defn env->UserCredentials
  "Create a UserCredentials instance from the environment variables:
  CONSUMER_KEY, CONSUMER_SECRET, ACCESS_TOKEN, and ACCESS_TOKEN_SECRET"
  []
  (->> ["CONSUMER_KEY" "CONSUMER_SECRET" "ACCESS_TOKEN" "ACCESS_TOKEN_SECRET"]
       (map #(System/getenv %))
       (apply ->UserCredentials)))
