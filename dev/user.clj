(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [twitter.test.api :refer [repeatedly-try]]
            [twitter.api :refer :all]
            [twitter.core :refer [->UserCredentials]]))

(def ^:dynamic *user* (->> ["CONSUMER_KEY" "CONSUMER_SECRET" "ACCESS_TOKEN" "ACCESS_TOKEN_SECRET"]
                           (map #(System/getenv %))
                           (apply ->UserCredentials)))

; (account-verify-credentials *user*)

(defn make-list [name]
  (let [new-list (:body (lists-create *user* :params {:name name}))]
    (repeatedly-try #(do (println "polling lists-show for successful response")
                         (lists-show *user* :params {:list_id (:id new-list)})))
    new-list))

(defn delete-list [id]
  (lists-destroy *user* :params {:list_id id}))

; delete all lists:
; (->> (lists-ownerships *user* :params {:count 1000})
;      :body
;      :lists
;      (map (comp delete-list :id)))
