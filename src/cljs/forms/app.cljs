(ns forms.app
  (:require [forms.core :as f]
            [rum.core :as rum]
            [rum.mdl :as mdl]
            [goog.format.EmailAddress :as email]
            [cljs.spec :as s]
            [cljs.pprint :as pp]
            [clojure.string :as string]))

(enable-console-print!)
;; App specific spec stuff

;; Specs =====================================================================

(defn valid-email? [addr] (email/isValidAddress addr))

(s/def ::email valid-email?)
(s/def ::not-blank (s/and string? #(not (string/blank? %))))

(s/def ::name ::not-blank)
(s/def ::department #{"research" "sales" "marketing" "development" "internal"})

(s/def ::person
  (s/keys :req-un [::name ::department]))

(s/def :oc.forms.su-share.email/to (s/and string? ::email))
(s/def :oc.forms.su-share.email/subject ::not-blank)
(s/def :oc.forms.su-share.email/note (s/nilable string?))
(s/def :oc.forms.su-share.email/people (s/* ::person))

(s/def ::email-form
  (s/keys :req-un [:oc.forms.su-share.email/to
                   :oc.forms.su-share.email/subject
                   :oc.forms.su-share.email/note
                   :oc.forms.su-share.email/people]))

;; App state & event dispatching =============================================

(defonce *app (atom {}))

(defmulti dispatch (fn [db [type]] type))

(defmethod dispatch :form/init
  [db [_ path spec]]
  (assoc-in db path (f/->fs {} spec)))

(defmethod dispatch :form/input
  [db [_ form-path path val invalid?]]
  (update-in db form-path f/input path val invalid?))

(defmethod dispatch :form/add-item
  [db [_ form-path path item invalid?]]
  (update-in db (concat form-path [::f/value] path) (fnil conj []) item))

(defmethod dispatch :form/submit
  [db [_ form-path]]
  (let [validated (f/validate (get-in db form-path))]
    (when (f/valid? validated)
      (js/console.info "Submitting form" (pr-str (::f/value validated))))
    (assoc-in db form-path validated)))

(defn dispatch! [ev]
  (prn ev)
  (reset! *app (dispatch @*app ev)))

;; Form implementation =======================================================

(defn label [path]
  (-> {[:to] "Receivers of this update"
       [:subject] "The subject of your message"
       [:note] "Any addtional note to add"
       [:people :name] "Name of the person"
       [:people :department] "Department of the person"}
      (get (filterv keyword? path))))

(defn error-message [path]
  (-> {[:to] "Please enter a valid email address"
       [:subject] "Please provide a subject for your message"
       [:people :name] "Please provide a name"
       [:people :department] "Please provide a known department"}
    (get path)))

(rum/defc field < rum/static
  [form-path _field-path field-data]
  (let [valid? (:valid? field-data)
        error  (:error field-data)]
    [:div.pv2
     ;; [:pre (pp/pprint field-data)]
     [:label.db.pv2
      (label (:path field-data))
      (when (and (:dirty? field-data) valid?) [:span.ml1.green "✓"])]
     [:input.pa2.w-100
      {:type       "text"
       :value      (or (:value field-data) "")
       :on-blur   #(when-not (string/blank? (.. % -target -value))
                     (dispatch! [:form/input form-path (:path field-data) (.. % -target -value) true]))
       :on-change #(dispatch! [:form/input form-path (:path field-data) (.. % -target -value) (boolean error)])}]
     (when (and (:validated? field-data) error)
       [:p.red (-> error :path error-message)])]))

(rum/defc button < rum/static
  [attrs content]
  [:button.pv2.ph3.ba.br1.bg-white.ttu.fw6
   (merge {:style {:outline :none :font-size "12px"}} attrs)
   content])

(rum/defc person-form < rum/static
  [& contents]
  (into [:div.pa3.bg-silver.br2.mb2] contents))
(rum/defc email-form < rum/static
  [form]
  [:div
   (field [:email-form] [:to] (f/path-info form [:to]))
   (field [:email-form] [:subject] (f/path-info form [:subject]))
   (field [:email-form] [:note] (f/path-info form [:note]))
   [:div.mb2
    (for [p (range (count (:people (::f/value form))))]
      (rum/with-key 
        (person-form
         (field [:email-form] [:people p :name] (f/path-info form [:people p :name]))
         (field [:email-form] [:people p :department] (f/path-info form [:people p :department])))
        p))]
   (button {:on-click #(dispatch! [:form/add-item [:email-form] [:people] {:name nil :department nil}])}
           "Add person")
   [:div.mt3
    (button
     {;:disabled (-> form f/valid? not)
      :on-click #(dispatch! [:form/submit [:email-form]])}
     "Submit")]
   [:pre (with-out-str (pp/pprint form))]])

(rum/defc app < rum/reactive
  [app-db]
  [:div.center.w-60.pv3
   [:span.f1.mr3 "App"]
   (button {:on-click #(dispatch! [:form/init [:email-form] ::email-form])} "reset")
   (email-form (:email-form (rum/react app-db)))])

(defonce x (dispatch! [:form/init [:email-form] ::email-form]))

(defn init []
  (rum/mount (app *app) (js/document.getElementById "container")))