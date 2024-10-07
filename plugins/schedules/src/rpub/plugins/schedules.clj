(ns rpub.plugins.schedules
  (:require [clojure.pprint :as pprint]
            [rpub.admin :as admin]
            [rpub.model :as model])
  (:import (java.lang AutoCloseable)))

(defprotocol Model
  (schema [model])
  (get-schedules [model opts])
  (update-schedules! [model schedules]))

(defmulti ->model :db-type)

(defn menu-items [_]
  {:plugins [{:name "Schedules"
              :href "/admin/schedules"}]})

(defn schedules-page [req]
  (admin/page-response
    req
    {:title "Schedules"
     :primary
     (fn [{:keys [::model]}]
       [:div
        (for [schedule (get-schedules model {})]
          [:pre (with-out-str (pprint/pprint schedule))])])}))

(defonce current-timer (atom nil))

(defn wrap-schedules [handler]
  (fn [{:keys [db-type] :as req}]
    (let [ds (get-in req [:model :ds])
          model (->model {:db-type db-type :ds ds})
          req' (merge req {::model model})]
      (handler req'))))

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

(defn update-schedules [req]
  (schedules-page req))

(def default-interval-ms 60000)

(defn run-schedules! [_])

(defn init [opts]
  (let [model (->model {:db-type (:db-type opts)
                        :ds (get-in opts [:model :ds])})
        opts' (assoc opts ::model model)
        timer (start-timer! default-interval-ms #(run-schedules! opts'))]
    (reset! current-timer timer)))

(defn routes [opts]
  [["/admin/schedules" {:middleware (admin/admin-middleware opts)}
    ["" {:get schedules-page
         :post update-schedules}]]])

(defn plugin [_]
  {:name "Schedules"
   :description "Create schedules for posts."
   :schema (fn [opts] (schema (->model opts)))
   :menu-items menu-items
   :init init
   :middleware [wrap-schedules]
   :routes routes})

(model/add-plugin ::plugin plugin)
