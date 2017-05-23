(ns user
  (:require [clojure.repl :refer :all]
            [clojure.pprint :refer :all]
            [clojure.string :as str]
            [clojure.java.io :as io]

            [twttr.api :as api]
            [twttr.auth :as auth]
            [clojure.tools.namespace.repl :refer [refresh]]))

(def app (auth/env->AppCredentials))
(def user (auth/env->UserCredentials))

; (println "Current authenticated user:")
; (pprint (api/account-verify-credentials user))
