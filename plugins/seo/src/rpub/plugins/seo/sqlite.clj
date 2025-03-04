(ns rpub.plugins.seo.sqlite
  (:require [rads.migrate :as migrate]
            [rads.migrate.next-jdbc :as migrate-next-jdbc]
            [rpub.lib.db :as db]
            [rpub.plugins.seo :as seo]))

(defn- row->meta-tag [row]
  (update row :id parse-uuid))

(defn- meta-tag->row [meta-tag]
  meta-tag)

(defn- initial-schema [{:keys [meta-tags-table]}]
  [(db/strict
     {:create-table [meta-tags-table :if-not-exists]
      :with-columns (concat [(db/uuid-column :id [:primary-key] [:not nil])
                             [:name :text]
                             [:property :text]
                             [:content :text]]
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

(defrecord Model [ds meta-tags-table]
  seo/Model
  (migrate! [model]
    (migrate/migrate! (migration-config model)))

  (get-meta-tags [_ {:keys [ids]}]
    (let [sql (cond-> {:select [:*]
                       :from [meta-tags-table]}
                (seq ids) (assoc :where [:in :id ids]))]
      (->> (db/execute! ds sql)
           (map row->meta-tag))))

  (update-meta-tag! [_ meta-tag]
    (db/execute-one! ds {:insert-into meta-tags-table
                         :values [(meta-tag->row meta-tag)]
                         :on-conflict :id
                         :do-update-set [:content :updated-by :updated-at]})))

(defn ->model [opts]
  (let [opts' (merge {:meta-tags-table :meta-tags
                      :migration-events-table :seo-migration-events}
                     (select-keys opts [:ds :meta-tags-table]))]
    (map->Model opts')))

(defmethod seo/->model :sqlite [opts]
  (->model opts))
