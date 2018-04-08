(ns twttr.auth
  "OAuth credential management for both user and app-only authentication"
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [oauth.client :as oauth]
            [oauth.signature :refer [url-encode]]))

(defprotocol Credentials
  (auth-header [this request-method request-uri request-params]
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
  [{:keys [consumer-key consumer-secret]}]
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
  (auth-header [_ request-method request-uri request-params]
    (-> (oauth/make-consumer consumer-key
                             consumer-secret
                             "https://twitter.com/oauth/request_token"
                             "https://twitter.com/oauth/access_token"
                             "https://twitter.com/oauth/authorize"
                             :hmac-sha1)
        ; this will throw a NullPointerException if the consumer-key or -secret is nil
        (oauth/credentials user-token user-token-secret request-method request-uri request-params)
        (oauth/authorization-header "Twitter API"))))

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

;; collection

(defn- map->Credentials
  "Create an instance of UserCredentials from `m` (a map) if it has values for
  the keys :user-token and :user-token-secret, otherwise create an AppCredentials."
  [credentials-map]
  (if (every? credentials-map [:user-token :user-token-secret])
    (map->UserCredentials credentials-map)
    (map->AppCredentials credentials-map)))

(defn- read-csv
  "Parse `lines` (a seq of strings) as CSV (no quoting or escaping).
  Return a seq of maps, using the first line in `lines` as column headers
  and remapping these headers to desired output key names as specified by
  `column-mapping`."
  [lines column-mapping]
  (let [[columns & rows] (map #(str/split % #",") lines)
        ; columns-indices is a mapping from column name (strings) to their index in the rows
        columns-indices (zipmap columns (range))
        ; row-mapping is a mapping from the desired output keys to their column indices in the csv
        row-mapping (map-values columns-indices column-mapping)]
    (map (fn [row] (map-values row row-mapping)) rows)))

(defn file->Credentials-coll
  "Read a sequence of App/UserCredentials instances from a CSV file.
   * `column-mapping` is a mapping from (App/)UserCredential keys to their
     column names in the CSV, defaulting to:
     consumer_key, consumer_secret, access_token, access_token_secret"
  ([csv-file]
   (file->Credentials-coll csv-file {:consumer-key      "consumer_key"
                                     :consumer-secret   "consumer_secret"
                                     :user-token        "access_token"
                                     :user-token-secret "access_token_secret"}))
  ([csv-file column-mapping]
   (with-open [reader (io/reader csv-file)]
     ; this must be eagerly-evaluated due to the file input
     (->> (read-csv (line-seq reader) column-mapping)
          (map map->Credentials)
          (doall)))))
