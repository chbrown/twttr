(defproject twttr "3.2.2"
  :description "Twitter API client supporting REST, streaming, and OAuth"
  :url "https://github.com/chbrown/twttr"
  :license {:name "Eclipse Public License"
            :url "https://www.eclipse.org/legal/epl-v10.html"}
  :deploy-repositories [["releases" :clojars]]
  :pom-addition [:developers [:developer
                              [:name "Christopher Brown"]
                              [:email "io@henrian.com"]]]
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.logging "0.4.1"]
                 [aleph "0.4.6"]
                 [byte-streams "0.2.4"]
                 [clj-oauth "1.5.5"]]
  :codox {:exclude-vars nil
          :source-uri "https://github.com/chbrown/twttr/blob/{version}/{filepath}#L{line}"}
  :profiles {:uberjar {:aot :all}
             :deploy {:plugins [[lein-codox "0.10.6"]]}
             :dev {:plugins [[lein-cloverage "1.1.1"]]
                   :dependencies [[ch.qos.logback/logback-classic "1.2.3"]]
                   :resource-paths ["dev-resources"]
                   :source-paths ["test"]}
             :repl {:dependencies [[org.clojure/tools.namespace "0.3.0-alpha3"]]
                    :source-paths ["dev"]}})
