(ns drv-example.app
  (:require [org.martinklepsch.derivatives :as d]
            [rum.core :as rum]))

(enable-console-print!)

(defn drv-spec [base]
  {:base   [[]           base]
   :inc    [[:base]      (fn [base] (inc base))]
   :as-map [[:base :inc] (fn [base inc] {:base base :after-inc inc})]
   :sum    [[:as-map]    (fn [as-map] (+ (:base as-map) (:after-inc as-map)))]})

(rum/defcs derived-view < rum/reactive (d/drv :base) (d/drv :inc) (d/drv :as-map) (d/drv :sum)
  [s]
  [:code
   [:p ":base "   (-> (d/react s :base) pr-str)]
   [:p ":inc "    (-> (d/react s :inc) pr-str)]
   [:p ":as-map " (-> (d/react s :as-map) pr-str)]
   [:p ":sum "    (-> (d/react s :sum) pr-str)]])

(defonce *state (atom 0))

(rum/defc dataflow-test < (d/rum-derivatives (drv-spec *state))
  []
  [:div
   [:h1 "Dataflow Test"]
   [:button {:on-click #(prn @*state)} "log *state"]
   [:button {:on-click #(swap! *state inc)} "inc"]
   (derived-view)])

(rum/defc dataflow-test* < (d/rum-derivatives* first)
  [spec]
  [:div
   [:h1 "Dataflow Test"]
   [:button {:on-click #(prn @*state)} "log *state"]
   [:button {:on-click #(swap! *state inc)} "inc"]
   (derived-view)])

(defn init []
  (rum/mount (dataflow-test* (drv-spec *state))
             (js/document.getElementById "container")))