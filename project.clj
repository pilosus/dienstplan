(defproject dienstplan "0.1.0-SNAPSHOT"
  :description "Rotation duty slack bot"
  :url "https://github.com/pilosus/dienstplan"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [
                 ;; Clojure
                 [org.clojure/clojure "1.10.3"]

                 ;; Web Framework
                 [ring/ring-core "1.9.4"]
                 [ring/ring-jetty-adapter "1.9.4"]
                 [ring/ring-json "0.5.1"]

                 ;; Routing
                 [bidi "2.1.6"]

                 ;; Alerts
                 [io.sentry/sentry-clj "5.2.158"]

                 ;; JSON parsing
                 [cheshire "5.10.1"]]

  :plugins [[lein-cljfmt "0.8.0"]
            [lein-cloverage "1.2.2"]
            [lein-licenses "0.2.2"]]
  :main dienstplan.core
  :aot [dienstplan.core]
  :target-path "target/%s"
  :repl-options {:init-ns dienstplan.core})
