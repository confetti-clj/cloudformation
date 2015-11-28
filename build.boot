(set-env!
 :source-paths   #{"src"}
 :dependencies '[[adzerk/bootlaces "0.1.11" :scope "test"]
                 [amazonica/amazonica "0.3.33"]
                 [camel-snake-kebab "0.3.2"]
                 [org.clojure/data.json "0.2.6"]])

(require '[adzerk.bootlaces :refer [bootlaces! build-jar push-snapshot push-release]])

(def +version+ "0.1.0-SNAPSHOT")
(bootlaces! +version+)

(task-options!
 pom {:project     'confetti/cloudformation
      :version     +version+
      :description "Generate CloudFormation templates suitable for static sites and run them."
      :url         "https://github.com/confetti-clj/cloudformation"
      :scm         {:url "https://github.com/confetti-clj/cloudformation"}})
