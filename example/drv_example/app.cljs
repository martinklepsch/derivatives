(ns drv-example.app
  (:require [org.martinklepsch.derivatives :as d]
            [rum.core :as rum]))

(enable-console-print!)

(defn drv-spec [base]
  {:base   [[]           base]
   :inc    [[:base]      (fn [base] (inc base))]
   :as-map [[:base :inc] (fn [base inc] {:base base :after-inc inc})]
   :sum    [[:as-map]    (fn [as-map] (+ (:base as-map) (:after-inc as-map)))]})

(def container :pre.bg-black-05.pa3.ma0.br2)
(def item :span.db.mb2)

(rum/defcs derived-view < rum/reactive (d/drv :base) (d/drv :inc) (d/drv :as-map) (d/drv :sum)
  [s]
  [:div
   [item "Using multiple " [:code "drv"] " mixins"]
   [container
    [item ":base "   (-> (d/react s :base) pr-str)]
    [item ":inc "    (-> (d/react s :inc) pr-str)]
    [item ":as-map " (-> (d/react s :as-map) pr-str)]
    [item ":sum "    (-> (d/react s :sum) pr-str)]]])

(rum/defcs derived-view-variadic-mixin < rum/reactive (d/drv :base :inc :as-map :sum)
  [s]
  [:div
   [item "Using single, variadic " [:code "drv"] " mixin"]
   [container
    [item ":base "   (-> (d/react s :base) pr-str)]
    [item ":inc "    (-> (d/react s :inc) pr-str)]
    [item ":as-map " (-> (d/react s :as-map) pr-str)]
    [item ":sum "    (-> (d/react s :sum) pr-str)]]])

(rum/defcs derived-view-react-all < rum/reactive (d/drv :inc :as-map :sum) (d/drv :base)
  [s]
  [:div
   [item "Both mixin varians, " [:code "react-all"]]
   (let [{:keys [base inc as-map sum]} (d/react-all s)]
     [container
      [item ":base "   (-> base pr-str)]
      [item ":inc "    (-> inc pr-str)]
      [item ":as-map " (-> as-map pr-str)]
      [item ":sum "    (-> sum pr-str)]])])

(rum/defcs derived-view-react-both < rum/reactive (d/drv :inc :as-map :sum) (d/drv :base)
  [s]
  [:div
   [item "Both mixin varians, " [:code "react-all"] " and singular " [:code "react"]]
   (let [{:keys [base inc]} (d/react-all s :base :inc)]
     [container
      [item ":base "   (-> base pr-str)]
      [item ":inc "    (-> inc pr-str)]
      [item ":as-map " (-> (d/react s :as-map) pr-str)]
      [item ":sum "    (-> (d/react s :sum) pr-str)]])])

(defonce *state (atom 0))

(def button :button.f6.fw6.ttu.link.dim.br1.ba.ph3.pv2.mb2.mr2.dib.white.pointer)

(rum/defc heading []
  [:div.mb3.pa2
   [:h1 "Derivatives Dataflow Test"]
   [button {:on-click #(swap! *state inc)} "inc"]
   [button {:on-click #(prn @*state)} "log state atom"]])

(rum/defc drv-variant-table []
  [:div.cf
   [:div.fl.w-50.pa2 (derived-view)]
   [:div.fl.w-50.pa2 (derived-view-variadic-mixin)]
   [:div.fl.w-50.pa2 (derived-view-react-all)]
   [:div.fl.w-50.pa2 (derived-view-react-both)]])

(rum/defc spec-via-mixin < (d/rum-derivatives (drv-spec *state))
  []
  [:div
   [:h3.mb4.pa2 "Derviatives spec passed to mixin"]
   (drv-variant-table)])

(rum/defc spec-via-args < (d/rum-derivatives* first)
  [spec]
  [:div
   [:h3.mb4.pa2 "Derviatives spec extracted from arguments"]
   (drv-variant-table)])

(rum/defc root-component []
  [:div.pa4
   (heading)
   [:hr.mv3.ba]
   (spec-via-args (drv-spec *state))
   [:hr.mv3.ba]
   (spec-via-mixin)])

(defn init []
  (rum/mount (root-component) (js/document.getElementById "container")))