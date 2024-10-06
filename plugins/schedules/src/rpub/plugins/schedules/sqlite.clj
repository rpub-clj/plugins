(ns rpub.plugins.schedules.sqlite
  (:require [rpub.lib.db :as db]
            [rpub.model.sqlite :as sqlite]
            [rpub.plugins.schedules :as schedules]))

(defn- row->schedule [row]
  (sqlite/row->metadata row))

(defrecord Model [ds schedules-table]
  schedules/Model
  (schema [_]
    [(db/strict
       {:create-table [schedules-table :if-not-exists]
        :with-columns (concat [(db/uuid-column :id [:primary-key] [:not nil])
                               (-> (db/uuid-column :post-id [:not nil])
                                   (db/references :post :id))
                               [:publish-at :text [:not nil]]]
                              sqlite/audit-columns)})])

  (get-schedules [_ _]
    (some-> (db/execute-one!
              ds
              {:select [:*]
               :from [schedules-table]
               :order-by [[:created-at :desc]]})
            row->schedule))

  (update-schedules! [_ _]))

(defn ->model [opts]
  (let [opts' (merge {:schedules-table :schedules-schedules}
                     (select-keys opts [:ds :schedules-table]))]
    (map->Model opts')))

(defmethod schedules/->model :sqlite [opts]
  (->model opts))
