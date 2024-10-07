(ns ^:clj-reload/no-unload rpub.plugins.forms
  (:require [rpub.admin :as admin]
            [rpub.model :as model]))

^:clj-reload/keep
(defprotocol Model
  (schema [model])
  (get-messages [model opts]))

(defmulti ->model :db-type)

(defn wrap-forms [handler]
  (fn [{:keys [db-type] :as req}]
    (let [ds (get-in req [:model :ds])
          model (->model {:db-type db-type :ds ds})
          req' (merge req {::model model})]
      (handler req'))))

(defn admin-forms-page [{:keys [model] :as req}]
  (admin/page-response
    req
    {:title "Forms"
     :primary
     (for [message (get-messages model {})]
       [:pre (pr-str message)])}))

(defn forms-send [_]
  {:status 200})

(defn routes [opts]
  [["/forms" {:post forms-send
              :conflicting true}]
   ["/admin/forms" {:get admin-forms-page
                    :middleware (admin/admin-middleware opts)}]])

(defn menu-items [_]
  {:plugins [{:name "Forms"
              :href "/admin/forms"}]})

(def contact-form
  {:name "Contact Form"
   :content (fn [_]
              [:form
               [:textarea]
               [:input {:type :submit}]])})

(defn plugin [_]
  {:name "Forms"
   :description "Add forms to your site."
   :schema (fn [opts] (schema (->model opts)))
   :menu-items menu-items
   :middleware [wrap-forms]
   :blocks {:contact-form contact-form}
   :routes routes})

(model/add-plugin ::plugin plugin)
