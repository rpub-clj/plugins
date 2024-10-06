(ns rpub.plugins.custom-fields.sqlite
  (:require [rpub.lib.db :as db]
            [rpub.model.sqlite :as sqlite]
            [rpub.plugins.custom-fields :as custom-fields]))

(defn- row->field [row]
  (update row :field-id parse-uuid))

(defrecord Model
           [ds
            field-groups-table
            post-types-table]
  custom-fields/Model
  (schema [_]
    [(db/strict
       {:create-table [field-groups-table :if-not-exists]
        :with-columns (concat [(db/uuid-column :id [:primary-key] [:not nil])
                               [:name :text [:not nil]]]
                              sqlite/audit-columns)})

     (db/strict
       {:create-table [post-types-table :if-not-exists]
        :with-columns (concat [(db/uuid-column :id [:primary-key] [:not nil])
                               [:name :text [:not nil]]
                               [:slug :text [:not nil]]]
                              sqlite/audit-columns)})])

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
         (map row->field)))

  (create-group! [_ group]
    (db/execute-one! ds {:insert-into field-groups-table
                         :values [group]})))

(defn ->model [opts]
  (let [opts' (merge {:field-groups-table :custom-fields-groups
                      :post-types-table :custom-fields-fields}
                     (select-keys opts [:ds
                                        :field-groups-table
                                        :post-types-table]))]
    (map->Model opts')))

(defmethod custom-fields/->model :sqlite [opts]
  (->model opts))
