(ns forms.dataflow
  (:require [com.stuartsierra.dependency :as dep]
            [medley.core :as med]
            [rum.core :as rum]))

(defn reactive-spec [base]
  {:base   [[] base]
   :inc    [[:base] (fn [base] (inc base))]
   :as-map [[:base :inc] (fn [base inc] {:base base :after-inc inc})]
   :sum    [[:as-map] (fn [as-map] (+ (:base as-map) (:after-inc as-map)))]})

(defn depend'
  "Variation of `depend` that takes a list of dependencies instead of one"
  [graph node deps]
  (reduce (fn [graph dep]
            (dep/depend graph node dep))
          graph
          deps))

(defn spec->graph
  "Turn a given spec into a dependency graph"
  [spec]
  (reduce-kv (fn [graph id [dependencies]]
               (depend' graph id dependencies))
             (dep/graph)
             spec))

(defn build
  "Given a spec return a map of similar structure replacing it's values
   with derived atoms built based on the depedency information encoded in the spec"
  [spec]
  (let [graph (spec->graph spec)]
    (reduce (fn [m k]
              (let [[direct-deps derive] (-> spec k)]
                (if (fn? derive) ; for the lack of `atom?`
                  (assoc m k (rum/derived-atom (map m direct-deps) k derive))
                  (assoc m k derive))))
            {}
            (dep/topo-sort graph))))

(defn build-pure
  "Given a spec return a map of similar structure replacing it's values
   with derived atoms built based on the depedency information encoded in the spec"
  [spec graph get-ref]
  (reduce (fn [m k]
            (let [[direct-deps derive] (-> spec k)]
              (if (fn? derive) ; for the lack of `atom?`
                (assoc m k (fn [] (rum/derived-atom (map get-ref direct-deps) k derive)))
                (assoc m k (fn [] derive)))))
          {}
          (dep/topo-sort graph)))

(defn use! [reg k token]
  (if-let [r (get-in @reg [::reactions k])]
    (do
      (prn :taking-existing-atom)
      (swap! reg update-in [::reactions k :used-by] conj token)
      (:atom r))
    (let [ref ((get-in @*registry [::ctors k]))]
      (prn :creating-new-atom)
      (swap! reg assoc-in [::reactions k] {:atom ref :used-by #{token}})
      ref)))

(defn free! [reg k token]
  (when-let [kd (get-in @reg [::reactions k])]
    (if (= (:used-by kd) #{token})
      (swap! reg update ::reactions dissoc k)
      (swap! reg update-in [::reactions k :used-by] disj k)))
  nil)

(defn registry [spec]
  (let [reg    (atom {})
        subs-m (build-pure spec (spec->graph spec) (partial reg) )]
    ))


(comment

  (def base (atom 0))

  (build (reactive-spec base))

  (def reactions
    (build x))
  ;; => {:base #object[clojure.lang.Atom 0x5a550b22 {:status :ready, :val 0}], :inc #object[clojure.lang.Atom 0x2f398697 {:status :ready, :val 1}], :as-map #object[clojure.lang.Atom 0x2e5eb389 {:status :ready, :val {:base 0, :after-inc 1}}], :sum #object[clojure.lang.Atom 0x385e17b0 {:status :ready, :val 1}]}


  (swap! base inc)

  (med/map-vals deref reactions)
  ;; => {:base 1, :inc 2, :as-map {:base 1, :after-inc 2}, :sum 3}

  )

(comment

  (def *registry (atom {::ctors (build-pure (reactive-spec base))}))
  (add-watch *registry ::x (fn [_ _ _ v] (prn (::reactions v))))


  (use! :base "y")
  (free! :base "y")

  (prn (::reactions @*registry))

  )
