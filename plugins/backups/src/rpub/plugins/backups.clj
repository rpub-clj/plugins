(ns rpub.plugins.backups
  (:require [clojure.pprint :as pprint]
            [rpub.admin :as admin]
            [rpub.model :as model])
  (:import (java.lang AutoCloseable)
           (java.time Duration Instant)))

(defprotocol Model
  (schema [model])
  (get-schedule [model opts])
  (get-backups [model opts])
  (get-latest-backup [model opts])
  (create-backup! [model opts]))

(defmulti ->model :db-type)

(def menu-items
  [{:name "Backups"
    :href "/admin/backups"}])

(defn index-page [req]
  (admin/page-response
    req
    {:title "Backups"
     :primary
     (fn [{:keys [::model]}]
       [:div
        (for [backup (get-backups model {})]
          [:pre (with-out-str (pprint/pprint backup))])])}))

(defonce current-timer (atom nil))

(defn needs-backup? [latest-backup {:keys [interval-ms] :as _schedule}]
  (or (not latest-backup)
      (let [now (Instant/now)
            duration (Duration/between (:created-at latest-backup) now)]
        (>= (.toMillis duration) interval-ms))))

(defn ->backup [opts]
  opts)

(defn- do-backup! [{:keys [::model config-promise]}]
  (when (realized? config-promise)
    (let [latest-backup (get-latest-backup model {})
          schedule (get-schedule model {})]
      (when (and schedule (needs-backup? latest-backup schedule))
        (let [backup (->backup {:created-at (Instant/now)})]
          (create-backup! model backup))))))

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

(defn update-backups [{:keys [form-params ::model] :as req}]
  (let [interval-seconds (some-> (get form-params "interval-seconds")
                                 Integer/parseInt)
        interval-ms (* interval-seconds 1000)]
    (some-> @current-timer .close)
    (reset! current-timer (start-timer! interval-ms #(do-backup! req)))
    (index-page req)))

(def default-interval-ms 60000)

(defn init [opts]
  (let [model (->model {:db-type (:db-type opts)
                        :ds (get-in opts [:model :ds])})
        opts' (assoc opts ::model model)
        timer (start-timer! default-interval-ms #(do-backup! opts'))]
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
