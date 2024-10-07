(ns rpub.plugins.content-types
  (:require [rpub.admin :as admin]
            [rpub.model :as model]))

(defprotocol Model
  (schema [model])
  (get-content-types [model opts])
  (create-content-type! [model opts]))

(defmulti ->model :db-type)

(defn menu-items [_]
  {:content-types [{:name "Movies"
                    :href "/admin/content-types"}]
   :plugins [{:name "Content Types"
              :href "/admin/content-types"}]})

(defn content-types-page [req]
  (admin/page-response
    req
    {:title "Content Types"
     :primary
     (fn [{:keys [::model]}]
       (for [content-type (->> (get-content-types model {}) (sort-by :name))]
         [:h3.text-2xl.font-semibold
          (:name content-type)]))}))

(defn update-content-types [{:keys [model] :as req}]
  (let [content-type {}]
    (create-content-type! model content-type)
    (content-types-page req)))

(defn wrap-content-types [handler]
  (fn [{:keys [db-type] :as req}]
    (let [ds (get-in req [:model :ds])
          model (->model {:db-type db-type :ds ds})
          req' (merge req {::model model})]
      (handler req'))))

(defn routes [opts]
  [["/admin/content-types" {:middleware (admin/admin-middleware opts)}
    ["" {:get content-types-page
         :post update-content-types}]]])

(defn plugin [_]
  {:name "Content Types"
   :description "Add content types using the admin UI."
   :schema (fn [opts] (schema (->model opts)))
   :menu-items menu-items
   :middleware [wrap-content-types]
   :routes routes})

(model/add-plugin ::plugin plugin)
