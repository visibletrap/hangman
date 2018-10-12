(defproject hangman "2.0.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.0-RC1"]
                 [org.clojure/core.async "0.4.474"]
                 [aleph "0.4.6"]
                 [bidi "2.1.4"]
                 [yada "1.2.14"]
                 [environ "1.0.0"]]
  :plugins [[environ/environ.lein "0.3.1"]]
  :uberjar-name "hangman.jar"
  :min-lein-version "2.0.0"
  :hooks [environ.leiningen.hooks]
  :profiles {:uberjar {:aot :all}})
