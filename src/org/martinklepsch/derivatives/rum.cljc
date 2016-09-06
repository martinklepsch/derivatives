(ns org.martinklepsch.derivatives.rum
  (:require [org.martinklepsch.derivatives :as drv]
            [rum.core :as rum]
            #?(:cljs [goog.object :as gobj])))

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
        :child-context    (fn [_] (let [{:keys [release! get!]} (derivatives-pool spec)]
                                    {release-k release! get-k get!}))}))

  (defn rum-derivatives*
    "Like rum-derivatives but get the spec from the arguments passed to the components (`:rum/args`) using `get-spec-fn`"
    [get-spec-fn]
    #?(:cljs
       {:class-properties {:childContextTypes {get-k     js/React.PropTypes.func
                                               release-k js/React.PropTypes.func}}
        :init             (fn [s _] (assoc s ::spec (get-spec-fn (:rum/args s))))
        :child-context    (fn [s] (let [{:keys [release! get!]} (derivatives-pool (::spec s))]
                                    {release-k release! get-k get!}))}))

  (defn drv
    "Rum mixin to retrieve a derivative for `:drv-k` using the functions in the component context
     To get the derived-atom use `get-ref` for swappable client/server behavior"
    [drv-k]
    #?(:cljs
       (let [token (rand-int 10000)] ; TODO think of something better here
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