(ns rpub.plugins.backups
  (:require [clojure.pprint :as pprint]
            [rpub.admin :as admin]
            [rpub.model :as model])
  (:import (java.lang AutoCloseable)
           (java.time Duration Instant LocalDateTime)))

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

(defonce current-timer (atom nil))

(defn needs-backup? [last-backup schedule]
  true
  #_(let [now (LocalDateTime/now)]
      (>= (.toMinutes (Duration/between last-backup now)) schedule)))

(defn ->backup [opts]
  opts)

(defn- do-backup! [model]
  (let [last-backup nil
        schedule nil]
    (when (needs-backup? last-backup schedule)
      (create-backup! model (->backup {:created-at (Instant/now)})))))

(defn start-timer! [interval-ms callback]
  (let [running (atom true)]
    (.start
      (Thread.
        (fn []
          (while @running
            (callback)
            (Thread/sleep ^long interval-ms)))))
    (reify AutoCloseable
      (close [_] (reset! running false)))))

(defn wrap-backups [handler]
  (fn [{:keys [db-type] :as req}]
    (let [ds (get-in req [:model :ds])
          model (->model {:db-type db-type :ds ds})
          req' (merge req {::model model})]
      (handler req'))))

(defn update-backups [{:keys [form-params model] :as req}]
  (let [interval-seconds (some-> (get form-params "interval-seconds")
                                 Integer/parseInt)
        interval-ms (* interval-seconds 1000)]
    (some-> @current-timer .close)
    (reset! current-timer (start-timer! interval-ms #(do-backup! model)))
    (index-page req)))

(def default-interval-ms 60000)

(defn init [opts]
  (let [model (->model opts)
        timer (start-timer! default-interval-ms #(do-backup! model))]
    (reset! current-timer timer)))

(defn routes [opts]
  [["/admin/backups" {:middleware (admin/admin-middleware opts)}
    ["" {:get index-page
         :post update-backups}]]])

(defn plugin [_]
  {:name "Backups"
   :description "Back up your site."
   :schema (fn [opts] (schema (->model opts)))
   :menu-items menu-items
   :init init
   :middleware [wrap-backups]
   :routes routes})

(model/add-plugin ::plugin plugin)
