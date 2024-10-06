(ns rpub.plugins.custom-fields.sqlite
  (:require [rpub.lib.db :as db]
            [rpub.model.sqlite :as sqlite]
            [rpub.plugins.custom-fields :as custom-fields]))

(defn- row->field [row]
  (update row :field-id parse-uuid))

(defrecord Model [ds]
  custom-fields/Model
  (schema [_]
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

     {:create-index [[:custom-fields-group-id-idx :if-not-exists]
                     [:custom-fields-fields :custom-fields-group-id]]}])

  (get-fields [_ _]
    (->> (db/execute!
           ds
           {:select [[:cff.id :field-id]
                     [:cff.custom-fields-group-id :group-id]
                     [:cfg.name :group-name]
                     [:cff.name :field-name]
                     [:cff.type :field-type]]
            :from [[:custom-fields-fields :cff]]
            :left-join [[:custom-fields-groups :cfg]
                        [:= :cfg.id :cff.custom-fields-group-id]]})
         (map row->field))))

(defn ->model [opts]
  (let [opts' (merge {:groups-table :custom-fields-groups
                      :fields-table :custom-fields-fields}
                     (select-keys opts [:ds :groups-table :fields-table]))]
    (map->Model opts')))

(defmethod custom-fields/->model :sqlite [opts]
  (->model opts))
