(defproject twitter-api/twitter-api "2.0.0-alpha1-SNAPSHOT"
  :description "Async interface to Twitter's REST and Streaming APIs"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/data.codec "0.1.0"]
                 [http.async.client "1.2.0"]
                 [clj-oauth "1.5.5"]]
  :url "https://github.com/adamwynne/twitter-api"
  :license {:name "Eclipse Public License"
            :url "https://www.eclipse.org/legal/epl-v10.html"}
  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :sign-releases false}]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.3.0-alpha3"]
                                  [org.clojure/tools.logging "0.3.1"]
                                  [org.clojure/tools.cli "0.3.5"]
                                  [ch.qos.logback/logback-classic "1.2.3"]]
                   :source-paths ["dev" "src"]
                   :repl-options {:init-ns user}}})
