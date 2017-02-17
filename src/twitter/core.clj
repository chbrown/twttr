(ns twitter.core
  (:require [clojure.string :as string]
            [clojure.data.json :as json]
            [http.async.client :as http]
            [http.async.client.request :refer [prepare-request execute-request]]
            [twitter.oauth :refer [oauth-header-string sign-query]]))

(defn- map-kv
  "transforms the k/v pairs of a map using a supplied transformation function"
  [m key-fn val-fn]
  (into {} (for [[k v] m] [(key-fn k) (val-fn v)])))

(defn- hyphen->underscore
  "Replaces each - with a _"
  [s]
  (string/replace s \- \_))

(defn- flatten-to-csv
  "Turns collections into comma-separated strings; preserves other values"
  [val]
  (if (coll? val) (string/join "," val) val))

(defn- subs-uri
  "substitutes parameters for tokens in the uri"
  [uri params]
  (string/replace uri #"\{\:(\w+)\}"
                  (fn [[_ kw]]
                    (let [value (get params (keyword kw))]
                      (assert value (format "%s needs :%s param to be supplied" uri kw))
                      (str value)))))

(defn format-twitter-error-message
  "read an error response into a string error message"
  [response]
  (let [status-code (:code (http/status response))
        body (json/read-json (http/string response))
        desc (or (:message (first (:errors body))) (:error body))
        code (or (:code (first (:errors body))) status-code)
        req (:request body)]
    (cond
      (= 429 status-code) (format "Twitter responded with error 88: Rate limit exceeded. Next reset at %s (UTC epoch seconds)" (-> response http/headers :x-rate-limit-reset))
      (and req code desc) (format "Twitter responded '%s' with error %d: %s" req code desc)
      (and code desc) (format "Twitter responded with error %d: %s" code desc)
      desc (format "Twitter responded with error: %s" desc)
      :default "Twitter responded with an unknown error")))

(def ^:private default-client (delay (http/create-client :follow-redirects false :request-timeout -1)))

(defn- await-response
  "this takes a response and returns a map of the headers, status, and body (as a string)"
  [response]
  (http/await response)
  {:status (http/status response)
   :headers (http/headers response)
   :body (http/string response)})

(defn- transform-sync-response
  [response]
  (if (< (:code (http/status response)) 400)
    (update (await-response response) :body #(json/read-str % :key-fn keyword))
    (throw (Exception. (format-twitter-error-message response)))))

(defn execute-api-request
  "calls the HTTP method on the resource specified in the uri, signing with oauth in the headers
   you can supply args for async.http.client (e.g. :query, :body, :headers etc).

   takes uri, HTTP method and optional args and returns the final uri and http parameters for the subsequent call.
   Note that the params are transformed (from lispy -'s to x-header-style _'s) and added to the query. So :params
   could be {:screen-name 'blah'} and it be merged into :query as {:screen_name 'blah'}. The uri has the params
   substituted in (so {:id} in the uri will use the :id in the :params map). Also, the oauth headers are added
   if required."
  [http-method uri {:keys [params body query oauth-creds headers client callbacks sync]
                    :or {client @default-client
                         callbacks {}
                         sync false}
                    :as arg-map}]
  (let [params (map-kv params (comp keyword hyphen->underscore name) flatten-to-csv)
        uri (subs-uri uri params)
        query (merge query params)
        ; request-args (get-request-args http-method uri arg-map)
        oauth-map (if (contains? oauth-creds :bearer)
                    oauth-creds ;; no need to sign for app-only auth
                    (sign-query oauth-creds http-method uri :query query))
        headers (merge headers
                       (when oauth-map {:Authorization (oauth-header-string oauth-map)})
                       (when (vector? body) {:Content-Type "multipart/form-data"}))
        request (prepare-request http-method uri :query query :headers headers :body body)
        response (apply execute-request client request (apply concat callbacks))]
        ; other-args (merge (dissoc arg-map :query :headers :body :params :oauth-creds :client :api :callbacks)]
    (if sync (transform-sync-response response) response)))

(defmacro def-twitter-method
  "Declares a twitter method with the supplied name, HTTP method and relative resource path.
   As part of the specification, it must have an :api member of the 'rest' list.
   From these it creates a uri, the api context and relative resource path. The default callbacks that are
   supplied, determine how to make the call (in terms of the sync/async or single/streaming)"
  [fn-name default-http-method resource-path & rest]
  (let [rest-map (apply sorted-map rest)]
    `(defn ~fn-name
       [& {:as args#}]
       (let [arg-map# (merge ~rest-map args#)
             api-prefix# (or (:api arg-map#) (throw (Exception. "must include an ':api' entry in the params")))
             http-method# (or (:http-method args#) ~default-http-method)
             ; makes a uri from a supplied protocol, site, version and resource-path
             uri# (str api-prefix# "/" ~resource-path)]
         (execute-api-request http-method# uri# arg-map#)))))
