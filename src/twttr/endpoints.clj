(ns twttr.endpoints
  (:refer-clojure :exclude [name])
  (:require [clojure.string :as str]))

(defrecord Endpoint [domain version path request-method format])

;; Sections below are derived from the sidebar at https://developer.twitter.com/en/docs

;; OAuth

(def authentication
  "Twitter uses OAuth to provide authorized access to its API.
  https://developer.twitter.com/en/docs/basics/authentication/api-reference"
  (for [[path request-method]
        [["/oauth/authenticate"      :get]
         ["/oauth/authorize"         :get]
         ["/oauth/access_token"      :post]
         ["/oauth/request_token"     :post]
         ["/oauth2/token"            :post]
         ["/oauth2/invalidate_token" :post]]]
    (->Endpoint "api.twitter.com" nil path request-method nil)))

;; Accounts and users

(def subscribe-to-your-account-activity
  "Subscribe to your account activity
  https://developer.twitter.com/en/docs/accounts-and-users/subscribe-account-activity/api-reference"
  (list*
   (assoc (->Endpoint "sitestream.twitter.com" "/1.1" "/site" :get :json) :name "site-stream")
   (assoc (->Endpoint "userstream.twitter.com" "/1.1" "/user" :get :json) :name "user-stream")
   (for [[path request-method]
         [["/account_activity/webhooks/:webhook_id/subscriptions"      :delete]
          ["/account_activity/webhooks/:webhook_id"                    :delete]
          ["/account_activity/webhooks/:webhook_id/subscriptions"      :get]
          ["/account_activity/webhooks"                                :get]
          ["/account_activity/webhooks/:webhook_id/subscriptions"      :post]
          ["/account_activity/webhooks"                                :post]
          ["/account_activity/webhooks/:webhook_id"                    :put]
          ["/account_activity/webhooks/:webhook_id/subscriptions/list" :get]]]
     (->Endpoint "api.twitter.com" "/1.1" path request-method :json))))

(def manage-account-settings-and-profile
  "Manage account settings and profile
  https://developer.twitter.com/en/docs/accounts-and-users/manage-account-settings/api-reference"
  ; are these endpoints deleted? deprecated? invalid?: "account/update_profile_colors" :post, "account/update_delivery_device" :post
  (for [[path request-method]
        [["/account/settings"                        :get]
         ["/account/verify_credentials"              :get]
         ["/users/profile_banner"                    :get] ; :post?
         ["/account/remove_profile_banner"           :post]
         ["/account/settings"                        :post]
         ["/account/update_profile"                  :post]
         ["/account/update_profile_background_image" :post]
         ["/account/update_profile_banner"           :post]
         ["/account/update_profile_image"            :post]
         ["/saved_searches/list"                     :get]
         ["/saved_searches/show/:id"                 :get]
         ["/saved_searches/create"                   :post]
         ["/saved_searches/destroy/:id"              :post]]]
    (->Endpoint "api.twitter.com" "/1.1" path request-method :json)))

(def mute-block-and-report-users
  "Mute, block and report users
  https://developer.twitter.com/en/docs/accounts-and-users/mute-block-report-users/api-reference"
  (for [[path request-method]
        [["/blocks/ids"          :get]
         ["/blocks/list"         :get]
         ["/mutes/users/ids"     :get]
         ["/mutes/users/list"    :get]
         ["/blocks/create"       :post]
         ["/blocks/destroy"      :post]
         ["/mutes/users/create"  :post]
         ["/mutes/users/destroy" :post]
         ["/users/report_spam"   :post]]]
    (->Endpoint "api.twitter.com" "/1.1" path request-method :json)))

(def follow-search-and-get-users
  "Follow, search, and get users
  https://developer.twitter.com/en/docs/accounts-and-users/follow-search-get-users/api-reference"
  ; Are these deprecated / missing?: "users/contributees" :get, "users/contributors" :get
  (for [[path request-method]
        [["/followers/ids"                   :get]
         ["/followers/list"                  :get]
         ["/friends/ids"                     :get]
         ["/friends/list"                    :get]
         ["/friendships/incoming"            :get]
         ["/friendships/lookup"              :get]
         ["/friendships/no_retweets/ids"     :get]
         ["/friendships/outgoing"            :get]
         ["/friendships/show"                :get]
         ["/users/lookup"                    :get]
         ["/users/search"                    :get]
         ["/users/show"                      :get]
         ["/users/suggestions"               :get]
         ["/users/suggestions/:slug"         :get]
         ["/users/suggestions/:slug/members" :get]
         ["/friendships/create"              :post]
         ["/friendships/destroy"             :post]
         ["/friendships/update"              :post]]]
    (->Endpoint "api.twitter.com" "/1.1" path request-method :json)))

