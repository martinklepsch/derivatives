(ns forms.core
  (:require [cljs.spec :as s]))

;; (s/def ::form-state
;;   {::value s/Any
;;    ::initial-value s/Any
;;    ::errors s/Any
;;    ::spec s/Any})

(defn alpha8-problems [probs]
  (reduce-kv (fn [xs k v]
               (conj xs (assoc v :path k)))
             []
             (::s/problems probs)))

(defn explain-data [spec v]
  (alpha8-problems (s/explain-data spec v)))

(defn ->fs
  ([value] (->fs value nil))
  ([value spec]
   {::value value
    ::initial-value value
    ::errors [] ;(when spec (explain-data spec value))
    ::dirty #{}
    ::spec spec}))

(defn reset [fs]
  (assoc fs ::value (::initial-value fs)))

(defn valid? [fs]
  (nil? (::errors fs)))

(defn validate [fs]
  (let [errors (if-let [spec (::spec fs)]
                 (explain-data spec (::value fs)))]
    (assoc fs
           ::errors errors
           ::validated ::all-fields)))

(defn validate-dirty [fs]
  (if (= ::all-fields (::validated fs))
    (validate fs)
    (let [errors (if-let [spec (::spec fs)]
                   (->> (explain-data spec (::value fs))
                        (filter #(contains? (::dirty fs) (:in %)))))]
      (assoc fs
             ::errors errors
             ::validated (::dirty fs)))))

(defn input [fs path value validate?]
  (let [fs (-> (assoc-in fs (into [::value] path) value)
               (update ::dirty conj path))]
    (if validate? (validate-dirty fs) fs)))

(defn errors-for-path [fs path]
  (first (filter #(= (:in %) path) (::errors fs))))
  
(defn path-info [fs path]
  (let [error     (errors-for-path fs path)
        validated (or (= ::all-fields (::validated fs))
                      (contains? (::validated fs) path))]
    {:dirty?     (contains? (::dirty fs) path)
     :valid?     (and validated (nil? error))
     :validated? validated
     :value      (get-in fs (into [::value] path))
     :error      error
     :path       path}))

(def nested-form
  (s/keys :req [:oc.forms/email-form]))

(comment 
  (s/valid? email-form {:to ["martin@klepsch.co"]
                        :subject "abc"
                        :note nil})

  (s/explain-data :oc.forms.su-share.email/subject "")

  (s/explain-data ::email-form {:to ["martin@xxxcom" "xxx"]
                                :subject "abc"
                                :note nil})

  (s/explain-data nested-form {::email-form {:to ["martin@xxxcom" "xxx"]
                                             :subject "abc"
                                             :note nil}})

  (s/conform ::email-form {:to ["martin@klepsch.co"]
                           :subject "abc"
                           :note ""})

  {:email [:to :subject :note]
   :slack [:note]}

  (-> (->fs {:to []
             :subject ""
             :note ""}
            ::email-form)
      (input [:to] ["x@mailcom"])
      (input [:note] "hello")
      (input [:to] ["x@mail.com"])
      (path-info [:to]))





  )