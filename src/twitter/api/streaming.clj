(ns twitter.api.streaming
  (:require [twitter.core :refer [def-twitter-method
                                  format-twitter-error-message]]))

(def callbacks {:completed (fn [response] (throw (Exception. "Unexpected completion of stream")) [true :continue])
                :error (fn [response throwable] (throw (Exception. (format-twitter-error-message response))) throwable)})

(def ^:dynamic *public-stream-api* "https://stream.twitter.com/1.1")
(def ^:dynamic *user-stream-api* "https://userstream.twitter.com/1.1")
(def ^:dynamic *site-stream-api* "https://sitestream.twitter.com/1.1")

(defmacro def-twitter-streaming-method
  "defines a streaming API method using *public-stream-api* as the default api
  with completed and error callbacks which both throw errors since Twitter streams are infinite"
  {:requires [#'def-twitter-method]}
  [name http-method resource-path & rest]
  `(def-twitter-method ~name ~http-method ~resource-path :api ~*public-stream-api* :callbacks callbacks ~@rest))

(def-twitter-streaming-method statuses-filter :post "statuses/filter.json")
(def-twitter-streaming-method statuses-firehose :get "statuses/firehose.json")
(def-twitter-streaming-method statuses-sample :get "statuses/sample.json")

(def-twitter-streaming-method user-stream :get "user.json" :api *user-stream-api*)

(def-twitter-streaming-method site-stream :get "site.json" :api *site-stream-api*)
