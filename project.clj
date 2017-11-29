(defproject twttr "2.0.0"
  :description "Twitter API client supporting REST, streaming, and OAuth"
  :url "https://github.com/chbrown/twttr"
  :license {:name "Eclipse Public License"
            :url "https://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [aleph "0.4.4"]
                 [clj-oauth "1.5.5"]]
  :codox {:exclude-vars nil
          :source-paths ["src"]
          :source-uri "https://github.com/chbrown/twttr/blob/{version}/{filepath}#L{line}"}
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[org.clojure/tools.namespace "0.3.0-alpha3"]]
                   :source-paths ["dev" "src"]
                   :repl-options {:init-ns user}}})
