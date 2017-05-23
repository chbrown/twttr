(ns twttr.test.auth
  (:require [clojure.test :refer :all]
            [twttr.auth :as auth]))

(def user-credentials (auth/env->UserCredentials))
(def app-credentials (auth/env->AppCredentials))

(deftest user-credentials-available
  (let [{:keys [consumer-key consumer-secret user-token user-token-secret]} user-credentials]
    (is (some? consumer-key))
    (is (some? consumer-secret))
    (is (some? user-token))
    (is (some? user-token-secret))))

(deftest app-credentials-available
  (let [{:keys [consumer-key consumer-secret]} app-credentials]
    (is (some? consumer-key))
    (is (some? consumer-secret))))
