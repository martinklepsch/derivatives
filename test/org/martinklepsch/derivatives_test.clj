(ns org.martinklepsch.derivatives-test
  (:require [org.martinklepsch.derivatives :as drv]
            [clojure.test :as t]))

(def compute-log (atom []))
(defn log! [id] (swap! compute-log conj id))

(defn at-least-n-in [thing n seq]
  (<= n (count (filter #(= % thing) seq))))

(t/use-fixtures :each (fn [f] (reset! compute-log []) (f)))

(def kbase   (str "base---" (java.util.UUID/randomUUID)))
(def kinc    (str "inc----" (java.util.UUID/randomUUID)))
(def kas-map (str "as-map-" (java.util.UUID/randomUUID)))
(def ksum    (str "sum----" (java.util.UUID/randomUUID)))

(defn test-spec [base]
  {kbase   [[]           base]
   kinc    [[kbase]      (fn [base] (log! kinc) (inc base))]
   kas-map [[kbase kinc] (fn [base inc] (log! kas-map) {:base base :after-inc inc})]
   ksum    [[kas-map]    (fn [as-map] (log! ksum) (+ (:base as-map) (:after-inc as-map)))]})

(t/deftest not-required-test
  (t/is (= [2] (drv/not-required {:a 1 :b 2} #{:a})))
  (t/is (= [1 2] (drv/not-required {:a 1 :b 2} #{:c})))
  (t/is (= [] (drv/not-required {:a 1 :b 2} #{:a :b}))))

(t/deftest build-test
  (let [drvs (drv/build (test-spec (atom 1)))]
    (t/is (= 1 @(get drvs kbase)))
    (t/is (= 2 @(get drvs kinc)))
    (t/is (= {:base 1 :after-inc 2} @(get drvs kas-map)))
    (t/is (= 3 @(get drvs ksum)))))

(t/deftest sync-derivatives-test
  (let [drvs (drv/sync-derivatives! (test-spec (atom 1)) (drv/prefix-id) {} [kbase kinc])]
    (t/is (= 1 @(get drvs kbase)))
    (t/is (= 2 @(get drvs kinc)))
    (t/is (nil? (get drvs kas-map)))
    (t/is (nil? (get drvs ksum)))
    (t/is (= [kinc] @compute-log))))

(t/deftest sync-derivatives-test
  (let [spec (test-spec (atom 1))
        id   (drv/prefix-id)
        drvs (drv/sync-derivatives! spec id {} [kbase kinc])]
    (t/is (= 1 @(get drvs kbase)))
    (t/is (= 2 @(get drvs kinc)))
    (t/is (nil? (get drvs kas-map)))
    (t/is (nil? (get drvs ksum)))
    (t/is (= [kinc] @compute-log))
    ;; sync with more required keys
    (let [drvs-updated (drv/sync-derivatives! spec id drvs [kbase kinc kas-map])]
      (t/is (= [kinc kas-map] @compute-log)))))

;; This test will fail if watches are not properly removed from source refs
(t/deftest watches-disposed-test
  (let [base  (atom 1)
        spec  (test-spec base)
        id    (drv/prefix-id)
        drvs1 (drv/sync-derivatives! spec id {} [kbase kinc])
        drvs2 (drv/sync-derivatives! spec id drvs1 [kbase])]
    (swap! base inc)
    (t/is (= [kinc] @compute-log))))

(t/deftest pool-test
  (let [base  (atom 1)
        spec  (test-spec base)
        {:keys [get! release!]} (drv/derivatives-pool spec)]
    (t/is (= 1 @(get! kbase :1-token)))
    (t/is (= [] @compute-log))
    (t/is (= 2 @(get! kinc :2-token)))
    (t/is (= [kinc] @compute-log))
    (t/is (= 3 @(get! ksum :3-token)))
    (t/is (= [kinc kas-map ksum] @compute-log))
    (swap! base inc)
    ;; Previously this test was used:
    ;; (t/is (= [kinc kas-map ksum kinc kas-map ksum kas-map] @compute-log))
    ;; We cannot guarantee an order in which the watches are triggered though
    ;; and so all we can test is that a thing as been recomputed at least n times
    (t/is (at-least-n-in kinc 2 @compute-log))
    (t/is (at-least-n-in ksum 2 @compute-log))
    (t/is (at-least-n-in kas-map 3 @compute-log))
    (t/is (= 5 @(get! ksum :3-token)))))

(t/deftest pool-throws-if-unknown-test
  (let [base  (atom 1)
        spec  (test-spec base)
        pool-state (atom {})
        dm (drv/->DerivativesPool spec (drv/prefix-id) (drv/spec->graph spec) pool-state)]
    (t/is (thrown? Exception @(drv/get! dm :unknown-foo :1-token)))
    (t/is (= {} @pool-state)))) ; pool state should not get modified

(t/deftest pool-internals-test
  (let [base  (atom 1)
        spec  (test-spec base)
        pool-state (atom {})
        dm (drv/->DerivativesPool spec (drv/prefix-id) (drv/spec->graph spec) pool-state)]

    (drv/get! dm ksum :1-token)
    (t/is (= {ksum #{:1-token}} (:registry @pool-state)))
    (drv/get! dm kas-map :2-token)
    (t/is (= {ksum #{:1-token} kas-map #{:2-token}} (:registry @pool-state)))
    (t/is (= {:base 1 :after-inc 2} (-> @pool-state :derivatives (get kas-map) deref)))
    (t/is (= 3 (-> @pool-state :derivatives (get ksum) deref)))

    ;; :as-map still needs to be present because :sum requires it
    (drv/release! dm kas-map :2-token)
    (t/is (= {ksum #{:1-token}} (:registry @pool-state)))
    (t/is (= {:base 1 :after-inc 2} (-> @pool-state :derivatives (get kas-map) deref)))
    (t/is (= 3 (-> @pool-state :derivatives (get ksum) deref)))

    ;; After releasing all, pool and registry should be empty
    (drv/release! dm ksum :1-token)
    (t/is (= {} (:registry @pool-state)))
    (t/is (= {} (:derivatives @pool-state)))))

(t/deftest pool-same-source-test
  (let [base  (atom 1)
        spec  (test-spec base)
        pool-state (atom {})
        dm (drv/->DerivativesPool spec (drv/prefix-id) (drv/spec->graph spec) pool-state)]
    (t/is (= base (drv/get! dm kbase :abc)))
    (let [am-ref (drv/get! dm kas-map :t1)]
      (t/is (= (drv/get! dm ksum :t1) (drv/get! dm ksum :t2)))
      (t/is (= (drv/get! dm ksum :t2) (drv/get! dm ksum :t4)))
      (t/is (= am-ref (drv/get! dm kas-map :t123))))))