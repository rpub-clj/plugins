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
                    :href "/admin/content-types/movies"}]
   :plugins [{:name "Content Types"
              :href "/admin/content-types"}]})

(defn all-content-types-page [{:keys [::model] :as req}]
  (admin/page-response
    req
    {:title "Content Types"
     :primary
     (let [content-types (->> (get-content-types model {}) (sort-by :name))]
       (for [content-type content-types]
         [:h3.text-2xl.font-semibold
          (:name content-type)]))}))

(defn update-content-types [{:keys [model] :as req}]
  (let [content-type {}]
    (create-content-type! model content-type)
    (all-content-types-page req)))

(defn wrap-content-types [handler]
  (fn [{:keys [db-type] :as req}]
    (let [ds (get-in req [:model :ds])
          model (->model {:db-type db-type :ds ds})
          req' (merge req {::model model})]
      (handler req'))))

(defn single-content-type-page [{:keys [::model path-params] :as req}]
  (let [{:keys [content-type-slug]} path-params
        [content-type] (get-content-types model {:slugs [content-type-slug]})]
    (admin/page-response
      req
      {:title (:name content-type)
       :primary [:div]})))

(defn routes [opts]
  [["/admin/content-types" {:middleware (admin/admin-middleware opts)}
    ["" {:get all-content-types-page
         :post update-content-types}]
    ["/{content-type-slug}" {:get single-content-type-page}]]])

(defn plugin [_]
  {:name "Content Types"
   :description "Add content types using the admin UI."
   :schema (fn [opts] (schema (->model opts)))
   :menu-items menu-items
   :middleware [wrap-content-types]
   :routes routes})

(model/add-plugin ::plugin plugin)
