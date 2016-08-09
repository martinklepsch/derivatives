(ns org.martinklepsch.derived)

(defprotocol IDisposable
  (dispose! [this]))

(defrecord DerivedValue [sources sink k]
  IDisposable
  (dispose! [this]
    (doseq [s sources]
      (prn 'removing-watch s k)
      (remove-watch s k))
    this)
  #?@(:cljs
      [IDeref
       (-deref [this]
               (deref sink))]
      :clj
      [clojure.lang.IDeref
       (deref [this]
              (deref sink))]))

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
     (->DerivedValue refs sink key))))

#?(:clj 
   (defmethod print-method DerivedValue [v ^java.io.Writer w]
     (.write w "<<DerivedValue ")
     (.write w (pr-str (into {} v)))
     (.write w ">>")))

(comment 
  (def a (atom {:a 1}))

  (def b (atom {:b 1}))

  (def der
    (derived-atom [a b] :ab (fn [a b] (prn 'recomputing) (merge a b))))

  (def derv
    (derived-value [a b] :ab (fn [a b] (prn 'recomputing) (merge a b))))

  @derv
  (dispose! derv)

  (swap! b update :b inc)

  (pr-str (into {} (->DerivedValue nil nil nil)))
  (pr (->DerivedValue nil nil nil))


  )

