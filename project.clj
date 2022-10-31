(defproject clojure-atm "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [metosin/reitit "0.5.18"]
                 [metosin/jsonista "0.3.6"]
                 [metosin/muuntaja "0.6.8"]
                 [com.appsflyer/donkey "0.5.2"]]
  :main ^:skip-aot clojure-atm.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :name "clojure-atm.jar"}})
