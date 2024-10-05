(ns rpub.plugins.seo.sqlite
  (:require [rpub.lib.db :as db]
            [rpub.model.sqlite :as sqlite]
            [rpub.plugins.seo :as seo]))

(defn- row->meta-tag [row]
  (update row :id parse-uuid))

(defrecord Model [ds meta-tags-table]
  seo/Model
  (schema [_]
    [{:create-table [meta-tags-table :if-not-exists]
      :with-columns (concat [(db/uuid-column :id [:primary-key] [:not nil])
                             [:name :text]
                             [:property :text]
                             [:content :text]]
                            sqlite/audit-columns)}])

  (get-meta-tags [_ _opts]
    (->> (db/execute! ds {:select [:*] :from [meta-tags-table]})
         (map row->meta-tag))))

(defn ->model [opts]
  (let [opts' (merge {:meta-tags-table :meta-tags}
                     (select-keys opts [:ds :meta-tags-table]))]
    (map->Model opts')))

(defmethod seo/->model :sqlite [opts]
  (->model opts))
