(ns rpub.plugins.backups.sqlite
  (:require [clojure.string :as str]
            [rads.migrate :as migrate]
            [rads.migrate.next-jdbc :as migrate-next-jdbc]
            [rpub.lib.db :as db]
            [rpub.model.sqlite :as sqlite]
            [rpub.plugins.backups :as backups]))

(defn- row->schedule [row]
  (sqlite/row->metadata row))

(defn- row->backup [row]
  (-> (sqlite/row->metadata row)
      (update :status keyword)
      (update :schedule-id parse-uuid)))

(defn- backup->row [backup]
  (-> backup
      (update :status name)
      (select-keys [:id :schedule-id :status :source-path :target-path :sha-256
                    :created-at :created-by :updated-at :updated-by])))

(defn- initial-schema [{:keys [schedules-table backups-table]}]
  [(db/strict
     {:create-table [schedules-table :if-not-exists]
      :with-columns (concat [(db/uuid-column :id [:primary-key] [:not nil])
                             [:interval-ms :integer [:not nil]]]
                            db/audit-columns)})

   (db/strict
     {:create-table [backups-table :if-not-exists]
      :with-columns (concat [(db/uuid-column :id [:primary-key] [:not nil])
                             (-> (db/uuid-column :schedule-id [:not nil])
                                 (db/references schedules-table :id))
                             [:status :text [:not nil]]
                             [:source-path :text [:not nil]]
                             [:target-path :text]
                             [:sha-256 :text]]
                            db/audit-columns)})])

(defn- migrations [model]
  (let [{:keys [meta-tags-table]} model]
    [{:id :initial-schema
      :migrate (fn [{:keys [tx]}]
                 (doseq [stmt (initial-schema model)]
                   (db/execute-one! tx stmt)))
      :rollback (fn [{:keys [tx]}]
                  (doseq [table [meta-tags-table]]
                    (db/execute-one! tx {:drop-table table})))}]))

(defn- migration-config [model]
  (let [{:keys [ds migration-events-table]} model]
    {:migrations (migrations model)
     :storage (migrate-next-jdbc/storage
                {:ds ds
                 :events-table migration-events-table})}))

(defrecord Model [ds source-path backups-table schedules-table]
  backups/Model
  (migrate! [model]
    (migrate/migrate! (migration-config model)))

  (source-path [_]
    source-path)

  (get-schedules [_ {:keys [order-by limit]}]
    (let [sql (cond-> {:select [:*]
                       :from [schedules-table]}
                order-by (assoc :order-by [order-by])
                limit (assoc :limit limit))]
      (->> (db/execute! ds sql)
           (map row->schedule))))

  (get-backups [_ {:keys [order-by limit]}]
    (let [sql (cond-> {:select [:*]
                       :from [backups-table]}
                order-by (assoc :order-by [order-by])
                limit (assoc :limit limit))]
      (->> (db/execute! ds sql)
           (map row->backup))))

  (start-backup! [_ backup]
    (let [backup' (assoc backup :status :started)]
      (db/execute-one!
        ds
        {:insert-into backups-table
         :values [(backup->row backup')]})))

  (finish-backup! [_ backup]
    (let [values (-> backup
                     (assoc :status :finished)
                     (select-keys [:status :sha-256 :target-path :updated-at
                                   :updated-by])
                     backup->row)]
      (db/execute-one!
        ds
        {:update backups-table
         :set values
         :where [:= :id (:id backup)]}))))

(defn ->model [opts]
  (let [required-keys [:ds :source-path]
        defaults {:backups-table :backups-backups
                  :schedules-table :backups-schedules
                  :migration-events-table :backups-migration-events}
        opts' (merge defaults
                     (select-keys opts (merge required-keys (keys defaults))))]
    (map->Model opts')))

(defn- db-path [database-url]
  (str/replace database-url "jdbc:sqlite:" ""))

(defmethod backups/->model :sqlite [{:keys [database-url] :as opts}]
  (let [opts' (assoc opts :source-path (db-path database-url))]
    (->model opts')))
