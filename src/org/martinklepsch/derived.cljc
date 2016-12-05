(ns org.martinklepsch.derived
  "An implementation of reactive derived values built on Clojure
  atoms. Mostly inspired by rum.core/derived-atom but extended with
  the possibility to dispose a derived value. Disposing will remove
  relevant watches on source atoms thus making this implementation
  suitable for dynamic use (runtime creation and disposal).")

(defprotocol IDisposable
  (dispose! [this]))

#?(:clj
   (deftype DerivedValue [sink sources key]
     clojure.lang.IDeref
     (deref [_] @sink)

     clojure.lang.IRef
     (addWatch [this key cb] (add-watch sink key cb))
     (removeWatch [_ key] (remove-watch sink key))

     IDisposable
     (dispose! [this]
       (doseq [s sources]
         (remove-watch s key))))

   :cljs
   (deftype DerivedValue [sink sources key]
     IDeref
     (-deref [_] @sink)

     IWatchable
     (-add-watch [self key cb] (add-watch sink key cb))
     (-remove-watch [_ key] (remove-watch sink key))

     IDisposable
     (dispose! [this]
       (doseq [s sources]
         (remove-watch s key)))))

(defn derived-value
  ([refs key f]
   (derived-value refs key f {}))
  ([refs key f opts]
   (let [{:keys [check-equals?]
          :or {check-equals? true}} opts
         recalc (case (count refs)
                  1 (let [[a] refs] #(f @a))
                  2 (let [[a b] refs] #(f @a @b))
                  3 (let [[a b c] refs] #(f @a @b @c))
                  #(apply f (map deref refs)))
         sink   (atom (recalc))
         watch  (if check-equals?
                  (fn [_ _ _ _]
                    (let [new-val (recalc)]
                      (when (not= @sink new-val)
                        (reset! sink new-val))))
                  (fn [_ _ _ _]
                    (reset! sink (recalc))))]
     (doseq [ref refs]
       (add-watch ref key watch))
     (->DerivedValue sink refs key))))

(comment
  (def a (atom {:a 1}))

  (def b (atom {:b 1}))

  (def der
    (derived-value [a b] :ab (fn [a b] (prn 'recomputing) (merge a b))))

  (def derv
    (derived-value [a b] :ab (fn [a b] (prn 'recomputing) (merge a b))))

  @der

  (dispose! der)

  (swap! b update :b inc)

  (pr-str (->DerivedValue nil nil nil))

  (pr (->DerivedValue nil nil nil))


  )

