(ns twitter.test.core
  (:require [clojure.test :refer :all]
            [twitter.core]))

(deftest test-map-kv
  (is (= {"a" 0, "b" 1, "c" 2, "d" 3} (#'twitter.core/map-kv {:a 0 :b 1 :c 2 :d 3} name identity)))
  (is (= {:a 1 :b 2 :c 3 :d 4} (#'twitter.core/map-kv {:a 0 :b 1 :c 2 :d 3} identity inc))))

(deftest test-sub-uri
  (is (= (#'twitter.core/subs-uri "http://www.cnn.com/{:version}/{:id}/test.json" {:version 1, :id "my123"})
         "http://www.cnn.com/1/my123/test.json"))
  (is (= (#'twitter.core/subs-uri "http://www.cnn.com/nosubs.json" {:version 1, :id "my123"})
         "http://www.cnn.com/nosubs.json"))
  (is (thrown? java.lang.AssertionError (#'twitter.core/subs-uri "http://www.cnn.com/{:version}/{:id}/test.json" {:id "my123"}))))
