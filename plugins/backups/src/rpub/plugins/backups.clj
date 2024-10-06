(ns rpub.plugins.backups
  (:require [clojure.pprint :as pprint]
            [rpub.admin :as admin]
            [rpub.model :as model]))

(defprotocol Model
  (schema [model])
  (get-backups [model opts])
  (create-backup! [model opts]))

(defmulti ->model :db-type)

(def menu-items
  [{:name "Backups"
    :href "/admin/backups"}])

(defn index-page [req]
  (admin/page-handler
    req
    {:title "Backups"
     :primary
     (fn [{:keys [::model]}]
       [:div
        (for [backup (get-backups model {})]
          [:pre (with-out-str (pprint/pprint backup))])])}))

(defn wrap-backups [handler]
  (fn [{:keys [db-type] :as req}]
    (let [ds (get-in req [:model :ds])
          model (->model {:db-type db-type :ds ds})
          req' (merge req {::model model})]
      (handler req'))))

(defn routes [opts]
  [["/admin/backups" {:middleware (admin/admin-middleware opts)}
    ["" {:get index-page}]]])

(defn plugin [_]
  {:name "Backups"
   :description "Back up your site."
   :schema (fn [opts] (schema (->model opts)))
   :menu-items menu-items
   :middleware [wrap-backups]
   :routes routes})

(model/add-plugin ::plugin plugin)
