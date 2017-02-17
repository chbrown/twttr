(ns twitter.test-utils
  (:require [clojure.test :refer :all]
            [twitter.api.restful :refer [users-show]]
            [twitter.oauth :refer [make-oauth-creds]]))

(def ^:private env-credentials
  (map #(System/getenv %) ["CONSUMER_KEY" "CONSUMER_SECRET" "ACCESS_TOKEN" "ACCESS_TOKEN_SECRET"]))

(def ^:dynamic *user-screen-name* (System/getenv "SCREEN_NAME"))

(def user-creds
  (let [[consumer-key consumer-secret user-token user-token-secret] env-credentials]
    (make-oauth-creds consumer-key consumer-secret user-token user-token-secret)))

(def app-creds
  (let [[consumer-key consumer-secret] env-credentials]
    (make-oauth-creds consumer-key consumer-secret)))

(deftest credentials-available
  (let [[consumer-key consumer-secret user-token user-token-secret] env-credentials]
    (is (some? consumer-key))
    (is (some? consumer-secret))
    (is (some? user-token))
    (is (some? user-token-secret))))

(defmacro is-http-code
  "checks to see if the response to an authenticated request is a specific HTTP return code"
  {:requires [#'is]}
  [code fn-name creds & args]
  `(is (= ~code (get-in (~fn-name :oauth-creds ~creds ~@args) [:status :code]))))

(defmacro is-200
  "checks to see if the response is HTTP 200"
  [fn-name & args]
  (if (some #{:app-only} args)
    `(is-http-code 200 ~fn-name ~app-creds ~@(remove #{:app-only} args))
    `(is-http-code 200 ~fn-name ~user-creds ~@args)))

(defn get-user-id
  "gets the id of the supplied screen name"
  [screen-name]
  (get-in (users-show :oauth-creds user-creds :params {:screen-name screen-name})
          [:body :id]))

(defn get-current-status-id
  "gets the id of the current status for the supplied screen name"
  [screen-name]
  (let [result (users-show :oauth-creds user-creds :params {:screen-name screen-name})
        status-id (get-in result [:body :status :id])]
    (assert status-id "could not retrieve the user's profile in 'show-user'")
    status-id))

(defn poll-until-no-error
  "repeatedly tries the poll instruction, for a maximum time, or until the error disappears"
  [poll-fn & {:keys [max-timeout-ms wait-time-ms]
              :or {max-timeout-ms 60000 wait-time-ms 10000}}]
  (loop [curr-time-ms 0]
    (if (< curr-time-ms max-timeout-ms)
      (when-not (try (poll-fn) (catch Exception e nil))
        (Thread/sleep wait-time-ms)
        (recur (+ curr-time-ms wait-time-ms))))))

(defmacro with-setup-poll-teardown
  [var-name setup poll teardown & body]
  `(let [~var-name ~setup]
     (try (poll-until-no-error (fn [] ~poll))
          ~@body
          (finally ~teardown))))
