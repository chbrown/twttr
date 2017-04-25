(ns twitter.api
  (:require [clojure.string :as str]
            [twitter.core :refer [prepare-request
                                  execute-request
                                  throw-or-parse-response]]
            [twitter.streaming :refer [create-streaming-callbacks]]))

(defn- replace-pattern-params
  "substitutes parameters for tokens in the given pattern"
  [pattern params]
  (str/replace pattern #"\{\:(\w+)\}" (fn [[_ name]] (str (get params (keyword name))))))

(defn- path->symbol
  [path]
  (-> path
      ; drop .json extension
      (str/replace #"\.json$" "")
      ; convert groups of symbols to single dashes
      (str/replace #"[^a-zA-Z]+" "-")
      ; drop trailing dashes
      (str/replace #"-$" "")
      (symbol)))

(defn- twitter
  [path credentials {:keys [prefix method options-fn response-fn params]
                     :or {method :get
                          options-fn identity
                          response-fn identity}
                     :as options}]
  (let [url (str prefix "/" (replace-pattern-params path params))
        options (options-fn (update options :query merge params))
        request (prepare-request method url credentials options)]
    (response-fn (execute-request request options))))

(defmacro defn-twitter
  "Define a Twitter API call as a method with name, HTTP method and relative resource path.
  From these it creates a url, the api context and relative resource path."
  [path default-options]
  `(defn ~(path->symbol path)
     [credentials# & {:as local-options#}]
     (twitter ~path credentials# (merge ~default-options local-options#))))

;; REST

(def ^:dynamic *rest* {:prefix "https://api.twitter.com/1.1"
                       :response-fn throw-or-parse-response})
(def ^:dynamic *rest-post* (assoc *rest* :method :post))
; Accounts
(defn-twitter "account/settings.json" *rest*)
(defn-twitter "account/verify_credentials.json" *rest*)
(defn-twitter "account/update_delivery_device.json" *rest-post*)
(defn-twitter "account/update_profile.json" *rest-post*)
(defn-twitter "account/update_profile_background_image.json" *rest-post*)
(defn-twitter "account/update_profile_colors.json" *rest-post*)
(defn-twitter "account/update_profile_image.json" *rest-post*)
(defn-twitter "account/remove_profile_banner.json" *rest-post*)
(defn-twitter "account/update_profile_banner.json" *rest-post*)
(defn-twitter "users/profile_banner.json" *rest-post*)
(defn-twitter "application/rate_limit_status.json" *rest*)
; Blocks
(defn-twitter "blocks/list.json" *rest*)
(defn-twitter "blocks/ids.json" *rest*)
(defn-twitter "blocks/create.json" *rest-post*)
(defn-twitter "blocks/destroy.json" *rest-post*)
; Timeline
(defn-twitter "statuses/mentions_timeline.json" *rest*)
(defn-twitter "statuses/user_timeline.json" *rest*)
(defn-twitter "statuses/home_timeline.json" *rest*)
(defn-twitter "statuses/retweets_of_me.json" *rest*)
; Statuses
(defn-twitter "statuses/lookup.json" *rest*)
(defn-twitter "statuses/retweets/{:id}.json" *rest*)
(defn-twitter "statuses/show/{:id}.json" *rest*)
(defn-twitter "statuses/destroy/{:id}.json" *rest-post*)
(defn-twitter "statuses/update.json" *rest-post*)
(defn-twitter "statuses/retweet/{:id}.json" *rest-post*)
(defn-twitter "statuses/oembed.json" *rest*)
; Search
(defn-twitter "search/tweets.json" *rest*)
; User
(defn-twitter "users/show.json" *rest*)
(defn-twitter "users/lookup.json" *rest-post*)
(defn-twitter "users/search.json" *rest*)
(defn-twitter "users/contributees.json" *rest*)
(defn-twitter "users/contributors.json" *rest*)
(defn-twitter "users/suggestions.json" *rest*)
(defn-twitter "users/suggestions/{:slug}.json" *rest*)
(defn-twitter "users/suggestions/{:slug}/members.json" *rest*)
; Trends
(defn-twitter "trends/place.json" *rest*)
(defn-twitter "trends/available.json" *rest*)
(defn-twitter "trends/closest.json" *rest*)
; Lists
(defn-twitter "lists/list.json" *rest*)
(defn-twitter "lists/statuses.json" *rest*)
(defn-twitter "lists/show.json" *rest*)
(defn-twitter "lists/memberships.json" *rest*)
(defn-twitter "lists/subscriptions.json" *rest*)
(defn-twitter "lists/ownerships.json" *rest*)
(defn-twitter "lists/create.json" *rest-post*)
(defn-twitter "lists/update.json" *rest-post*)
(defn-twitter "lists/destroy.json" *rest-post*)
; List members
(defn-twitter "lists/members/destroy.json" *rest-post*)
(defn-twitter "lists/members/destroy_all.json" *rest-post*)
(defn-twitter "lists/members.json" *rest*)
(defn-twitter "lists/members/show.json" *rest*)
(defn-twitter "lists/members/create.json" *rest-post*)
(defn-twitter "lists/members/create_all.json" *rest-post*)
; List subscribers
(defn-twitter "lists/subscribers.json" *rest*)
(defn-twitter "lists/subscribers/show.json" *rest*)
(defn-twitter "lists/subscribers/create.json" *rest-post*)
(defn-twitter "lists/subscribers/destroy.json" *rest-post*)
; Direct messages
(defn-twitter "direct_messages.json" *rest*)
(defn-twitter "direct_messages/sent.json" *rest*)
(defn-twitter "direct_messages/show.json" *rest*)
(defn-twitter "direct_messages/new.json" *rest-post*)
(defn-twitter "direct_messages/destroy.json" *rest-post*)
; Friendships
(defn-twitter "friendships/lookup.json" *rest*)
(defn-twitter "friendships/create.json" *rest-post*)
(defn-twitter "friendships/destroy.json" *rest-post*)
(defn-twitter "friendships/update.json" *rest-post*)
(defn-twitter "friendships/show.json" *rest*)
(defn-twitter "friendships/incoming.json" *rest*)
(defn-twitter "friendships/outgoing.json" *rest*)
; Friends and followers
(defn-twitter "friends/ids.json" *rest*)
(defn-twitter "friends/list.json" *rest*)
(defn-twitter "followers/ids.json" *rest*)
(defn-twitter "followers/list.json" *rest*)
; Favorites
(defn-twitter "favorites/list.json" *rest*)
(defn-twitter "favorites/destroy.json" *rest-post*)
(defn-twitter "favorites/create.json" *rest-post*)
; Report spam
(defn-twitter "users/report_spam.json" *rest-post*)
; Saved searches
(defn-twitter "saved_searches/list.json" *rest*)
(defn-twitter "saved_searches/show/{:id}.json" *rest*)
(defn-twitter "saved_searches/create.json" *rest-post*)
(defn-twitter "saved_searches/destroy/{:id}.json" *rest-post*)
; Geo
(defn-twitter "geo/id/{:place_id}.json" *rest*)
(defn-twitter "geo/reverse_geocode.json" *rest*)
(defn-twitter "geo/search.json" *rest*)
(defn-twitter "geo/similar_places.json" *rest*)
(defn-twitter "geo/place.json" *rest-post*)
; Help
(defn-twitter "help/configuration.json" *rest*)
(defn-twitter "help/languages.json" *rest*)
(defn-twitter "help/tos.json" *rest*)
(defn-twitter "help/privacy.json" *rest*)

;; OAuth

(def ^:dynamic *oauth* {:prefix "https://api.twitter.com"})

(defn-twitter "oauth/authenticate" *oauth*)
(defn-twitter "oauth/authorize" *oauth*)
(defn-twitter "oauth/access_token" (assoc *oauth* :method :post))
(defn-twitter "oauth/request_token" (assoc *oauth* :method :post))

;; Streaming

(def ^:dynamic *stream* {:prefix "https://stream.twitter.com/1.1"
                         :options-fn #(assoc % :callbacks (create-streaming-callbacks))})

(defn-twitter "statuses/filter.json" (assoc *stream* :method :post))
(defn-twitter "statuses/firehose.json" *stream*)
(defn-twitter "statuses/sample.json" *stream*)

(def ^:dynamic *userstream* (assoc *stream* :prefix "https://userstream.twitter.com/1.1"))
(defn user-stream
  [credentials & {:as local-options}]
  (twitter "user.json" credentials (merge *userstream* local-options)))

(def ^:dynamic *sitestream* (assoc *stream* :prefix "https://sitestream.twitter.com/1.1"))
(defn site-stream
  [credentials & {:as local-options}]
  (twitter "site.json" credentials (merge *sitestream* local-options)))
