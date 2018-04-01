(ns twttr.middleware
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]

            [byte-streams :as bs]
            [manifold.deferred :as d]))

; The argument to middleware should be a function that takes a handler
; (which turns a request into a response), and returns a function which takes a request,
; potentially modifies that request, runs request through the given handler to get a response,
; and then returns the response, potentially after modifying it.

(defn wrap-rest
  "REST middleware for parsing response body as single JSON document"
  [handler]
  (fn rest-middleware-handler [request]
    (d/chain (handler request)
             (fn [response]
               (with-meta (-> (:body response)
                              (io/reader)
                              (json/read :key-fn keyword :eof-error? false)) response)))))

(defn wrap-stream
  "Streaming middleware for parsing response as infinite sequence of JSON documents,
  separated by newlines"
  [handler]
  (fn stream-middleware-handler [request]
    (d/chain (handler request)
             (fn [response]
               (with-meta (->> (:body response)
                               (bs/to-line-seq)
                               ; Twitter will send lots of newlines as keep-alive signals if the stream is sparse
                               (remove empty?)
                               (map #(json/read-str % :key-fn keyword))) response)))))
