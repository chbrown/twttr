(ns twitter.api.streaming
  (:require [twitter.core :refer [def-twitter-method
                                  format-twitter-error-message]]))

(def callbacks {:completed (fn [response] (throw (Exception. "Unexpected completion of stream")) [true :continue])
                :error (fn [response throwable] (throw (Exception. (format-twitter-error-message response))) throwable)})

(def ^:dynamic *streaming-api* "https://stream.twitter.com/1.1")

(defmacro def-twitter-streaming-method
  "defines a streaming API method using the above api context"
  {:requires [#'def-twitter-method]}
  [name http-method resource-path & rest]
  `(def-twitter-method ~name ~http-method ~resource-path :api ~*streaming-api* :callbacks callbacks ~@rest))

(def-twitter-streaming-method statuses-filter :post "statuses/filter.json")
(def-twitter-streaming-method statuses-firehose :get "statuses/firehose.json")
(def-twitter-streaming-method statuses-sample :get "statuses/sample.json")

(def ^:dynamic *user-stream-api* "https://userstream.twitter.com/1.1")

(defmacro def-twitter-user-streaming-method
  "defines a user streaming method using the above context"
  {:requires [#'def-twitter-method]}
  [name http-method resource-path & rest]
  `(def-twitter-method ~name ~http-method ~resource-path :api ~*user-stream-api* :callbacks callbacks ~@rest))

(def-twitter-user-streaming-method user-stream :get "user.json")

(def ^:dynamic *site-stream-api* "https://sitestream.twitter.com/1.1")

(defmacro def-twitter-site-streaming-method
  "defines a site streaming method using the above context"
  {:requires [#'def-twitter-method]}
  [name http-method resource-path & rest]
  `(def-twitter-method ~name ~http-method ~resource-path :api ~*site-stream-api* :callbacks callbacks ~@rest))

(def-twitter-site-streaming-method site-stream :get "site.json")
