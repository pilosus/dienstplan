;; Based on the tools.build Guide's examples
;; https://clojure.org/guides/tools_build
;; https://clojure.github.io/tools.build/clojure.tools.build.api.html

(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'org.pilosus/dienstplan)
(def main 'dienstplan.core)
(def version (format "1.0.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def src-dirs ["src" "resources"])
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def uber-file (format "target/uberjar/%s-%s-standalone.jar" (name lib) version))

(defn- build-opts
  [opts]
  (merge
   {:class-dir class-dir
    :lib lib
    :version version
    :basis basis
    :src-dirs src-dirs
    :target-dir class-dir ;; b/copy-dir specific
    :jar-file jar-file    ;; b/jar specific
    :ns-compile [main]    ;; b/compile-clj specific
    :uber-file uber-file  ;; b/uber specific
    :main main            ;; b/uber specific
    }
   opts))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar
  [opts]
  (let [opts' (build-opts opts)]
    (println "Writing pom file...")
    (b/write-pom opts')

    (println "Copying files...")
    (b/copy-dir opts')

    (println "Creating jar...")
    (b/jar opts')))

(defn uberjar
  "Create an uberjar file with possible options override

  Examples:
  clojure -T:build uberjar
  clojure -T:build uberjar :uber-file '\"target/app.jar\"'
  "
  [opts]
  (println "Cleaning...")
  (clean nil)
  (let [opts' (build-opts opts)]
    (println "Copying files...")
    (b/copy-dir opts')

    (println "Compiling...")
    (b/compile-clj opts')

    (println "Creating uberjar...")
    (b/uber opts')))
