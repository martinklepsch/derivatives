(ns org.martinklepsch.derivatives-test
  (:require [org.martinklepsch.derivatives :as drv]
            [clojure.test :as t]))

(def compute-log (atom []))
(defn log! [id] (swap! compute-log conj id))

(t/use-fixtures :each (fn [f] (reset! compute-log []) (f)))

(defn test-spec [base]
  {:base   [[]           base]
   :inc    [[:base]      (fn [base] (log! :inc) (inc base))]
   :as-map [[:base :inc] (fn [base inc] (log! :as-map) {:base base :after-inc inc})]
   :sum    [[:as-map]    (fn [as-map] (log! :sum) (+ (:base as-map) (:after-inc as-map)))]})


(t/deftest not-required-test
  (t/is (= [2] (drv/not-required {:a 1 :b 2} #{:a})))
  (t/is (= [1 2] (drv/not-required {:a 1 :b 2} #{:c})))
  (t/is (= [] (drv/not-required {:a 1 :b 2} #{:a :b}))))

(t/deftest build-test
  (let [drvs (drv/build (test-spec (atom 1)))]
    (t/is (= 1 @(:base drvs)))
    (t/is (= 2 @(:inc drvs)))
    (t/is (= {:base 1 :after-inc 2} @(:as-map drvs)))
    (t/is (= 3 @(:sum drvs)))))

(t/deftest sync-derivatives-test
  (let [drvs (drv/sync-derivatives! (test-spec (atom 1)) {} [:base :inc])]
    (t/is (= 1 @(:base drvs)))
    (t/is (= 2 @(:inc drvs)))
    (t/is (nil? (:as-map drvs)))
    (t/is (nil? (:sum drvs)))
    (t/is (= [:inc] @compute-log))))

(t/deftest sync-derivatives-test
  (let [spec (test-spec (atom 1))
        drvs (drv/sync-derivatives! spec {} [:base :inc])]
    (t/is (= 1 @(:base drvs)))
    (t/is (= 2 @(:inc drvs)))
    (t/is (nil? (:as-map drvs)))
    (t/is (nil? (:sum drvs)))
    (t/is (= [:inc] @compute-log))
    ;; sync with more required keys
    (let [drvs-updated (drv/sync-derivatives! spec drvs [:base :inc :as-map])]
      (t/is (= [:inc :as-map] @compute-log)))))

;; fails, fixed by proper disposable derived-atoms
(t/deftest watches-disposed-test
  (let [base  (atom 1)
        spec  (test-spec base)
        drvs1 (drv/sync-derivatives! spec {} [:base :inc])
        drvs2 (drv/sync-derivatives! spec drvs1 [:base])]
    (swap! base inc)
    (t/is (= [:inc] @compute-log))))

(t/deftest pool-test
  (let [base  (atom 1)
        spec  (test-spec base)
        {:keys [get! release!]} (drv/derivatives-pool spec)]
    (t/is (= 1 @(get! :base :1-token)))
    (t/is (= [] @compute-log))
    (t/is (= 2 @(get! :inc :2-token)))
    (t/is (= [:inc] @compute-log))
    (t/is (= 3 @(get! :sum :3-token)))
    (t/is (= [:inc :as-map :sum] @compute-log))
    (swap! base inc)
    (t/is (= [:inc :as-map :sum :inc :as-map :sum :as-map] @compute-log))
    (t/is (= 5 @(get! :sum :3-token)))))

(t/deftest pool-throws-if-unknown-test
  (let [base  (atom 1)
        spec  (test-spec base)
        pool-state (atom {})
        dm (drv/->DerivativesPool spec (drv/spec->graph spec) pool-state)]
    (t/is (thrown? Exception @(drv/get! dm :unknown-foo :1-token)))
    (t/is (= {} @pool-state)))) ; pool state should not get modified

(t/deftest pool-internals-test
  (let [base  (atom 1)
        spec  (test-spec base)
        pool-state (atom {})
        dm (drv/->DerivativesPool spec (drv/spec->graph spec) pool-state)]

    (drv/get! dm :sum :1-token)
    (t/is (= {:sum #{:1-token}} (:registry @pool-state)))
    (drv/get! dm :as-map :2-token)
    (t/is (= {:sum #{:1-token} :as-map #{:2-token}} (:registry @pool-state)))
    (t/is (= {:base 1 :after-inc 2} (-> @pool-state :derivatives :as-map deref)))
    (t/is (= 3 (-> @pool-state :derivatives :sum deref)))

    ;; :as-map still needs to be present because :sum requires it
    (drv/release! dm :as-map :2-token)
    (t/is (= {:sum #{:1-token}} (:registry @pool-state)))
    (t/is (= {:base 1 :after-inc 2} (-> @pool-state :derivatives :as-map deref)))
    (t/is (= 3 (-> @pool-state :derivatives :sum deref)))

    ;; After releasing all, pool and registry should be empty
    (drv/release! dm :sum :1-token)
    (t/is (= {} (:registry @pool-state)))
    (t/is (= {} (:derivatives @pool-state)))))

(t/deftest pool-same-source-test
  (let [base  (atom 1)
        spec  (test-spec base)
        pool-state (atom {})
        dm (drv/->DerivativesPool spec (drv/spec->graph spec) pool-state)]
    (t/is (= base (drv/get! dm :base :abc)))
    (let [am-ref (drv/get! dm :as-map :t1)]
      (t/is (= (drv/get! dm :sum :t1) (drv/get! dm :sum :t2)))
      (t/is (= (drv/get! dm :sum :t2) (drv/get! dm :sum :t4)))
      (t/is (= am-ref (drv/get! dm :as-map :t123))))))
 