(ns rpub.plugins.content-types.sqlite
  (:require [clojure.set :as set]
            [rpub.lib.db :as db]
            [rpub.model.sqlite :as sqlite]
            [rpub.plugins.content-types :as content-types]))

(defn row->content-type [row]
  (-> (sqlite/row->metadata row)))

(defn row->content-type-field [row]
  (-> (sqlite/row->metadata row)
      (update :content-type-id parse-uuid)
      (update :field-id parse-uuid)))

(defn add-fields [content-types content-type-fields]
  (let [index (group-by :content-type-id content-type-fields)]
    (map (fn [content-type]
           (let [fields (-> (get index (:id content-type))
                            (set/rename {:field-id :id
                                         :field-name :name
                                         :field-type :type
                                         :content-type-field-rank :rank}))]
             (assoc content-type :fields fields)))
         content-types)))

(defn- get-content-type-fields
  [{:keys [ds content-type-fields-table fields-table] :as _model}
   {:keys [content-type-ids] :as _opts}]
  (->> (db/execute!
         ds
         {:select [[:ctf.content-type-id :content-type-id]
                   [:ctf.rank :content-type-field-rank]
                   [:f.id :field-id]
                   [:f.name :field-name]
                   [:f.type :field-type]]
          :from [[content-type-fields-table :ctf]]
          :join [[fields-table :f] [:= :f.id :ctf.field-id]]
          :where [:in :content-type-id content-type-ids]})
       (map row->content-type-field)))

(defrecord Model
           [ds
            fields-table
            content-types-table
            content-type-fields-table]
  content-types/Model
  (schema [_]
    [(db/strict
       {:create-table [fields-table :if-not-exists]
        :with-columns
        (concat [(db/uuid-column :id [:primary-key] [:not nil])
                 [:name :text [:not nil]]
                 [:type :text [:not nil]]]
                sqlite/audit-columns)})

     (db/strict
       {:create-table [content-types-table :if-not-exists]
        :with-columns
        (concat [(db/uuid-column :id [:primary-key] [:not nil])
                 [:name :text [:not nil]]
                 [:slug :text [:not nil]]]
                sqlite/audit-columns)})

     (db/strict
       {:create-table [content-type-fields-table :if-not-exists]
        :with-columns
        (concat [(db/uuid-column :id [:primary-key] [:not nil])
                 (-> (db/uuid-column :content-type-id)
                     (db/references :content-types :id))
                 (-> (db/uuid-column :field-id)
                     (db/references :fields :id))
                 [:rank :integer [:not nil]]]
                sqlite/audit-columns)})])

  (get-content-types [this {:keys [slugs]}]
    (let [sql (cond-> {:select [:id :name :slug]
                       :from content-types-table}
                slugs (assoc :where [:in :slug slugs]))
          content-types (->> (db/execute! ds sql)
                             (map row->content-type))
          content-type-fields (get-content-type-fields
                                this
                                {:content-type-ids (map :id content-types)})]
      (add-fields content-types content-type-fields)))

  (create-content-type! [_ _]
    {}))

(defn ->model [opts]
  (let [defaults {:fields-table :fields
                  :content-types-table :content-types
                  :content-type-fields-table :content-type-fields}
        opts' (merge defaults (select-keys opts (into [:ds] (keys defaults))))]
    (map->Model opts')))

(defmethod content-types/->model :sqlite [opts]
  (->model opts))
