(ns twitter.test.core
  (:require [clojure.test :refer :all]
            [twitter.core :refer [->UserCredentials ->AppCredentials]]))

(def env-credentials
  (map #(System/getenv %) ["CONSUMER_KEY" "CONSUMER_SECRET" "ACCESS_TOKEN" "ACCESS_TOKEN_SECRET"]))

(deftest credentials-available
  (let [[consumer-key consumer-secret user-token user-token-secret] env-credentials]
    (is (some? consumer-key))
    (is (some? consumer-secret))
    (is (some? user-token))
    (is (some? user-token-secret))))

(let [[consumer-key consumer-secret user-token user-token-secret] env-credentials]
  (def ^:dynamic *user* (->UserCredentials consumer-key consumer-secret user-token user-token-secret))
  (def ^:dynamic *app* (->AppCredentials consumer-key consumer-secret)))