(def create-and-manage-lists
  "Create and manage lists
  https://developer.twitter.com/en/docs/accounts-and-users/create-manage-lists/api-reference"
  ; reorganize into:
  ; List subscribers
  (for [[path request-method]
        [["/lists/list"                :get]
         ["/lists/show"                :get]
         ["/lists/statuses"            :get]
         ["/lists/create"              :post]
         ["/lists/update"              :post]
         ["/lists/destroy"             :post]
         ["/lists/ownerships"          :get]
         ["/lists/subscriptions"       :get]
         ["/lists/memberships"         :get]
         ["/lists/members"             :get]
         ["/lists/members/show"        :get]
         ["/lists/members/create"      :post]
         ["/lists/members/create_all"  :post]
         ["/lists/members/destroy"     :post]
         ["/lists/members/destroy_all" :post]
         ["/lists/subscribers"         :get]
         ["/lists/subscribers/show"    :get]
         ["/lists/subscribers/create"  :post]
         ["/lists/subscribers/destroy" :post]]]
    (->Endpoint "api.twitter.com" "/1.1" path request-method :json)))

;; Tweets

(def post-retrieve-and-engage-with-tweets
  "Post, retrieve and engage with Tweets
  https://developer.twitter.com/en/docs/tweets/post-and-engage/api-reference/post-statuses-update"
  (for [[path request-method]
        [["/statuses/update"         :post]
         ["/statuses/destroy/:id"    :post]
         ["/statuses/show/:id"       :get]
         ["/statuses/oembed"         :get]
         ["/statuses/lookup"         :get]
         ["/statuses/retweet/:id"    :post]
         ["/statuses/unretweet/:id"  :post]
         ["/statuses/retweets/:id"   :get]
         ["/statuses/retweets_of_me" :get]
         ["/statuses/retweeters/ids" :get]
         ["/favorites/create"        :post]
         ["/favorites/destroy"       :post]
         ["/favorites/list"          :get]]]
    (->Endpoint "api.twitter.com" "/1.1" path request-method :json)))

(def get-tweet-timelines
  "Get Tweet timelines
  https://developer.twitter.com/en/docs/tweets/timelines/api-reference"
  (for [[path request-method]
        [["/statuses/home_timeline"     :get]
         ["/statuses/mentions_timeline" :get]
         ["/statuses/user_timeline"     :get]]]
    (->Endpoint "api.twitter.com" "/1.1" path request-method :json)))

(def curate-a-collection-of-tweets
  "Curate a collection of Tweets
  https://developer.twitter.com/en/docs/tweets/curate-a-collection/api-reference"
  (for [[path request-method]
        [["/collections/entries"        :get]
         ["/collections/list"           :get]
         ["/collections/show"           :get]
         ["/collections/create"         :post]
         ["/collections/destroy"        :post]
         ["/collections/entries/add"    :post]
         ["/collections/entries/curate" :post]
         ["/collections/entries/move"   :post]
         ["/collections/entries/remove" :post]
         ["/collections/update"         :post]]]
    (->Endpoint "api.twitter.com" "/1.1" path request-method :json)))

(def search-tweets
  "Search Tweets
  https://developer.twitter.com/en/docs/tweets/search/api-reference/get-search-tweets"
  [(->Endpoint "api.twitter.com" "/1.1" "/search/tweets" :get :json)])

(def filter-realtime-tweets
  "Filter realtime Tweets
  https://developer.twitter.com/en/docs/tweets/filter-realtime/api-reference"
  [(->Endpoint "stream.twitter.com" "/1.1" "/statuses/filter" :post :json)])

(def sample-realtime-tweets
  "Sample realtime Tweets
  https://developer.twitter.com/en/docs/tweets/sample-realtime/api-reference/get-statuses-sample"
  [(->Endpoint "stream.twitter.com" "/1.1" "/statuses/sample" :get :json)])

;; Direct Messages

(def sending-and-receiving-events
  "Sending and receiving events
  https://developer.twitter.com/en/docs/direct-messages/sending-and-receiving/api-reference"
  (for [[path request-method]
        [["/direct_messages/destroy"     :post]
         ["/direct_messages/events/show" :get]
         ["/direct_messages/events/list" :get]
         ["/direct_messages/events/new"  :post]
         ["/direct_messages/show"        :get]
         ["/direct_messages"             :get]
         ["/direct_messages/sent"        :get]
         ["/direct_messages/new"         :post]]]
    (->Endpoint "api.twitter.com" "/1.1" path request-method :json)))

