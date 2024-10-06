(ns rpub.plugins.backups.sqlite
  (:require [rpub.lib.db :as db]
            [rpub.model.sqlite :as sqlite]
            [rpub.plugins.backups :as backups]))

(defn- row->schedule [row]
  (sqlite/row->metadata row))

(defn- row->backup [row]
  (-> (sqlite/row->metadata row)
      (update :schedule-id parse-uuid)))

(defn- backup->row [backup]
  (select-keys backup [:id :schedule-id :file-path :file-hash :created-at
                       :created-by :updated-at :updated-by]))

(defrecord Model [ds backups-table schedules-table]
  backups/Model
  (schema [_]
    [(db/strict
       {:create-table [schedules-table :if-not-exists]
        :with-columns (concat [(db/uuid-column :id [:primary-key] [:not nil])
                               [:interval-ms :integer [:not nil]]]
                              sqlite/audit-columns)})

     (db/strict
       {:create-table [backups-table :if-not-exists]
        :with-columns (concat [(db/uuid-column :id [:primary-key] [:not nil])
                               (-> (db/uuid-column :schedule-id [:not nil])
                                   (db/references schedules-table :id))
                               [:file-path :text [:not nil]]
                               [:file-hash :text [:not nil]]]
                              sqlite/audit-columns)})])

  (get-schedule [_ _]
    (some-> (db/execute-one!
              ds
              {:select [:*]
               :from [schedules-table]
               :order-by [[:created-at :desc]]
               :limit 1})
            row->schedule))

  (get-backups [_ _]
    (->> (db/execute!
           ds
           {:select [:*]
            :from [backups-table]})
         (map row->backup)))

  (get-latest-backup [_ _]
    (some-> (db/execute-one!
              ds
              {:select [:*]
               :from [backups-table]
               :order-by [[:created-at :desc]]
               :limit 1})
            row->backup))

  (create-backup! [_ backup]
    (db/execute-one!
      ds
      {:insert-into backups-table
       :values [(backup->row backup)]})))

(defn ->model [opts]
  (let [opts' (merge {:backups-table :backup-backups
                      :schedules-table :backup-schedules}
                     (select-keys opts [:ds
                                        :backups-table
                                        :schedules-table]))]
    (map->Model opts')))

(defmethod backups/->model :sqlite [opts]
  (->model opts))
