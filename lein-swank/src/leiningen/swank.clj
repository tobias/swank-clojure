(ns leiningen.swank
  "Launch swank server for Emacs to connect."
  (:require [clojure.java.io :as io]))

(defn swank-form [project port host opts]
  ;; bootclasspath workaround: http://dev.clojure.org/jira/browse/CLJ-673
  (when (:eval-in-leiningen project)
    (require '[clojure walk template stacktrace]))
  `(do
     (when-let [is# ~(:repl-init-script project)]
       (when (.exists (java.io.File. (str is#)))
         (load-file is#)))
     (when-let [repl-init# '~(:repl-init project)]
       (doto repl-init# require in-ns))
     (require '~'swank.swank)
     (require '~'swank.commands.basic)
     (@(ns-resolve '~'swank.swank '~'start-server)
      ~@(concat (map read-string opts)
                [:host host :port (Integer. port)
                 :repl-out-root true :block true]))))

(def ^{:private true} jvm-opts
  "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n")

(defn- add-cdt-jvm-opts [project]
  (if (seq (filter #(re-find #"jdwp" %)
                   (:jvm-opts project)))
    project
    (update-in project [:jvm-opts] conj jvm-opts)))

(defn- add-cdt-project-args
  "CDT requires the JDK's tools.jar and sa-jdi.jar. Add them to the classpath."
  [project]
  (if (:swank-cdt project true)
    (let [libdir (io/file (System/getProperty "java.home") ".." "lib")
          extra-cp (for [j ["tools.jar" "sa-jdi.jar"]
                         :when (.exists (io/file libdir j))]
                     (.getAbsolutePath (io/file libdir j)))]
      (-> project
          (update-in [:extra-classpath-dirs] concat extra-cp)
          add-cdt-jvm-opts))
    project))

(defn eval-in-project
  "Support eval-in-project in both Leiningen 1.x and 2.x."
  [& args]
  (let [eip (try (require 'leiningen.compile)
                 (resolve 'leiningen.compile/eval-in-project)
                 (catch java.io.FileNotFoundException _
                   (require 'leiningen.core.eval)
                   (resolve 'leiningen.core.eval/eval-in-project)))]
    (apply eip args)))

(defn swank
  "Launch swank server for Emacs to connect. Optionally takes PORT and HOST."
  ([project port host & opts]
     (eval-in-project (update-in (add-cdt-project-args project)
                                 [:dependencies] conj ['swank-clojure "1.4.0"])
                      (swank-form project port host opts)))
  ([project port] (swank project port "localhost"))
  ([project] (swank project 4005)))