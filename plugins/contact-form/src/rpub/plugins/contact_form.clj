(ns rpub.plugins.contact-form
  (:require [rpub.admin :as admin]
            [rpub.model :as model]))

(defprotocol Model
  (schema [model])
  (get-messages [model opts]))

(defmulti ->model :db-type)

(defn wrap-contact-form [handler]
  (fn [{:keys [db-type] :as req}]
    (let [ds (get-in req [:model :ds])
          model (->model {:db-type db-type :ds ds})
          req' (merge req {::model model})]
      (handler req'))))

(defn admin-contact-form-page [req]
  (admin/page-handler
    req
    {:title "Contact Form"
     :primary
     (fn [{:keys [::model]}]
       (for [message (get-messages model {})]
         [:pre (pr-str message)]))}))

(defn contact-form-send [_]
  {:status 200})

(defn routes [opts]
  [["/contact-form" {:post contact-form-send
                     :conflicting true}]
   ["/admin/contact-form" {:get admin-contact-form-page
                           :middleware (admin/admin-middleware opts)}]])

(def menu-item
  {:name "Contact Form"
   :href "/admin/contact-form"})

(def block
  {:name "Contact Form"
   :content (fn [_]
              [:form
               [:textarea]
               [:input {:type :submit}]])})

(defn plugin [_]
  {:name "Contact Form"
   :description "Add a contact form to your site."
   :schema (fn [opts] (schema (->model opts)))
   :menu-items [menu-item]
   :middleware [wrap-contact-form]
   :blocks {:contact-form block}
   :routes routes})

(model/add-plugin ::plugin plugin)
