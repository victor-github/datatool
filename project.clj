(defproject datatool "0.0.1"
  :description "A general data tool."
  :dependencies
  [[org.clojure/clojure "1.2.0"]
   [org.clojure/clojure-contrib "1.2.0"]
   [ring/ring-core "0.3.0"]
   [ring/ring-devel "0.3.0"]
   [ring/ring-httpcore-adapter "0.2.5"]
   [ring/ring-jetty-adapter "0.2.5"]
   [compojure "0.5.3"] ;was: 0.5.0
   [hiccup "0.2.6"]
   [congomongo "0.1.3-SNAPSHOT"]
   [clj-http "0.1.1"]
   [clj-time "0.2.0-SNAPSHOT"]
   [sandbar/sandbar "0.3.0-SNAPSHOT"]
   [jline "0.9.94"]]
  :dev-dependencies
  [[lein-run "1.0.0-SNAPSHOT"]
   [swank-clojure "1.2.0"]])

