(ns rpub.plugins.custom-fields
  (:require [clojure.set :as set]
            [rads.rpub.admin :as admin]
            [rads.rpub.db :as db]
            [rads.rpub.model.sqlite :as sqlite]))

(def schema
  [{:create-table [:custom-fields-groups :if-not-exists]
    :with-columns (concat [(db/uuid-column :id [:primary-key] [:not nil])
                           [:name :text [:not nil]]]
                          sqlite/audit-columns)}

   {:create-table [:custom-fields-fields :if-not-exists]
    :with-columns (concat [(db/uuid-column :id [:primary-key] [:not nil])
                           (-> (db/uuid-column :custom-fields-group-id [:not nil])
                               (db/references :custom-fields-group :id))
                           [:name :text [:not nil]]
                           [:type :text [:not nil]]]
                          sqlite/audit-columns)}

   {:create-index [[:custom-fields-field-group-id-idx :if-not-exists]
                   [:custom-fields-fields :custom-fields-group-id]]}])

(def menu-items
  [{:name "Custom Fields"
    :href "/admin/custom-fields"}])

(defn- row->field [row]
  (update row :field-id parse-uuid))

(defn get-fields [{:keys [ds] :as _model}]
  (->> (db/execute! ds {:select [[:cff.id :field-id]
                                 [:cff.custom-fields-group-id :group-id]
                                 [:cfg.name :group-name]
                                 [:cff.name :field-name]
                                 [:cff.type :field-type]]
                        :from [[:custom-fields-fields :cff]]
                        :left-join [[:custom-fields-groups :cfg]
                                    [:= :cfg.id :cff.custom-fields-group-id]]})
       (map row->field)))

(defn fields->groups [fields]
  (->> fields
       (group-by :group-name)
       (map (fn [[k v]] {:group-name k :fields v}))))

(defn create-group! [{:keys [ds] :as _model} group]
  (db/execute-one! ds {:insert-into :custom-fields-groups
                       :values [group]}))

(defn index-page [req]
  (admin/page-handler
    req
    {:title "Custom Fields"
     :primary
     (fn [{:keys [model]}]
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
         (for [group (fields->groups (get-fields model))]
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

(defn routes [opts]
  [["/admin/custom-fields" {:middleware (admin/admin-middleware opts)}
    ["" {:get index-page
         :post create-group}]]])

(defn plugin [_]
  {:name "Custom Fields"
   :description "Add custom fields using the admin UI."
   :schema schema
   :menu-items menu-items
   :routes routes})
