(ns rpub.plugins.contact-form.sqlite
  (:require [rpub.lib.db :as db]
            [rpub.model.sqlite :as sqlite]
            [rpub.plugins.contact-form :as contact-form]))

(defn- row->message [row]
  (update row :id parse-uuid))

(defrecord Model [ds contact-form-messages-table]
  contact-form/Model
  (schema [_]
    [{:create-table [contact-form-messages-table :if-not-exists]
      :with-columns (concat [(db/uuid-column :id [:primary-key] [:not nil])
                             [:name :text]
                             [:email :text]
                             [:content :text]]
                            sqlite/audit-columns)}])

  (get-messages [_ _opts]
    (->> (db/execute! ds {:select [:*] :from [contact-form-messages-table]})
         (map row->message))))

(defn ->model [opts]
  (let [opts' (merge {:contact-form-messages-table :contact-form-messages}
                     (select-keys opts [:ds :contact-form-messages-table]))]
    (map->Model opts')))

(defmethod contact-form/->model :sqlite [opts]
  (->model opts))
