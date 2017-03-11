(ns twitter.api
  (:require [clojure.string :as str]
            [twitter.core :refer [prepare-and-execute-request
                                  transform-sync-response
                                  format-twitter-error-message]]))

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
  [prefix path credentials {:keys [method sync params]
                            :or {method :get sync false}
                            :as options}]
  (let [url (str prefix "/" (replace-pattern-params path params))
        options (update options :query merge params)
        response (prepare-and-execute-request method url credentials options)]
    (if sync
      (transform-sync-response response)
      response)))

(defmacro defn-twitter
  "Define a Twitter API call as a method with name, HTTP method and relative resource path.
  From these it creates a url, the api context and relative resource path."
  [prefix path & {:as default-options}]
  `(defn ~(path->symbol path)
     [credentials# & {:as local-options#}]
     (twitter ~prefix ~path credentials# (merge ~default-options local-options#))))

;; REST

(def ^:dynamic *rest* "https://api.twitter.com/1.1")
; Accounts
(defn-twitter *rest* "account/settings.json" :sync true)
(defn-twitter *rest* "account/verify_credentials.json" :sync true)
(defn-twitter *rest* "account/update_delivery_device.json" :method :post :sync true)
(defn-twitter *rest* "account/update_profile.json" :method :post :sync true)
(defn-twitter *rest* "account/update_profile_background_image.json" :method :post :sync true)
(defn-twitter *rest* "account/update_profile_colors.json" :method :post :sync true)
(defn-twitter *rest* "account/update_profile_image.json" :method :post :sync true)
(defn-twitter *rest* "account/remove_profile_banner.json" :method :post :sync true)
(defn-twitter *rest* "account/update_profile_banner.json" :method :post :sync true)
(defn-twitter *rest* "users/profile_banner.json" :method :post :sync true)
(defn-twitter *rest* "application/rate_limit_status.json" :sync true)
; Blocks
(defn-twitter *rest* "blocks/list.json" :sync true)
(defn-twitter *rest* "blocks/ids.json" :sync true)
(defn-twitter *rest* "blocks/create.json" :method :post :sync true)
(defn-twitter *rest* "blocks/destroy.json" :method :post :sync true)
; Timeline
(defn-twitter *rest* "statuses/mentions_timeline.json" :sync true)
(defn-twitter *rest* "statuses/user_timeline.json" :sync true)
(defn-twitter *rest* "statuses/home_timeline.json" :sync true)
(defn-twitter *rest* "statuses/retweets_of_me.json" :sync true)
; Statuses
(defn-twitter *rest* "statuses/lookup.json" :sync true)
(defn-twitter *rest* "statuses/retweets/{:id}.json" :sync true)
(defn-twitter *rest* "statuses/show/{:id}.json" :sync true)
(defn-twitter *rest* "statuses/destroy/{:id}.json" :method :post :sync true)
(defn-twitter *rest* "statuses/update.json" :method :post :sync true)
(defn-twitter *rest* "statuses/retweet/{:id}.json" :method :post :sync true)
(defn-twitter *rest* "statuses/oembed.json" :sync true)
; Search
(defn-twitter *rest* "search/tweets.json" :sync true)
; User
(defn-twitter *rest* "users/show.json" :sync true)
(defn-twitter *rest* "users/lookup.json" :sync true)
(defn-twitter *rest* "users/search.json" :sync true)
(defn-twitter *rest* "users/contributees.json" :sync true)
(defn-twitter *rest* "users/contributors.json" :sync true)
(defn-twitter *rest* "users/suggestions.json" :sync true)
(defn-twitter *rest* "users/suggestions/{:slug}.json" :sync true)
(defn-twitter *rest* "users/suggestions/{:slug}/members.json" :sync true)
; Trends
(defn-twitter *rest* "trends/place.json" :sync true)
(defn-twitter *rest* "trends/available.json" :sync true)
(defn-twitter *rest* "trends/closest.json" :sync true)
; Lists
(defn-twitter *rest* "lists/list.json" :sync true)
(defn-twitter *rest* "lists/statuses.json" :sync true)
(defn-twitter *rest* "lists/show.json" :sync true)
(defn-twitter *rest* "lists/memberships.json" :sync true)
(defn-twitter *rest* "lists/subscriptions.json" :sync true)
(defn-twitter *rest* "lists/ownerships.json" :sync true)
(defn-twitter *rest* "lists/create.json" :method :post :sync true)
(defn-twitter *rest* "lists/update.json" :method :post :sync true)
(defn-twitter *rest* "lists/destroy.json" :method :post :sync true)
; List members
(defn-twitter *rest* "lists/members/destroy.json" :method :post :sync true)
(defn-twitter *rest* "lists/members/destroy_all.json" :method :post :sync true)
(defn-twitter *rest* "lists/members.json" :sync true)
(defn-twitter *rest* "lists/members/show.json" :sync true)
(defn-twitter *rest* "lists/members/create.json" :method :post :sync true)
(defn-twitter *rest* "lists/members/create_all.json" :method :post :sync true)
; List subscribers
(defn-twitter *rest* "lists/subscribers.json" :sync true)
(defn-twitter *rest* "lists/subscribers/show.json" :sync true)
(defn-twitter *rest* "lists/subscribers/create.json" :method :post :sync true)
(defn-twitter *rest* "lists/subscribers/destroy.json" :method :post :sync true)
; Direct messages
(defn-twitter *rest* "direct_messages.json" :sync true)
(defn-twitter *rest* "direct_messages/sent.json" :sync true)
(defn-twitter *rest* "direct_messages/show.json" :sync true)
(defn-twitter *rest* "direct_messages/new.json" :method :post :sync true)
(defn-twitter *rest* "direct_messages/destroy.json" :method :post :sync true)
; Friendships
(defn-twitter *rest* "friendships/lookup.json" :sync true)
(defn-twitter *rest* "friendships/create.json" :method :post :sync true)
(defn-twitter *rest* "friendships/destroy.json" :method :post :sync true)
(defn-twitter *rest* "friendships/update.json" :method :post :sync true)
(defn-twitter *rest* "friendships/show.json" :sync true)
(defn-twitter *rest* "friendships/incoming.json" :sync true)
(defn-twitter *rest* "friendships/outgoing.json" :sync true)
; Friends and followers
(defn-twitter *rest* "friends/ids.json" :sync true)
(defn-twitter *rest* "friends/list.json" :sync true)
(defn-twitter *rest* "followers/ids.json" :sync true)
(defn-twitter *rest* "followers/list.json" :sync true)
; Favorites
(defn-twitter *rest* "favorites/list.json" :sync true)
(defn-twitter *rest* "favorites/destroy.json" :method :post :sync true)
(defn-twitter *rest* "favorites/create.json" :method :post :sync true)
; Report spam
(defn-twitter *rest* "users/report_spam.json" :method :post :sync true)
; Saved searches
(defn-twitter *rest* "saved_searches/list.json" :sync true)
(defn-twitter *rest* "saved_searches/show/{:id}.json" :sync true)
(defn-twitter *rest* "saved_searches/create.json" :method :post :sync true)
(defn-twitter *rest* "saved_searches/destroy/{:id}.json" :method :post :sync true)
; Geo
(defn-twitter *rest* "geo/id/{:place_id}.json" :sync true)
(defn-twitter *rest* "geo/reverse_geocode.json" :sync true)
(defn-twitter *rest* "geo/search.json" :sync true)
(defn-twitter *rest* "geo/similar_places.json" :sync true)
(defn-twitter *rest* "geo/place.json" :method :post :sync true)
; Help
(defn-twitter *rest* "help/configuration.json" :sync true)
(defn-twitter *rest* "help/languages.json" :sync true)
(defn-twitter *rest* "help/tos.json" :sync true)
(defn-twitter *rest* "help/privacy.json" :sync true)

;; OAuth

(def ^:dynamic *oauth* "https://api.twitter.com")
(defn-twitter *oauth* "oauth/authenticate" :sync true)
(defn-twitter *oauth* "oauth/authorize" :sync true)
(defn-twitter *oauth* "oauth/access_token" :method :post :sync true)
(defn-twitter *oauth* "oauth/request_token" :method :post :sync true)

;; Streaming

(defn- stream-completed
  [response]
  (throw (Exception. "Unexpected completion of stream"))
  [true :continue])

(defn- stream-error
  [response throwable]
  (throw (Exception. (format-twitter-error-message response)))
  throwable)

(def ^:private stream-callbacks {:completed stream-completed :error stream-error})

(def ^:dynamic *stream* "https://stream.twitter.com/1.1")
(defn-twitter *stream* "statuses/filter.json" :method :post :callbacks stream-callbacks)
(defn-twitter *stream* "statuses/firehose.json" :callbacks stream-callbacks)
(defn-twitter *stream* "statuses/sample.json" :callbacks stream-callbacks)

(def ^:dynamic *userstream* "https://userstream.twitter.com/1.1")
(defn user-stream
  [credentials & {:as local-options}]
  (twitter *userstream* "user.json" credentials (assoc local-options :callbacks stream-callbacks)))

(def ^:dynamic *sitestream* "https://sitestream.twitter.com/1.1")
(defn site-stream
  [credentials & {:as local-options}]
  (twitter *sitestream* "site.json" credentials (assoc local-options :callbacks stream-callbacks)))