(def welcome-messages
  "Welcome Messages
  https://developer.twitter.com/en/docs/direct-messages/welcome-messages/api-reference"
  (for [[path request-method]
        [["/direct_messages/welcome_messages/destroy"       :delete]
         ["/direct_messages/welcome_messages/rules/destroy" :delete]
         ["/direct_messages/welcome_messages/show"          :get]
         ["/direct_messages/welcome_messages/rules/show"    :get]
         ["/direct_messages/welcome_messages/rules/list"    :get]
         ["/direct_messages/welcome_messages/list"          :get]
         ["/direct_messages/welcome_messages/new"           :post]
         ["/direct_messages/welcome_messages/rules/new"     :post]]]
    (->Endpoint "api.twitter.com" "/1.1" path request-method :json)))

;; Media

(def upload-media
  "Upload media
  https://developer.twitter.com/en/docs/media/upload-media/api-reference"
  (for [[path request-method]
        [["/media/upload"          :post]
         ; ["/media/upload"          :get ]
         ["/media/metadata/create" :post]]]
    (->Endpoint "api.twitter.com" "/1.1" path request-method :json)))

;; Trends

(def get-trends-near-a-location
  "Get trends near a location
  https://developer.twitter.com/en/docs/trends/trends-for-location/api-reference"
  [(->Endpoint "api.twitter.com" "/1.1" "/trends/place" :get :json)])

(def get-locations-with-trending-topics
  "Get locations with trending topics
  https://developer.twitter.com/en/docs/trends/locations-with-trending-topics/api-reference"
  [(->Endpoint "api.twitter.com" "/1.1" "/trends/available" :get :json)
   (->Endpoint "api.twitter.com" "/1.1" "/trends/closest"   :get :json)])

;; Geo

(def get-information-about-a-place
  "Get information about a place
  https://developer.twitter.com/en/docs/geo/place-information/api-reference"
  [(->Endpoint "api.twitter.com" "/1.1" "/geo/id/:place_id" :get :json)])

(def get-places-near-a-location
  "Get places near a location
  https://developer.twitter.com/en/docs/geo/places-near-location/api-reference"
  ; are these deprecated/etc?: "geo/similar_places" :get, "geo/place" :post
  [(->Endpoint "api.twitter.com" "/1.1" "/geo/reverse_geocode" :get :json)
   (->Endpoint "api.twitter.com" "/1.1" "/geo/search"          :get :json)])

;; Developer utilities

(def get-twitter-help
  "Combination of:
  * Get app rate limit status
  * Get Twitter configuration details
  * Get Twitter supported languages
  * Get Twitter's privacy policy
  * Get Twitter's terms of service"
  (for [[path request-method]
        [["/application/rate_limit_status" :get]
         ["/help/configuration"            :get]
         ["/help/languages"                :get]
         ["/help/tos"                      :get]
         ["/help/privacy"                  :get]]]
    (->Endpoint "api.twitter.com" "/1.1" path request-method :json)))

(def all
  (concat authentication
          subscribe-to-your-account-activity
          manage-account-settings-and-profile
          mute-block-and-report-users
          follow-search-and-get-users
          create-and-manage-lists
          post-retrieve-and-engage-with-tweets
          get-tweet-timelines
          curate-a-collection-of-tweets
          search-tweets
          filter-realtime-tweets
          sample-realtime-tweets
          sending-and-receiving-events
          welcome-messages
          upload-media
          get-trends-near-a-location
          get-locations-with-trending-topics
          get-information-about-a-place
          get-places-near-a-location
          get-twitter-help))

(defn name
  "Return the name of the given endpoint, or generate one from its path"
  [endpoint]
  (or (:name endpoint)
      (-> (:path endpoint)
          (subs 1)
          (str/replace #"[^a-zA-Z]+" "-"))))

(defn url
  "Format the URL for the given endpoint, substituting values from params into its path as needed"
  [endpoint params]
  (let [{:keys [domain version path format]} endpoint
        ; Replace named placeholders in pattern with values from params
        path (str/replace path #":(\w+)" (fn [[_ param-name]] (str (get params (keyword param-name)))))]
    (str "https://" domain version path (when (= format :json) ".json"))))

(defn streaming?
  "Return true if the given endpoint produces a streaming response"
  [endpoint]
  (str/ends-with? (:domain endpoint) "stream.twitter.com"))
