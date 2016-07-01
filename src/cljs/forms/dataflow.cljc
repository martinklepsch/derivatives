(ns forms.dataflow
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
  "Given a spec return a map of similar structure replacing it's values
   with derived atoms built based on the depedency information encoded in the spec

   WARNING: This will create derived atoms for all keys so it may lead to some uneccesary computations
   To avoid this issue consider using `subman` which manages subscriptions in a registry
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

(defn sync-subs
  "Update the subscription map `sub-map` so that all keys passed in `order`
   are statisfied and any superfluous keys are removed"
  [spec sub-map order]
  (reduce (fn [m k]
            (let [[direct-deps derive] (-> spec k)]
              (if (get m k)
                m
                (if (fn? derive) ; for the lack of `atom?`
                  (do 
                    (prn :creating-new-ref k)
                    (assoc m k (rum/derived-atom (map #(get m %) direct-deps) k derive)))
                  (assoc m k derive)))))
          (select-keys sub-map order) 
          order))

(defn subman
  "Given a subscription spec return a map with `get!` and `free!` functions.
  
  - (get! sub-id token) will retrieve a subscription for `sub-id` registering
    the usage with `token`
  - (free! sub-id token) will indicate the subscription `sub-id` is no longer needed
    by `token`, if there are no more tokens needing the subscription it will be removed"
  [spec]
  (let [graph (spec->graph spec)
        state (atom {:registry {}
                     :subs     {}})
        sync! (fn [new-registry]
                (let [required? (calc-deps graph (keys new-registry))
                      ordered   (filter required? (dep/topo-sort graph))
                      new-subs  (sync-subs spec (:subs @state) ordered)]
                  (swap! state assoc :subs new-subs, :registry new-registry)
                  new-subs))]
    {:get! (fn get! [sub-k token]
             (let [registry  (:registry @state)
                   new-reg   (update registry sub-k (fnil conj #{}) token)]
               (if-let [sub (get (sync! new-reg) sub-k)]
                 sub
                 (throw (ex-info (str "No subscription defined for " sub-k) {:key sub-k})))))
     :release! (fn release! [sub-k token]
              (let [registry  (:registry @state)
                    new-reg   (if (= #{token} (get registry sub-k))
                                (dissoc registry sub-k)
                                (update registry sub-k disj token))]
                (sync! new-reg)
                nil))}))


;; RUM specific code ===========================================================

(let [get-k     ":dataflow/get"
      release-k ":dataflow/release"]
  (defn rum-subman
    "Given the passed spec add get-sub!/free-sub! functions to the child context
   so they can be seen by components using the `sub` mixin."
    [spec]
    #?(:cljs 
       {:class-properties {:childContextTypes {get-k     js/React.PropTypes.func
                                               release-k js/React.PropTypes.func}}
        :child-context    (fn [_] (let [{:keys [release! get!]} (subman spec)]
                                    {release-k release! get-k get!}))})) 

  (defn rum-subman*
    "Like rum-subman but get the spec from the arguments passed to the components (`:rum/args`) using `get-spec-fn`"
    [get-spec-fn]
    #?(:cljs 
       {:class-properties {:childContextTypes {get-k     js/React.PropTypes.func
                                               release-k js/React.PropTypes.func}}
        :init             (fn [s _] (assoc s ::spec (get-spec-fn (:rum/args s))))
        :child-context    (fn [s] (let [{:keys [release! get!]} (subman (::spec s))]
                                    {release-k release! get-k get!}))}))

  (defn sub
    "Rum mixin to retrieve a subscription for `:sub-k` using the functions in the component context
   To get the derived-atom use `get-ref` for swappable client/server behavior"
    [sub-k]
    #?(:cljs 
       (let [token (rand-int 10000)] ;TODO
         {:class-properties {:contextTypes {get-k     js/React.PropTypes.func
                                            release-k js/React.PropTypes.func}}
          :will-mount    (fn [s]
                           (let [get-sub! (-> s :rum/react-component (gobj/get "context") (gobj/get get-k))]
                             (assert get-sub! "No get-sub! function found in component context")
                             (assoc-in s [::sub sub-k] (get-sub! sub-k))))
          :will-unmount  (fn [s]
                           (let [release-sub! (-> s :rum/react-component (gobj/get "context") (gobj/get release-k))]
                             (assert release-sub! "No release-sub! function found in component context")
                             (release-sub! sub-k)
                             (update s ::sub dissoc sub-k)))}))))

(def ^:dynamic *subscriptions* nil)

(defn get-ref
  "Get the subscription identified by `sub-k` from the component state.
   When rendering in Clojure this looks for `sub-k` in the dynvar `*subscriptions`"
  [state sub-k]
  #?(:cljs (get-in state [::sub sub-k])
     :clj  (get *subscriptions* sub-k)))

(defn react
  "Like `get-ref` wrapped in `rum.core/react`"
  [state sub-k]
  (rum/react (get-ref state sub-k)))

(def base (atom 0))

(comment 
                

  
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
