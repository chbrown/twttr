(ns twttr.test.api
  (:require [clojure.test :refer :all]
            [twttr.api :refer :all]
            [twttr.test.auth :refer [user-credentials app-credentials]]))

(def current-user (delay (account-verify-credentials user-credentials)))

(defn http-ok?
  "checks if the response's HTTP status code is 200 (OK)"
  [body]
  (= 200 (:status (meta body))))

(defn is-http-ok?
  "tests if the response's HTTP status code is 200 (OK)"
  [body]
  (is (http-ok? body)))

(defn repeatedly-try
  "repeatedly tries f, up to some number of retries, sleeping in-between attempts, ignoring exceptions"
  [f & {:keys [max-tries wait-ms] :or {max-tries 10 wait-ms 250}}]
  (let [tries (repeatedly max-tries #(try (f) (catch Exception e nil)))
        ; wait between tries
        waits (repeatedly (dec max-tries) #(Thread/sleep wait-ms))]
    ; filter out the errors/waits
    (remove nil? (interleave tries waits))))

(deftest test-account
  (is (http-ok? (account-verify-credentials user-credentials)))
  (is (http-ok? (application-rate-limit-status user-credentials)))
  (is (http-ok? (application-rate-limit-status app-credentials)))
  (is (http-ok? (account-settings user-credentials))))

(deftest test-blocks
  (is (http-ok? (blocks-list user-credentials)))
  (is (http-ok? (blocks-ids user-credentials))))

(deftest test-timeline
  (is (http-ok? (statuses-mentions-timeline user-credentials)))
  (is (http-ok? (statuses-user-timeline user-credentials)))
  (is (http-ok? (statuses-home-timeline user-credentials)))
  (is (http-ok? (statuses-retweets-of-me user-credentials))))

(deftest test-statuses
  (is (http-ok? (statuses-lookup user-credentials :params {:id "20,432656548536401920"})))
  (let [status-id (get-in @current-user [:status :id])]
    (is (http-ok? (statuses-show-id user-credentials :params {:id status-id})))
    (is (http-ok? (statuses-show-id app-credentials :params {:id status-id})))
    (is (http-ok? (statuses-retweets-id user-credentials :params {:id status-id})))
    (is (http-ok? (statuses-retweets-id app-credentials :params {:id status-id})))))

(deftest test-search
  (is (http-ok? (search-tweets user-credentials :params {:q "clojure"})))
  (is (http-ok? (search-tweets app-credentials :params {:q "clojure"}))))

(deftest test-user
  (let [user-id (:id @current-user)]
    (is (http-ok? (users-show user-credentials :params {:user_id user-id})))
    (is (http-ok? (users-show app-credentials :params {:user_id user-id})))
    (is (http-ok? (users-lookup user-credentials :params {:user_id user-id})))
    (is (http-ok? (users-lookup app-credentials :params {:user_id user-id})))
    (is (http-ok? (users-suggestions user-credentials :params {:q "john smith"})))
    (is (http-ok? (users-suggestions app-credentials :params {:q "john smith"})))
    (is (http-ok? (users-suggestions-slug user-credentials :params {:slug "sports"})))
    (is (http-ok? (users-suggestions-slug-members user-credentials :params {:slug "sports"})))))

(deftest test-trends
  (is (http-ok? (trends-place user-credentials :params {:id 1})))
  (is (http-ok? (trends-place app-credentials :params {:id 1})))
  (is (http-ok? (trends-available user-credentials)))
  (is (http-ok? (trends-available app-credentials)))
  (is (http-ok? (trends-closest user-credentials :params {:lat 37.781157 :long -122.400612831116})))
  (is (http-ok? (trends-closest app-credentials :params {:lat 37.781157 :long -122.400612831116}))))

(deftest test-lists-list
  (is (http-ok? (lists-list user-credentials))))

(deftest test-lists-memberships
  (is (http-ok? (lists-memberships user-credentials))))

(deftest test-lists-subscriptions
  (is (http-ok? (lists-subscriptions user-credentials))))

(deftest test-lists-ownerships
  (is (http-ok? (lists-ownerships user-credentials))))

(defrecord List [credentials id]
  java.io.Closeable ; there's no clojure.core/ICloseable (yet?)
  (close [this] (lists-destroy credentials :params {:list_id id})))

(defn NewList
  [credentials params]
  (let [{:keys [id]} (lists-create credentials :params params)]
    ; wait until it's ready
    (first (repeatedly-try #(lists-show credentials :params {:list_id id})))
    (List. credentials id)))

(deftest test-lists-statuses
  (with-open [temp-list (NewList user-credentials {:name "Goonies", :mode "public"})]
    (is (http-ok? (lists-statuses user-credentials :params {:list_id (:id temp-list)})))))

(deftest test-list-members
  (with-open [temp-list (NewList user-credentials {:name "Goonies", :mode "public"})]
    (is (http-ok? (lists-members user-credentials :params {:list_id (:id temp-list)})))
    (is (thrown? Exception (lists-members-show :params {:list_id (:id temp-list)
                                                        :screen_name (:screen_name @current-user)})))))

(deftest test-list-subscribers
  (with-open [temp-list (NewList user-credentials {:name "Goonies", :mode "public"})]
    (is (http-ok? (lists-subscribers user-credentials :params {:list_id (:id temp-list)})))))

(deftest test-list-subscribers-show
  (with-open [temp-list (NewList user-credentials {:name "Goonies", :mode "public"})]
    (is (thrown? Exception (lists-subscribers-show :params {:list_id (:id temp-list)
                                                            :screen_name (:screen_name @current-user)})))))

(deftest test-direct-messages
  (is (http-ok? (direct-messages user-credentials)))
  (is (http-ok? (direct-messages-sent user-credentials))))

(deftest test-friendship
  (is (http-ok? (friendships-show user-credentials :params {:source_screen_name (:screen_name @current-user)
                                                            :target_screen_name "AdamJWynne"})))
  (is (http-ok? (friendships-show app-credentials :params {:source_screen_name (:screen_name @current-user)
                                                           :target_screen_name "AdamJWynne"})))
  (is (http-ok? (friendships-lookup user-credentials :params {:screen_name "peat,AdamJWynne"})))
  (is (http-ok? (friendships-incoming user-credentials)))
  (is (http-ok? (friendships-outgoing user-credentials))))

(deftest test-friends-followers
  (is (http-ok? (friends-ids user-credentials)))
  (is (http-ok? (friends-list user-credentials)))
  (is (http-ok? (followers-ids user-credentials)))
  (is (http-ok? (followers-list user-credentials))))

(deftest test-favourites
  (let [status-id (get-in @current-user [:status :id])]
    (is (http-ok? (favorites-create user-credentials :params {:id status-id})))
    (is (http-ok? (favorites-destroy user-credentials :params {:id status-id})))
    (is (http-ok? (favorites-list user-credentials)))))

(deftest test-saved-searches-list
  (is (http-ok? (saved-searches-list user-credentials))))

(defrecord SavedSearch [credentials id]
  java.io.Closeable
  (close [this] (saved-searches-destroy-id credentials :params {:id id})))

(defn NewSavedSearch
  [credentials params]
  (let [{:keys [id]} (saved-searches-create credentials :params params)]
    ; TODO: wait until it's ready
    (first (repeatedly-try #(saved-searches-show-id credentials :params {:id id})))
    (SavedSearch. credentials id)))

(deftest test-saved-searches-show-id
  (with-open [temp-saved-search (NewSavedSearch user-credentials {:query "sandwiches"})]
    (is (http-ok? (saved-searches-show-id user-credentials :params {:id (:id temp-saved-search)})))))

(deftest test-streaming-statuses-filter
  (let [statuses (statuses-filter user-credentials :params {:track "Twitter"})]
    (Thread/sleep 1000)
    ; cancel the response so that we don't stall here forever
    (-> statuses meta :body (.close))
    (is (http-ok? statuses))
    (is (some? (first statuses)))))

(deftest test-streaming-statuses-sample
  (let [statuses (statuses-sample user-credentials)]
    (Thread/sleep 1000)
    (-> statuses meta :body (.close))
    (is (http-ok? statuses))
    (is (some? (first statuses)))))

(deftest test-user-streaming
  (let [statuses (user-stream user-credentials)]
    (-> statuses meta :body (.close))
    ; the test users don't get much in their streams...
    (is (http-ok? statuses))))
