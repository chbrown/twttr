(ns twitter.test.api.streaming
  (:require [clojure.test :refer :all]
            [http.async.client :as http]
            [twitter.test-utils :refer [user-creds]]
            [twitter.api.streaming :refer [statuses-filter
                                           statuses-sample
                                           user-stream]]))

(defmacro is-async-200
  "checks to see if the response is HTTP return code 200, and then cancels it"
  {:requires [#'is http/status http/cancel]}
  [fn-name & args]
  `(let [response# (~fn-name :oauth-creds ~user-creds ~@args)]
     (is (= 200 (:code (http/status response#))))
     (http/cancel (meta response#))))

(deftest test-streaming-statuses-filter
  (is-async-200 statuses-filter :params {:track "Twitter"}))

(deftest test-streaming-statuses-sample
  (is-async-200 statuses-sample))

(deftest test-user-streaming
  (is-async-200 user-stream))
