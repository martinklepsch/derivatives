(set-env!
 :resource-paths  #{"src"}
 :dependencies '[[adzerk/boot-cljs          "1.7.228-1"  :scope "test"]
                 [adzerk/boot-reload        "0.4.11"      :scope "test"]
                 [pandeiro/boot-http        "0.7.3"      :scope "test"]
                 [org.clojure/clojurescript "1.9.93"]
                 [com.stuartsierra/dependency "0.2.0"]
                 [rum "0.9.1"]])

(task-options!
 pom {:project     'org.martinklepsch/derivatives
      :version     +version+
      :description "Push things to S3, but be lazy about it."
      :url         "https://github.com/confetti-clj/s3-deploy"
      :scm         {:url "https://github.com/confetti-clj/s3-deploy"}})

(deftask dev []
  (task-options! cljs   {:optimizations :none :source-map true}
                 reload {:on-jsload 'forms.app/init})
  (comp (serve)
        (watch)
        (reload)
        (speak)
        (cljs)))