(ns twitter.test.core
  (:require [clojure.test :refer :all]
            [twitter.oauth :refer [oauth-header-string sign-query]]
            [twitter.test-utils :refer [user-creds]]))

(deftest test-map-kv
  (is (= {"a" 0, "b" 1, "c" 2, "d" 3} (#'twitter.core/map-kv {:a 0 :b 1 :c 2 :d 3} name identity)))
  (is (= {:a 1 :b 2 :c 3 :d 4} (#'twitter.core/map-kv {:a 0 :b 1 :c 2 :d 3} identity inc))))

(deftest test-sign-query
  (let [result (sign-query user-creds :get "http://www.cnn.com" :query {:test-param "true"})]
    (is (:oauth_signature result))))

(deftest test-oauth-header-string
  (is (= (oauth-header-string {:a 1 :b 2 :c 3}) "OAuth c=\"3\",b=\"2\",a=\"1\""))
  (is (= (oauth-header-string {:a "hi there"}) "OAuth a=\"hi%20there\""))
  (is (= (oauth-header-string {:a "hi there"} :url-encode? nil) "OAuth a=\"hi there\""))
  (is (= (oauth-header-string {:bearer "hello"}) "Bearer hello")))

(deftest test-sub-uri
  (is (= (#'twitter.core/subs-uri "http://www.cnn.com/{:version}/{:id}/test.json" {:version 1, :id "my123"})
         "http://www.cnn.com/1/my123/test.json"))
  (is (= (#'twitter.core/subs-uri "http://www.cnn.com/nosubs.json" {:version 1, :id "my123"})
         "http://www.cnn.com/nosubs.json"))
  (is (thrown? java.lang.AssertionError (#'twitter.core/subs-uri "http://www.cnn.com/{:version}/{:id}/test.json" {:id "my123"}))))
