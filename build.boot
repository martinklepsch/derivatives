(set-env!
 :resource-paths  #{"src"}
 :dependencies '[[adzerk/boot-cljs          "1.7.228-1"  :scope "test"]
                 [adzerk/boot-reload        "0.4.11"     :scope "test"]
                 [pandeiro/boot-http        "0.7.3"      :scope "test"]
                 [org.clojure/clojurescript "1.9.93"     :scope "provided"]
                 [com.stuartsierra/dependency "0.2.0"]
                 [rum "0.9.1"]])

(task-options!
 pom {:project     'org.martinklepsch/derivatives
      :version     "0.1.0" 
      :description "Chains of derived values"
      :url         "https://github.com/martinklepsch/derivatives"
      :scm         {:url "https://github.com/martinklepsch/derivatives"}})

;; Example app stuff ============================================================

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-reload    :refer [reload]]
 '[pandeiro.boot-http    :refer [serve]])

(deftask deploy []
  (comp (pom) (jar) (push :repo "clojars"
                          :tag true)))

(deftask dev []
  (set-env! :resource-paths #(conj % "example"))
  (task-options! cljs   {:optimizations :none :source-map true}
                 reload {:on-jsload 'drv-example.app/init})
  (comp (serve)
        (watch)
        (reload)
        (speak)
        (cljs)))