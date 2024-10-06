(ns rpub.plugins.content-types.sqlite
  (:require [rpub.lib.db :as db]
            [rpub.model.sqlite :as sqlite]
            [rpub.plugins.content-types :as content-types]))

(defn- row->field [row]
  (update row :field-id parse-uuid))

(defrecord Model
           [ds
            field-groups-table
            post-types-table]
  content-types/Model
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
                     [:cff.content-types-group-id :group-id]
                     [:cfg.name :group-name]
                     [:cff.name :field-name]
                     [:cff.type :field-type]]
            :from [[:content-types-fields :cff]]
            :left-join [[:content-types-groups :cfg]
                        [:= :cfg.id :cff.content-types-group-id]]})
         (map row->field)))

  (create-group! [_ group]
    (db/execute-one! ds {:insert-into field-groups-table
                         :values [group]})))

(defn ->model [opts]
  (let [opts' (merge {:field-groups-table :content-types-groups
                      :post-types-table :content-types-fields}
                     (select-keys opts [:ds
                                        :field-groups-table
                                        :post-types-table]))]
    (map->Model opts')))

(defmethod content-types/->model :sqlite [opts]
  (->model opts))
