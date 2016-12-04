(set-env!
 :resource-paths  #{"src"}
 :dependencies '[[adzerk/bootlaces          "0.1.13"     :scope "test"]
                 [adzerk/boot-cljs          "1.7.228-2"  :scope "test"]
                 [adzerk/boot-test          "1.1.2"      :scope "test"]
                 [adzerk/boot-reload        "0.4.13"     :scope "test"]
                 [pandeiro/boot-http        "0.7.3"      :scope "test"]
                 [org.clojure/clojure       "1.9.0-alpha14" :scope "provided"]
                 [org.clojure/clojurescript "1.9.293"     :scope "provided"]
                 [com.stuartsierra/dependency "0.2.0"]
                 [rum "0.10.5"]])

(def +version+ "0.1.1")

(task-options!
 pom {:project     'org.martinklepsch/derivatives
      :version     +version+
      :description "Chains of derived values"
      :url         "https://github.com/martinklepsch/derivatives"
      :scm         {:url "https://github.com/martinklepsch/derivatives"}})

;; Example app stuff ============================================================

(require
 '[adzerk.bootlaces      :refer [bootlaces! build-jar push-release]]
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-test      :refer [test]]
 '[adzerk.boot-reload    :refer [reload]]
 '[pandeiro.boot-http    :refer [serve]])

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