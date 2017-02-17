(ns twitter.api.search
  (:require [twitter.core :refer [def-twitter-method]]))

(def ^:dynamic *search-api* "https://api.twitter.com/1.1")

(defmacro def-twitter-search-method
  "defines a search method using the search api context and the synchronous comms"
  {:requires [#'def-twitter-method]}
  [name http-method resource-path & rest]
  `(def-twitter-method ~name ~http-method ~resource-path :api ~*search-api* :sync true ~@rest))

(def-twitter-search-method search :get "search/tweets.json")
