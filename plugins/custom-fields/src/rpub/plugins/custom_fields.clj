(ns rpub.plugins.custom-fields
  (:require [clojure.set :as set]
            [rpub.admin :as admin]
            [rpub.model :as model]))

(defprotocol Model
  (schema [model])
  (get-fields [model opts])
  (create-group! [model opts]))

(defmulti ->model :db-type)

(def menu-items
  [{:name "Custom Fields"
    :href "/admin/custom-fields"}])

(defn fields->groups [fields]
  (->> fields
       (group-by :group-name)
       (map (fn [[k v]] {:group-name k :fields v}))))

(defn index-page [req]
  (admin/page-response
    req
    {:title "Custom Fields"
     :primary
     (fn [{:keys [::model]}]
       [:div
        (admin/form
          [:div
           [:input {:type :text
                    :name :group-name
                    :placeholder "Name"}]]
          [:div
           [:button {:type :submit}
            "Create Field Group"]])
        [:div
         (for [group (fields->groups (get-fields model {}))]
           [:div.mt-4
            [:h3.text-2xl.font-semibold
             (:group-name group)]
            [:ul
             (for [field (:fields group)]
               [:li (:field-name field) " (" (:field-type field) ")"])]])]])}))

(defn ->group [opts]
  (merge {:id (random-uuid)}
         (select-keys opts [:name])))

(defn create-group [{:keys [model params] :as req}]
  (let [opts (-> params
                 (select-keys ["group-name"])
                 (set/rename-keys {"group-name" :name}))
        group (->group opts)]
    (create-group! model group)
    (index-page req)))

(defn wrap-custom-fields [handler]
  (fn [{:keys [db-type] :as req}]
    (let [ds (get-in req [:model :ds])
          model (->model {:db-type db-type :ds ds})
          req' (merge req {::model model})]
      (handler req'))))

(defn routes [opts]
  [["/admin/custom-fields" {:middleware (admin/admin-middleware opts)}
    ["" {:get index-page
         :post create-group}]]])

(defn plugin [_]
  {:name "Custom Fields"
   :description "Add custom fields using the admin UI."
   :schema (fn [opts] (schema (->model opts)))
   :menu-items menu-items
   :middleware [wrap-custom-fields]
   :routes routes})

(model/add-plugin ::plugin plugin)
