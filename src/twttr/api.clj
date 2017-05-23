(ns twttr.api
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [aleph.http :as http]
            [byte-streams :as bs]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [twttr.auth :refer [auth-header]]))

(defn- replace-pattern-params
  "Replace named placeholders in pattern with values from params"
  [pattern params]
  (str/replace pattern #"\{\:(\w+)\}" (fn [[_ name]] (str (get params (keyword name))))))

(defn- path->symbol
  "Convert a Twitter API path to a symbol suitable for use as a function"
  [path]
  (-> path
      ; drop .json extension
      (str/replace #"\.json$" "")
      ; convert groups of symbols to single dashes
      (str/replace #"[^a-zA-Z]+" "-")
      ; drop trailing dashes
      (str/replace #"-$" "")
      (symbol)))

(defn- read-json
  "Read a String / java.io.InputStream / java.io.Reader as JSON.
  Parse keys as keywords, and return nil for empty input."
  [input]
  (-> (cond
        ; this is how json/read-str does it
        (string? input) (java.io.StringReader. input)
        ; convert a java.io.InputStream to a java.io.BufferedReader
        (instance? java.io.InputStream input) (io/reader input)
        ; hope it's some sort of java.io.Reader!
        :default input)
      (json/read :key-fn keyword :eof-error? false)))

(defn- ex-twitter
  "read an error response into a string error message"
  [response]
  (let [response (update response :body slurp)
        body (try (read-json (:body response))
               ; java.lang.Exception: JSON error
               (catch Exception _
                 (:body response)))
        first-error (or (first (:errors body)) (:error body))
        message-parts (list "Twitter API error response"
                            (str "(#" (or (:code first-error) "N/A") ")")
                            (when (= 429 (:status response)) ; rate limit exceeeded
                              (str "Rate limit exceeded; next reset at "
                                   (get-in response [:headers :x-rate-limit-reset])
                                   " (UTC epoch seconds)"))
                            (get first-error :message first-error)
                            (:request body))]
    (ex-info (->> message-parts (remove nil?) (str/join " ")) response)))

(defn request
  ; the argument to middleware should be a function that takes a handler
  ; (which turns a request into a response), and returns a function which takes a request,
  ; potentially modifies that request, runs request through the given handler to get a response,
  ; and then returns the response, potentially after modifying it.
  [url credentials options]
  (let [params (:params options)
        url (replace-pattern-params url params)
        query-params (merge (:query options) params)
        ; Prepare the HTTP request, signing with OAuth as directed by credentials
        authorization (auth-header credentials (get options :request-method :get) url query-params)]
    (-> options
        (assoc :url url :query-params query-params)
        (assoc-in [:headers :Authorization] authorization)
        (http/request)
        (d/catch clojure.lang.ExceptionInfo
                 (fn [ex]
                   (throw (ex-twitter (ex-data ex)))))
        (deref))))

(defmacro defn-twitter
  "Define a Twitter API call as a method with name, HTTP method and relative resource path.
  From these it creates a url, the api context and relative resource path."
  [path request-method defaults]
  `(defn ~(path->symbol path)
     [credentials# & {:as local-options#}]
     (request (str (get ~defaults :prefix) "/" ~path)
              credentials#
              (merge ~defaults {:request-method ~request-method} local-options#))))

;; REST

(defn- wrap-rest-middleware
  [handler]
  (fn rest-middleware-handler [request]
    (d/chain (handler request)
             (fn [response]
               (with-meta (read-json (:body response))
                          (dissoc response :body))))))

(let [defaults {:prefix "https://api.twitter.com/1.1"
                :middleware wrap-rest-middleware}]
  ; Accounts
  (defn-twitter "account/settings.json" :get defaults)
  (defn-twitter "account/verify_credentials.json" :get defaults)
  (defn-twitter "account/update_delivery_device.json" :post defaults)
  (defn-twitter "account/update_profile.json" :post defaults)
  (defn-twitter "account/update_profile_background_image.json" :post defaults)
  (defn-twitter "account/update_profile_colors.json" :post defaults)
  (defn-twitter "account/update_profile_image.json" :post defaults)
  (defn-twitter "account/remove_profile_banner.json" :post defaults)
  (defn-twitter "account/update_profile_banner.json" :post defaults)
  (defn-twitter "users/profile_banner.json" :post defaults)
  (defn-twitter "application/rate_limit_status.json" :get defaults)
  ; Blocks
  (defn-twitter "blocks/list.json" :get defaults)
  (defn-twitter "blocks/ids.json" :get defaults)
  (defn-twitter "blocks/create.json" :post defaults)
  (defn-twitter "blocks/destroy.json" :post defaults)
  ; Timeline
  (defn-twitter "statuses/mentions_timeline.json" :get defaults)
  (defn-twitter "statuses/user_timeline.json" :get defaults)
  (defn-twitter "statuses/home_timeline.json" :get defaults)
  (defn-twitter "statuses/retweets_of_me.json" :get defaults)
  ; Statuses
  (defn-twitter "statuses/lookup.json" :get defaults)
  (defn-twitter "statuses/retweets/{:id}.json" :get defaults)
  (defn-twitter "statuses/show/{:id}.json" :get defaults)
  (defn-twitter "statuses/destroy/{:id}.json" :post defaults)
  (defn-twitter "statuses/update.json" :post defaults)
  (defn-twitter "statuses/retweet/{:id}.json" :post defaults)
  (defn-twitter "statuses/oembed.json" :get defaults)
  ; Search
  (defn-twitter "search/tweets.json" :get defaults)
  ; User
  (defn-twitter "users/show.json" :get defaults)
  (defn-twitter "users/lookup.json" :post defaults)
  (defn-twitter "users/search.json" :get defaults)
  (defn-twitter "users/contributees.json" :get defaults)
  (defn-twitter "users/contributors.json" :get defaults)
  (defn-twitter "users/suggestions.json" :get defaults)
  (defn-twitter "users/suggestions/{:slug}.json" :get defaults)
  (defn-twitter "users/suggestions/{:slug}/members.json" :get defaults)
  ; Trends
  (defn-twitter "trends/place.json" :get defaults)
  (defn-twitter "trends/available.json" :get defaults)
  (defn-twitter "trends/closest.json" :get defaults)
  ; Lists
  (defn-twitter "lists/list.json" :get defaults)
  (defn-twitter "lists/statuses.json" :get defaults)
  (defn-twitter "lists/show.json" :get defaults)
  (defn-twitter "lists/memberships.json" :get defaults)
  (defn-twitter "lists/subscriptions.json" :get defaults)
  (defn-twitter "lists/ownerships.json" :get defaults)
  (defn-twitter "lists/create.json" :post defaults)
  (defn-twitter "lists/update.json" :post defaults)
  (defn-twitter "lists/destroy.json" :post defaults)
  ; List members
  (defn-twitter "lists/members/destroy.json" :post defaults)
  (defn-twitter "lists/members/destroy_all.json" :post defaults)
  (defn-twitter "lists/members.json" :get defaults)
  (defn-twitter "lists/members/show.json" :get defaults)
  (defn-twitter "lists/members/create.json" :post defaults)
  (defn-twitter "lists/members/create_all.json" :post defaults)
  ; List subscribers
  (defn-twitter "lists/subscribers.json" :get defaults)
  (defn-twitter "lists/subscribers/show.json" :get defaults)
  (defn-twitter "lists/subscribers/create.json" :post defaults)
  (defn-twitter "lists/subscribers/destroy.json" :post defaults)
  ; Direct messages
  (defn-twitter "direct_messages.json" :get defaults)
  (defn-twitter "direct_messages/sent.json" :get defaults)
  (defn-twitter "direct_messages/show.json" :get defaults)
  (defn-twitter "direct_messages/new.json" :post defaults)
  (defn-twitter "direct_messages/destroy.json" :post defaults)
  ; Friendships
  (defn-twitter "friendships/lookup.json" :get defaults)
  (defn-twitter "friendships/create.json" :post defaults)
  (defn-twitter "friendships/destroy.json" :post defaults)
  (defn-twitter "friendships/update.json" :post defaults)
  (defn-twitter "friendships/show.json" :get defaults)
  (defn-twitter "friendships/incoming.json" :get defaults)
  (defn-twitter "friendships/outgoing.json" :get defaults)
  ; Friends and followers
  (defn-twitter "friends/ids.json" :get defaults)
  (defn-twitter "friends/list.json" :get defaults)
  (defn-twitter "followers/ids.json" :get defaults)
  (defn-twitter "followers/list.json" :get defaults)
  ; Favorites
  (defn-twitter "favorites/list.json" :get defaults)
  (defn-twitter "favorites/destroy.json" :post defaults)
  (defn-twitter "favorites/create.json" :post defaults)
  ; Report spam
  (defn-twitter "users/report_spam.json" :post defaults)
  ; Saved searches
  (defn-twitter "saved_searches/list.json" :get defaults)
  (defn-twitter "saved_searches/show/{:id}.json" :get defaults)
  (defn-twitter "saved_searches/create.json" :post defaults)
  (defn-twitter "saved_searches/destroy/{:id}.json" :post defaults)
  ; Geo
  (defn-twitter "geo/id/{:place_id}.json" :get defaults)
  (defn-twitter "geo/reverse_geocode.json" :get defaults)
  (defn-twitter "geo/search.json" :get defaults)
  (defn-twitter "geo/similar_places.json" :get defaults)
  (defn-twitter "geo/place.json" :post defaults)
  ; Help
  (defn-twitter "help/configuration.json" :get defaults)
  (defn-twitter "help/languages.json" :get defaults)
  (defn-twitter "help/tos.json" :get defaults)
  (defn-twitter "help/privacy.json" :get defaults))

;; OAuth

(let [defaults {:prefix "https://api.twitter.com"}]
  (defn-twitter "oauth/authenticate" :get defaults)
  (defn-twitter "oauth/authorize" :get defaults)
  (defn-twitter "oauth/access_token" :post defaults)
  (defn-twitter "oauth/request_token" :post defaults))

;; Streaming

(defn- wrap-stream-middleware
  [handler]
  (fn stream-middleware-handler [request]
    (d/chain (handler request)
             (fn [response]
               (with-meta (->> (bs/to-line-seq (:body response))
                               ; Twitter will send lots of newlines as keep-alive signals if the stream is sparse
                               (remove empty?)
                               (map read-json))
                          response)))))

(let [defaults {:prefix "https://stream.twitter.com/1.1"
                :middleware wrap-stream-middleware}]
  (defn-twitter "statuses/filter.json" :post defaults)
  (defn-twitter "statuses/firehose.json" :get defaults)
  (defn-twitter "statuses/sample.json" :get defaults))

(defn user-stream
  [credentials & {:as local-options}]
  (->> local-options
       (merge {:request-method :get :middleware wrap-stream-middleware})
       (request "https://userstream.twitter.com/1.1/user.json" credentials)))

(defn site-stream
  [credentials & {:as local-options}]
  (->> local-options
       (merge {:request-method :get :middleware wrap-stream-middleware})
       (request "https://sitestream.twitter.com/1.1/site.json" credentials)))
