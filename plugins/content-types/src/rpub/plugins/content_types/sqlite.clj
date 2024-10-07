(ns rpub.plugins.content-types.sqlite
  (:require [rpub.lib.db :as db]
            [rpub.model.sqlite :as sqlite]
            [rpub.plugins.content-types :as content-types]))

(defrecord Model [ds content-types-table]
  content-types/Model
  (schema [_]
    [(db/strict
       {:create-table [content-types-table :if-not-exists]
        :with-columns
        (concat [(db/uuid-column :id [:primary-key] [:not nil])
                 [:name :text [:not nil]]
                 [:slug :text [:not nil]]]
                sqlite/audit-columns)})])

  (get-content-types [_ _]
    (db/execute!
      ds
      {:select [:*]
       :from content-types-table}))

  (create-content-type! [_ _]))

(defn ->model [opts]
  (let [opts' (merge {:content-types-table :content-types}
                     (select-keys opts [:ds :content-types-table]))]
    (map->Model opts')))

(defmethod content-types/->model :sqlite [opts]
  (->model opts))
