(set-env!
 :resource-paths  #{"src"}
 :dependencies '[[adzerk/bootlaces          "0.1.13"     :scope "test"]
                 [adzerk/boot-cljs          "1.7.228-2"  :scope "test"]
                 [adzerk/boot-test          "1.1.2"      :scope "test"]
                 [adzerk/boot-reload        "0.4.13"     :scope "test"]
                 [pandeiro/boot-http        "0.7.3"      :scope "test"]
                 [boot-codox                "0.10.3"     :scope "test"]
                 [org.clojure/clojure       "1.9.0-alpha14" :scope "provided"]
                 [org.clojure/clojurescript "1.9.946"     :scope "provided"]
                 [com.stuartsierra/dependency "0.2.0"]
                 [rum "0.10.8"]
                 [cljsjs/prop-types "15.6.0-0"]])

(def +version+ "0.3.0")
(def github "https://github.com/martinklepsch/derivatives")

(task-options!
 pom {:project     'org.martinklepsch/derivatives
      :version     +version+
      :description "Chains of derived values"
      :url         github
      :scm         {:url github}})

;; Example app stuff ============================================================

(require
 '[adzerk.bootlaces      :refer [bootlaces! build-jar push-release]]
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-test      :refer [test]]
 '[adzerk.boot-reload    :refer [reload]]
 '[pandeiro.boot-http    :refer [serve]]
 '[codox.boot :refer [codox]])

(bootlaces! +version+)

(deftask deploy []
  (comp (pom) (jar) (push :repo "clojars" :tag true)))

(deftask dev []
  (set-env! :resource-paths #(conj % "example"))
  (task-options! cljs   {:optimizations :none :source-map true}
                 reload {:on-jsload 'drv-example.app/init})
  (comp (serve)
        (watch)
        (reload)
        (speak)
        (cljs)))

(deftask gen-docs []
  (codox :name "Derivatives"
         :version +version+
         :output-path "gh-pages"))
