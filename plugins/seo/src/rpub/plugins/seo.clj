(ns rpub.plugins.seo
  (:require [rpub.admin :as admin]
            [rpub.model :as model]))

(defprotocol Model
  (schema [model])
  (get-meta-tags [model opts]))

(defn add-meta-tags [head meta-tags]
  (let [elements (map (fn [attrs]
                        [:meta (select-keys attrs [:name :content :property])])
                      meta-tags)]
    (into head elements)))

(defmulti ->model :db-type)

(defn wrap-seo [handler]
  (fn [{:keys [head db-type] :as req}]
    (let [ds (get-in req [:model :ds])
          model (->model {:db-type db-type :ds ds})
          meta-tags (get-meta-tags model {})
          head' (add-meta-tags head meta-tags)
          req' (merge req {::model model :head head'})]
      (handler req'))))

(defn meta-tags-page [req]
  (admin/page-handler
    req
    {:title "Search Engine Optimization (SEO)"
     :primary
     (fn [{:keys [::model]}]
       (for [meta-tag (get-meta-tags model {})]
         [:pre (pr-str meta-tag)]))}))

(defn routes [opts]
  [["/admin/seo" {:get meta-tags-page
                  :middleware (admin/admin-middleware opts)}]])

(defn plugin [_]
  {:id #uuid"a1e00bf3-556a-46db-86e0-e8b74d1b728d"
   :name "Search Engine Optimization (SEO)"
   :description "Adds meta tags and site maps."
   :schema (fn [opts] (schema (->model opts)))
   :middleware [wrap-seo]
   :routes routes})

(model/add-plugin ::plugin plugin)
