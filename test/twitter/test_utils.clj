(ns twitter.test-utils
  (:require [clojure.test :refer :all]
            [http.async.client :as http]
            [twitter.oauth :refer [make-oauth-creds]]
            [twitter.api.restful :refer [users-show]]))

(def ^:private env-credentials
  (map #(System/getenv %) ["CONSUMER_KEY" "CONSUMER_SECRET" "ACCESS_TOKEN" "ACCESS_TOKEN_SECRET"]))

(def ^:dynamic *user-screen-name* (System/getenv "SCREEN_NAME"))

; "makes an Oauth structure that uses an app's credentials and a users's credentials"
(def user-creds
  (let [[app-key app-secret user-token user-token-secret] env-credentials]
    (make-oauth-creds app-key app-secret user-token user-token-secret)))

; "makes an Oauth structure that uses only an app's credentials"
(def app-creds
  (let [[app-key app-secret] env-credentials]
    (make-oauth-creds app-key app-secret)))

(deftest credentials-available
  (let [[app-key app-secret user-token user-token-secret] env-credentials]
    (is (some? app-key))
    (is (some? app-secret))
    (is (some? user-token))
    (is (some? user-token-secret))))

(defmacro is-http-code
  "checks to see if the response is a specific HTTP return code"
  {:requires [#'is]}
  [code fn-name & args]
  `(is (= ~code (get-in (~fn-name :oauth-creds ~user-creds ~@args) [:status :code]))))

(defmacro is-200-with-app-only
  "checks to see if the response to a request using application-only
  authentication is a specific HTTP return code"
  {:requires [#'is]}
  [fn-name & args]
  `(is (= 200 (get-in (~fn-name :oauth-creds ~app-creds ~@args) [:status :code]))))

(defmacro is-200
  "checks to see if the response is HTTP 200"
  [fn-name & args]
  (if (some #{:app-only} args)
    (let [args# (remove #{:app-only} args)]
      `(is-200-with-app-only ~fn-name ~@args#))
    `(is-http-code 200 ~fn-name ~@args)))

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
