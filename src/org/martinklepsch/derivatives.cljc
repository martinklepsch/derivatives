(ns org.martinklepsch.derivatives
  (:require [com.stuartsierra.dependency :as dep]
            [org.martinklepsch.derived :as derived]
            [clojure.set :as s]
            [rum.core :as rum]
            #?(:cljs [goog.object :as gobj])))

(defn depend'
  "Variation of `depend` that takes a list of dependencies instead of one"
  [graph node deps]
  (reduce #(dep/depend %1 node %2) graph deps))

(defn spec->graph
  "Turn a given spec into a dependency graph"
  [spec]
  (reduce-kv (fn [graph id [dependencies]]
               (depend' graph id dependencies))
             (dep/graph)
             spec))

(defn calc-deps
  "Calculate all dependencies for `ks` and return a set with the dependencies and `ks`"
  [graph ks]
  (apply s/union (set ks) (map #(dep/transitive-dependencies graph %) ks)))

(defn watchable?
  "Platform-agnostic helper to determine if something is watchable (atom, etc)"
  [x]
  #?(:cljs (satisfies? IWatchable x)
     :clj  (instance? clojure.lang.Atom x)))

(defn not-required
  [drv-map required?]
  {:pre [(set? required?)]}
  (reduce-kv (fn [xs k drv-val]
               (if (required? k) xs (conj xs drv-val)))
             []
             drv-map))

(defn sync-derivatives!
  "Update the derivatives map `drv-map` so that all keys passed in `order`
  are statisfied and any superfluous keys are removed.
  Values of superfluous keys that implement IDisposable they will also be disposed."
  [spec drv-map order]
  (doseq [drv-val (not-required drv-map (set order))]
    (when (satisfies? derived/IDisposable drv-val)
      (derived/dispose! drv-val)))
  (reduce (fn [m k]
            (let [[direct-deps derive] (-> spec k)]
              (if (get m k)
                m
                (if (watchable? derive)
                  (assoc m k derive)
                  (assoc m k (derived/derived-value (map #(get m %) direct-deps) k derive))))))
          (select-keys drv-map order)
          order))

(defn build
  "Given a spec return a map of similar structure replacing it's values with
  derived atoms built based on the depedency information encoded in the spec

  WARNING: This will create derived atoms for all keys so it may lead
  to some uneccesary computations To avoid this issue consider using
  `derivatives-pool` which manages derivatives in a registry
  removing them as soon as they become unused"
  [spec]
  {:pre [(map? spec)]}
  (sync-derivatives! spec {} (dep/topo-sort (spec->graph spec))))

(defn ^:private required-drvs [graph registry]
  (let [required? (calc-deps graph (keys registry))]
    (filter required? (dep/topo-sort graph))))

(defprotocol IDerivativesPool
  (get! [this drv-k token])
  (release! [this drv-k token]))

(defrecord DerivativesPool [spec graph state]
  IDerivativesPool
  (get! [this drv-k token]
    (if-not (get spec drv-k)
      (throw (ex-info (str "No derivative defined for " drv-k) {:key drv-k}))
      (let [new-reg  (update (:registry @state) drv-k (fnil conj #{}) token)
            new-drvs (sync-derivatives! spec (:derivatives @state) (required-drvs graph new-reg))]
        (reset! state {:derivatives new-drvs :registry new-reg})
        (get new-drvs drv-k))))
  (release! [this drv-k token]
    (let [registry  (:registry @state)
          new-reg   (if (= #{token} (get registry drv-k))
                      (dissoc registry drv-k)
                      (update registry drv-k disj token))
          new-drvs (sync-derivatives! spec (:derivatives @state) (required-drvs graph new-reg))]
      (reset! state {:derivatives new-drvs :registry new-reg})
      nil)))

(defn derivatives-pool
  "Given a derivatives spec return a map with `get!` and `free!` functions.

  - (get! derivative-id token) will retrieve a derivative for
    `derivative-id` registering the usage with `token`
  - (free! derivative-id token) will indicate the derivative `derivative-id`
    is no longer needed by `token`, if there are no more tokens needing
    the derivative it will be removed"
  [spec]
  (let [dm (->DerivativesPool spec (spec->graph spec) (atom {}))]
    {:get! (partial get! dm)
     :release! (partial release! dm)}))

(comment
  (def base (atom 0))

  (def test-subman (subman (reactive-spec base)))

  ((:get! test-subman) :ainc "y")

  ((:free! test-subman) :inc "y")

  (def reg {:as-map #{:token-a :token-b}
            :sum    #{:token-c}})

  (def g (spec->graph (reactive-spec base)))

  ;; compute all required dependencies
  (def req? (apply s/union (into #{} (keys reg)) (map #(dep/transitive-dependencies g %) (keys reg)) ))

  ;; order-them
  (def order (filter req? (dep/topo-sort g)))


  (sync-subs (reactive-spec base) {:inc (atom 0)} order)

  )

(comment


  (build (reactive-spec base))

  (def reactions
    (build x))
  ;; => {:base #object[clojure.lang.Atom 0x5a550b22 {:status :ready, :val 0}], :inc #object[clojure.lang.Atom 0x2f398697 {:status :ready, :val 1}], :as-map #object[clojure.lang.Atom 0x2e5eb389 {:status :ready, :val {:base 0, :after-inc 1}}], :sum #object[clojure.lang.Atom 0x385e17b0 {:status :ready, :val 1}]}


  (swap! base inc)

  ;; => {:base 1, :inc 2, :as-map {:base 1, :after-inc 2}, :sum 3}

  )

(comment

  (def *registry (atom {::ctors (build-pure (reactive-spec base))}))
  (add-watch *registry ::x (fn [_ _ _ v] (prn (::reactions v))))


  (use! :base "y")
  (free! :base "y")

  (prn (::reactions @*registry))

  )

(comment

  (def state (atom {}))

  (defn ->spec [db]
    {:base    [[] db]
     :derived [[:base] (fn [base] (inc base))]})

  (spec->graph (->spec state))

  (dep/topo-sort (spec->graph (->spec state)))

  (calc-deps (spec->graph (->spec state)) [:base])

  )
