(ns rpub.plugins.forms.sqlite
  (:require [rpub.lib.db :as db]
            [rpub.model.sqlite :as sqlite]
            [rpub.plugins.forms :as forms]))

(defn- row->message [row]
  (update row :id parse-uuid))

(defrecord Model [ds forms-messages-table]
  forms/Model
  (schema [_]
    [{:create-table [forms-messages-table :if-not-exists]
      :with-columns (concat [(db/uuid-column :id [:primary-key] [:not nil])
                             [:name :text]
                             [:email :text]
                             [:content :text]]
                            sqlite/audit-columns)}])

  (get-messages [_ _opts]
    (->> (db/execute! ds {:select [:*] :from [forms-messages-table]})
         (map row->message))))

(defn ->model [opts]
  (let [opts' (merge {:forms-messages-table :forms-messages}
                     (select-keys opts [:ds :forms-messages-table]))]
    (map->Model opts')))

(defmethod forms/->model :sqlite [opts]
  (->model opts))
