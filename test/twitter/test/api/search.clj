(ns twitter.test.api.search
  (:require [clojure.test :refer :all]
            [twitter.api.search :refer :all]
            [twitter.test-utils :refer [is-200]]))

(deftest test-search
  (is-200 search :params {:q "sports"}))
