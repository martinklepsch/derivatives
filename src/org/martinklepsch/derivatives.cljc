(ns org.martinklepsch.derivatives
  (:require [com.stuartsierra.dependency :as dep]
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

(defn build
  "Given a spec return a map of similar structure replacing it's values with
   derived atoms built based on the depedency information encoded in the spec

   WARNING: This will create derived atoms for all keys so it may lead
  to some uneccesary computations To avoid this issue consider using
  `derivatives-manager` which manages derivatives in a registry
  removing them as soon as they become unused"
  [spec]
  (let [graph (spec->graph spec)]
    (reduce (fn [m k]
              (let [[direct-deps derive] (-> spec k)]
                (if (fn? derive) ; for the lack of `atom?`
                  (assoc m k (rum/derived-atom (map m direct-deps) k derive))
                  (assoc m k derive))))
            {}
            (dep/topo-sort graph))))

(defn calc-deps
  "Calculate all dependencies for `ks` and return a set with the dependencies and `ks`"
  [graph ks]
  (apply s/union (set ks) (map #(dep/transitive-dependencies graph %) ks)))

(defn sync-derivatives
  "Update the derivatives map `drv-map` so that all keys passed in `order`
   are statisfied and any superfluous keys are removed"
  [spec drv-map order]
  (reduce (fn [m k]
            (let [[direct-deps derive] (-> spec k)]
              (if (get m k)
                m
                (if (implements? IWatchable derive)
                  (assoc m k derive)
                  (do
                    (prn :creating-new-ref k)
                    (assoc m k (rum/derived-atom (map #(get m %) direct-deps) k derive)))))))
          (select-keys drv-map order)
          order))

(defn derivatives-manager
  "Given a derivatives spec return a map with `get!` and `free!` functions.

  - (get! derivative-id token) will retrieve a derivative for
    `derivative-id` registering the usage with `token`
  - (free! derivative-id token) will indicate the derivative `derivative-id`
    is no longer needed by `token`, if there are no more tokens needing
    the derivative it will be removed"
  [spec]
  {:pre [(map? spec)]}
  (let [graph (spec->graph spec)
        state (atom {:registry   {}
                     :dervatives {}})
        sync! (fn [new-registry]
                (let [required? (calc-deps graph (keys new-registry))
                      ordered   (filter required? (dep/topo-sort graph))
                      new-drvs  (sync-derivatives spec (:derivatives @state) ordered)]
                  (swap! state assoc :derivatives new-drvs, :registry new-registry)
                  new-drvs))]
    {:get! (fn get! [drv-k token]
             (let [registry  (:registry @state)
                   new-reg   (update registry drv-k (fnil conj #{}) token)]
               (if-let [derivative (get (sync! new-reg) drv-k)]
                 derivative
                 (throw (ex-info (str "No derivative defined for " drv-k) {:key drv-k})))))
     :release! (fn release! [drv-k token]
              (let [registry  (:registry @state)
                    new-reg   (if (= #{token} (get registry drv-k))
                                (dissoc registry drv-k)
                                (update registry drv-k disj token))]
                (sync! new-reg)
                nil))}))


;; RUM specific code ===========================================================

(let [get-k     "org.martinklepsch.derivatives/get"
      release-k "org.martinklepsch.derivatives/release"]

  (defn rum-derivatives
    "Given the passed spec add get!/release! derivative functions to
    the child context so they can be seen by components using the `deriv`
    mixin."
    [spec]
    #?(:cljs
       {:class-properties {:childContextTypes {get-k     js/React.PropTypes.func
                                               release-k js/React.PropTypes.func}}
        :child-context    (fn [_] (let [{:keys [release! get!]} (derivatives-manager spec)]
                                    {release-k release! get-k get!}))}))

  (defn rum-derivatives*
    "Like rum-derivatives but get the spec from the arguments passed to the components (`:rum/args`) using `get-spec-fn`"
    [get-spec-fn]
    #?(:cljs
       {:class-properties {:childContextTypes {get-k     js/React.PropTypes.func
                                               release-k js/React.PropTypes.func}}
        :init             (fn [s _] (assoc s ::spec (get-spec-fn (:rum/args s))))
        :child-context    (fn [s] (let [{:keys [release! get!]} (derivatives-manager (::spec s))]
                                    {release-k release! get-k get!}))}))

  (defn drv
    "Rum mixin to retrieve a derivative for `:drv-k` using the functions in the component context
     To get the derived-atom use `get-ref` for swappable client/server behavior"
    [drv-k]
    #?(:cljs
       (let [token (rand-int 10000)] ;TODO
         {:class-properties {:contextTypes {get-k     js/React.PropTypes.func
                                            release-k js/React.PropTypes.func}}
          :will-mount    (fn [s]
                           (let [get-drv! (-> s :rum/react-component (gobj/get "context") (gobj/get get-k))]
                             (assert get-drv! "No get! derivative function found in component context")
                             (assoc-in s [::derivatives drv-k] (get-drv! drv-k token))))
          :will-unmount  (fn [s]
                           (let [release-drv! (-> s :rum/react-component (gobj/get "context") (gobj/get release-k))]
                             (assert release-drv! "No release! derivative function found in component context")
                             (release-drv! drv-k token)
                             (update s ::derivatives dissoc drv-k)))}))))

(def ^:dynamic *derivatives* nil)

(defn get-ref
  "Get the derivative identified by `drv-k` from the component state.
   When rendering in Clojure this looks for `drv-k` in the dynvar `*derivatives`"
  [state drv-k]
  (or #?(:cljs (get-in state [::derivatives drv-k])
         :clj  (get *derivatives* drv-k))
      (throw (ex-info (str "No derivative found! Maybe you forgot a (drv " drv-k ") mixin?")
                      {:key drv-k :derivatives #?(:cljs (keys (::derivatives state))
                                                  :clj (keys *derivatives*))}))))

(defn react
  "Like `get-ref` wrapped in `rum.core/react`"
  [state drv-k]
  (rum/react (get-ref state drv-k)))

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
