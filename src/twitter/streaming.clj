(ns twitter.streaming
  (:require [http.async.client :as http])
  (:import (java.io ByteArrayOutputStream)
           (java.util.concurrent LinkedBlockingQueue)))

(defn create-streaming-callbacks
  []
  (let [queue (LinkedBlockingQueue.)]
    {:part (fn [_ ^ByteArrayOutputStream baos]
             (.put queue baos)
             [queue :continue])
     :completed (fn [_]
                  (.put queue ::http/done))
     :error (fn [_ t]
              (.put queue ::http/done)
              t)}))
