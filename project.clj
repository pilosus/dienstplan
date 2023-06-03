(defproject dienstplan "0.4.0"
  :description "Duty rotation slack bot"
  :url "https://github.com/pilosus/dienstplan"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [
                 ;; Clojure
                 [org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.cli "1.0.219"]

                 ;; Web Framework
                 [ring/ring-core "1.10.0"]
                 [ring/ring-jetty-adapter "1.10.0"]
                 [ring/ring-json "0.5.1"]

                 ;; Routing
                 [bidi "2.1.6"]

                 ;; Logging
                 [org.clojure/tools.logging "1.2.4"]
                 [ch.qos.logback/logback-classic "1.4.7"]

                 ;; Alerts
                 ;; FIXME version 6 and its underlying Java SDK seem to rely on jdk.unsupported
                 ;; that aren't in temurin JRE 17
                 [io.sentry/sentry-clj "5.7.180"]
                 ;; In sentry-clj 5.5.165 dsn string must be non-blank
                 ;; in the presense of context map:
                 ;; https://github.com/getsentry/sentry-clj/compare/5.5.164...5.5.165#diff-e3b4ff05b98b80c7fa2082652c8510acf99a4bbf293d3e035b703b9720f4ca90R253
                 ;; [io.sentry/sentry-clj "5.5.164"]

                 ;; Validation
                 [expound "0.9.0"]

                 ;; Config managements
                 [exoscale/yummy "0.2.11"]
                 [mount "0.1.17"]

                 ;; HTTP client
                 [clj-http "3.12.3"]

                 ;; JSON parsing
                 [cheshire "5.11.0"]

                 ;; DB
                 [org.clojure/java.jdbc "0.7.12"]
                 [org.postgresql/postgresql "42.6.0"]
                 [hikari-cp "3.0.1"]
                 [dev.weavejester/ragtime "0.9.3"]
                 [com.github.seancorfield/honeysql "2.4.1026"]

                 ;; Cryptography
                 [buddy/buddy-core "1.11.418"]]
  :plugins [[lein-cljfmt "0.8.0"]
            [lein-cloverage "1.2.2"]
            [lein-licenses "0.2.2"]
            [lein-ancient "1.0.0-RC3"]]
  :main dienstplan.core
  :aot [dienstplan.core]
  :target-path "target/%s"
  :repl-options {:init-ns dienstplan.core}
  :test-selectors {:integration :integration
                   :unit (complement :integration)}
  :aliases {"migrate"  ["run" "-m" "dienstplan.db/migrate"]
            "rollback" ["run" "-m" "dienstplan.db/rollback"]})
