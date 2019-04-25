(ns twttr.auth
  "OAuth credential management for both user and app-only authentication"
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [oauth.client :as oauth]))

(defprotocol Credentials
  (auth-header [this request-method request-uri request-params]
    "Generate the string value for an Authorization HTTP header"))

(defn- map-values
  "Contruct a new map with all the values of the map kvs passed through f"
  [f kvs]
  (into {} (for [[k v] kvs] [k (f v)])))

(defn- url-encode
  "Basically identical to oauth.signature/url-encode; starts with Java's built-in
  application/x-www-form-urlencoded format, then layers RFC-3986 compliance on top."
  [url-string]
  (-> (java.net.URLEncoder/encode url-string "UTF-8")
      (str/replace "+" "%20")
      (str/replace "*" "%2A")
      (str/replace "%7E" "~")))

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
   (let [credentials (map-values #(System/getenv %) env-mapping)]
     (assert (every? some? (vals credentials))
             (str "Failed to read credentials from env; one of "
                  (vals env-mapping) " could not be found."))
     (map->AppCredentials credentials))))

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
   (let [credentials (map-values #(System/getenv %) env-mapping)]
     (assert (every? some? (vals credentials))
             (str "Failed to read credentials from env; one of "
                  (vals env-mapping) " could not be found."))
     (map->UserCredentials credentials))))

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

; The RateLimitStatus record keeps track of the rate-limiting state
;   for a specific application (set of credentials),
;   for a specific Twitter API endpoint (path).
; It doesn't need to be a record, but we might accrue a lot of them.
; Whenever we request an endpoint for an application,
;   the response conveys an update to this record.
(defrecord RateLimitStatus [limit remaining reset])

(defn headers->RateLimitStatus
  "Parse Twitter response headers as a map with integer values,
  without the 'x-rate-limit-' prefix."
  [{:strs [x-rate-limit-limit x-rate-limit-remaining x-rate-limit-reset]
    :or   {x-rate-limit-limit     "999"
           x-rate-limit-remaining "999"
           x-rate-limit-reset     "0"}}]
  (->RateLimitStatus (Integer/parseInt x-rate-limit-limit)
                     (Integer/parseInt x-rate-limit-remaining)
                     (Integer/parseInt x-rate-limit-reset)))

(defprotocol StatefulCredentials
  (find! [this path]
    "Find the freshest credentials from the collection `this` for requesting `path`")
  (update! [this credentials path rate-limit-status]
    "Update the `rate-limit-status` state associated with `credentials` for requesting `path`"))

(defrecord MemoryCredentials [coll-rate-limit-statuses]
  ; `coll-rate-limit-statuses` is an atom mapping {User,App}Credentials records
  ; to `rate-limit-statuses` maps, which in turn are mappings
  ; from rate-limited Twitter API paths (like "/users/lookup") to RateLimitStatus records,
  ; (which are fundamentally just a map like {:limit 75, :remaining 70, :reset 1513290428})
  StatefulCredentials
  (find! [_ path]
    (let [; current-epoch is the number of seconds, like Twitter's 'reset' header value
          current-epoch (quot (System/currentTimeMillis) 1000)
          ; used-calls return the number of used calls to `path`;
          ; smaller values indicate newer/fresher credentials;
          ; 0 indicates never-used credentials (as far as we know)
          used-calls (fn used-calls [[credentials rate-limit-statuses]]
                       (if-let [{:keys [limit remaining reset]} (get rate-limit-statuses path)]
                         (if (> current-epoch reset)
                           0
                           (- limit remaining))
                         0))]
      (key (apply min-key used-calls @coll-rate-limit-statuses))))
  ; `credentials` is a key from the `coll-rate-limit-statuses` atom mapping,
  ; and `rate-limit-status` is a RateLimitStatus record
  (update! [_ credentials path rate-limit-status]
    (swap! coll-rate-limit-statuses assoc-in [credentials path] rate-limit-status)))

(defn coll->MemoryCredentials
  "Create a new MemoryCredentials record from a flat sequence of {User,App}Credentials records."
  [credentials-coll]
  (-> (zipmap credentials-coll (repeat nil))
      (atom)
      (->MemoryCredentials)))
