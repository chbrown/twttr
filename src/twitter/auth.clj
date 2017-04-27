(ns twitter.auth
  "OAuth credential management for both user and app-only authentication"
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [aleph.http :as http]
            [oauth.client :as oauth]
            [oauth.signature :refer [url-encode]]))

(defn- request-app-only-token
  "Request a 'Bearer' token from Twitter for app-only authentication"
  [consumer-key consumer-secret]
  (let [response @(http/request {:request-method :post
                                 :url "https://api.twitter.com/oauth2/token"
                                 :form-params {"grant_type" "client_credentials"}
                                 :basic-auth (map url-encode [consumer-key consumer-secret])})]
    (if (= 200 (:status response))
      (get (json/read (io/reader (:body response))) "access_token")
      (let [msg (str "Failed to retrieve application-only token. Error: " (slurp (:body response)))]
        (throw (ex-info msg (dissoc response :body)))))))

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
