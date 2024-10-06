(ns rpub.plugins.backups.sqlite
  (:require [rpub.lib.db :as db]
            [rpub.model.sqlite :as sqlite]
            [rpub.plugins.backups :as backups]))

(defrecord Model [ds backups-table]
  backups/Model
  (schema [_]
    [{:create-table [backups-table :if-not-exists]
      :with-columns (concat [(db/uuid-column :id [:primary-key] [:not nil])
                             [:file-path :text [:not nil]]]
                            sqlite/audit-columns)}])

  (get-backups [_ _])
  (create-backup! [_ _]))

(defn ->model [opts]
  (let [opts' (merge {:backups-table :backups}
                     (select-keys opts [:ds :backups-table]))]
    (map->Model opts')))

(defmethod backups/->model :sqlite [opts]
  (->model opts))
