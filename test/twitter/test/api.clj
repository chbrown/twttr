(ns twitter.test.api
  (:require [clojure.test :refer :all]
            [http.async.client :as http]
            [twitter.api :refer :all]
            [twitter.test.core :refer [*user* *app*]]))

(deftest test-replace-pattern-params
  ; fully specified replacements
  (let [pattern "http://example.com/{:version}/{:id}/test.json"
        params {:version 1, :id "my123"}
        result (#'twitter.api/replace-pattern-params pattern params)]
    (is (= "http://example.com/1/my123/test.json" result)))
  ; no replacements but with extra params
  (let [pattern "http://example.com/no-replacements.json"
        params {:version 1, :id "my123"}
        result (#'twitter.api/replace-pattern-params pattern params)]
    (is (= "http://example.com/no-replacements.json" result)))
  ; one missing replacement
  (let [pattern "http://example.com/{:version}/{:id}/test.json"
        params {:id "my123"}
        result (#'twitter.api/replace-pattern-params pattern params)]
    (is (= "http://example.com//my123/test.json" result))))

(def current-user (delay (:body (account-verify-credentials *user*))))

(defn http-ok?
  "checks if the response's HTTP status code is 200 (OK)"
  [response]
  (= 200 (get-in response [:status :code])))

(defn is-http-ok?
  "tests if the response's HTTP status code is 200 (OK)"
  [response]
  (is (http-ok? response)))

(defn repeatedly-try
  "repeatedly tries f, up to some number of retries, sleeping in-between attempts, ignoring exceptions"
  [f & {:keys [max-tries wait-ms] :or {max-tries 10 wait-ms 250}}]
  (let [tries (repeatedly max-tries #(try (f) (catch Exception e nil)))
        ; wait between tries
        waits (repeatedly (dec max-tries) #(Thread/sleep wait-ms))]
    ; filter out the errors/waits
    (remove nil? (interleave tries waits))))

(deftest test-account
  (is (http-ok? (account-verify-credentials *user*)))
  (is (http-ok? (application-rate-limit-status *user*)))
  (is (http-ok? (application-rate-limit-status *app*)))
  (is (http-ok? (account-settings *user*))))

(deftest test-blocks
  (is (http-ok? (blocks-list *user*)))
  (is (http-ok? (blocks-ids *user*))))

(deftest test-timeline
  (is (http-ok? (statuses-mentions-timeline *user*)))
  (is (http-ok? (statuses-user-timeline *user*)))
  (is (http-ok? (statuses-home-timeline *user*)))
  (is (http-ok? (statuses-retweets-of-me *user*))))

(deftest test-statuses
  (is (http-ok? (statuses-lookup *user* :params {:id "20,432656548536401920"})))
  (let [status-id (get-in @current-user [:status :id])]
    (is (http-ok? (statuses-show-id *user* :params {:id status-id})))
    (is (http-ok? (statuses-show-id *app* :params {:id status-id})))
    (is (http-ok? (statuses-retweets-id *user* :params {:id status-id})))
    (is (http-ok? (statuses-retweets-id *app* :params {:id status-id})))))

(deftest test-search
  (is (http-ok? (search-tweets *user* :params {:q "clojure"})))
  (is (http-ok? (search-tweets *app* :params {:q "clojure"}))))

(deftest test-user
  (let [user-id (:id @current-user)]
    (is (http-ok? (users-show *user* :params {:user_id user-id})))
    (is (http-ok? (users-show *app* :params {:user_id user-id})))
    (is (http-ok? (users-lookup *user* :params {:user_id user-id})))
    (is (http-ok? (users-lookup *app* :params {:user_id user-id})))
    (is (http-ok? (users-suggestions *user* :params {:q "john smith"})))
    (is (http-ok? (users-suggestions *app* :params {:q "john smith"})))
    (is (http-ok? (users-suggestions-slug *user* :params {:slug "sports"})))
    (is (http-ok? (users-suggestions-slug-members *user* :params {:slug "sports"})))))

(deftest test-trends
  (is (http-ok? (trends-place *user* :params {:id 1})))
  (is (http-ok? (trends-place *app* :params {:id 1})))
  (is (http-ok? (trends-available *user*)))
  (is (http-ok? (trends-available *app*)))
  (is (http-ok? (trends-closest *user* :params {:lat 37.781157 :long -122.400612831116})))
  (is (http-ok? (trends-closest *app* :params {:lat 37.781157 :long -122.400612831116}))))

(deftest test-lists-list
  (is (http-ok? (lists-list *user*))))

(deftest test-lists-memberships
  (is (http-ok? (lists-memberships *user*))))

(deftest test-lists-subscriptions
  (is (http-ok? (lists-subscriptions *user*))))

(deftest test-lists-ownerships
  (is (http-ok? (lists-ownerships *user*))))

(defrecord List [credentials id]
  java.io.Closeable ; there's no clojure.core/ICloseable (yet?)
  (close [this] (lists-destroy credentials :params {:list_id id})))

(defn NewList
  [credentials params]
  (let [id (get-in (lists-create credentials :params params) [:body :id])]
    ; wait until it's ready
    (first (repeatedly-try #(lists-show credentials :params {:list_id id})))
    (List. credentials id)))

(deftest test-lists-statuses
  (with-open [temp-list (NewList *user* {:name "Goonies", :mode "public"})]
    (is (http-ok? (lists-statuses *user* :params {:list_id (:id temp-list)})))))

(deftest test-list-members
  (with-open [temp-list (NewList *user* {:name "Goonies", :mode "public"})]
    (is (http-ok? (lists-members *user* :params {:list_id (:id temp-list)})))
    (is (thrown? Exception (lists-members-show :params {:list_id (:id temp-list)
                                                        :screen_name (:screen_name @current-user)})))))

(deftest test-list-subscribers
  (with-open [temp-list (NewList *user* {:name "Goonies", :mode "public"})]
    (is (http-ok? (lists-subscribers *user* :params {:list_id (:id temp-list)})))))

(deftest test-list-subscribers-show
  (with-open [temp-list (NewList *user* {:name "Goonies", :mode "public"})]
    (is (thrown? Exception (lists-subscribers-show :params {:list_id (:id temp-list)
                                                            :screen_name (:screen_name @current-user)})))))

(deftest test-direct-messages
  (is (http-ok? (direct-messages *user*)))
  (is (http-ok? (direct-messages-sent *user*))))

(deftest test-friendship
  (is (http-ok? (friendships-show *user* :params {:source_screen_name (:screen_name @current-user)
                                                  :target_screen_name "AdamJWynne"})))
  (is (http-ok? (friendships-show *app* :params {:source_screen_name (:screen_name @current-user)
                                                 :target_screen_name "AdamJWynne"})))
  (is (http-ok? (friendships-lookup *user* :params {:screen_name "peat,AdamJWynne"})))
  (is (http-ok? (friendships-incoming *user*)))
  (is (http-ok? (friendships-outgoing *user*))))

(deftest test-friends-followers
  (is (http-ok? (friends-ids *user*)))
  (is (http-ok? (friends-list *user*)))
  (is (http-ok? (followers-ids *user*)))
  (is (http-ok? (followers-list *user*))))

(deftest test-favourites
  (let [status-id (get-in @current-user [:status :id])]
    (is (http-ok? (favorites-create *user* :params {:id status-id})))
    (is (http-ok? (favorites-destroy *user* :params {:id status-id})))
    (is (http-ok? (favorites-list *user*)))))

(deftest test-saved-searches-list
  (is (http-ok? (saved-searches-list *user*))))

(defrecord SavedSearch [credentials id]
  java.io.Closeable
  (close [this] (saved-searches-destroy-id credentials :params {:id id})))

(defn NewSavedSearch
  [credentials params]
  (let [id (get-in (saved-searches-create credentials :params params) [:body :id])]
    ; TODO: wait until it's ready
    (first (repeatedly-try #(saved-searches-show-id credentials :params {:id id})))
    (SavedSearch. credentials id)))

(deftest test-saved-searches-show-id
  (with-open [temp-saved-search (NewSavedSearch *user* {:query "sandwiches"})]
    (is (http-ok? (saved-searches-show-id *user* :params {:id (:id temp-saved-search)})))))

(deftest test-streaming-statuses-filter
  (with-open [client (http/create-client)]
    (let [response (statuses-filter *user* :params {:track "Twitter"} :client client)]
      (is (= 200 (:code (http/status response))))
      ; cancel the response so that we don't stall here forever
      (http/cancel (meta response)))))

(deftest test-streaming-statuses-sample
  (with-open [client (http/create-client)]
    (let [response (statuses-sample *user* :client client)]
      (is (= 200 (:code (http/status response))))
      (http/cancel (meta response)))))

(deftest test-user-streaming
  (with-open [client (http/create-client)]
    (let [response (user-stream *user* :client client)]
      (is (= 200 (:code (http/status response))))
      (http/cancel (meta response)))))
