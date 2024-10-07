(ns ^:clj-reload/no-unload rpub.plugins.backups
  (:require [buddy.core.codecs :as codecs]
            [buddy.core.hash :as hash]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.tools.logging :as log]
            [rpub.admin :as admin]
            [rpub.model :as model])
  (:import (java.io ByteArrayOutputStream FileOutputStream)
           (java.lang AutoCloseable)
           (java.time Duration Instant)
           (java.util.zip ZipEntry ZipOutputStream)))

^:clj-reload/keep
(defprotocol Model
  (schema [model])
  (get-schedule [model opts])
  (get-backups [model opts])
  (get-latest-backup [model opts])
  (create-backup! [model opts]))

(defmulti ->model :db-type)

(defn menu-items [_]
  {:plugins [{:name "Backups"
              :href "/admin/backups"}]})

(defn index-page [{:keys [::model] :as req}]
  (admin/page-response
    req
    {:title "Backups"
     :primary
     [:div
      (for [backup (get-backups model {})]
        [:pre (with-out-str (pprint/pprint backup))])]}))

(defonce current-timer (atom nil))

(defn needs-backup? [latest-backup {:keys [interval-ms] :as _schedule}]
  (or (not latest-backup)
      (let [now (Instant/now)
            duration (Duration/between (:created-at latest-backup) now)]
        (>= (.toMillis duration) interval-ms))))

(defn- file-hash [file-data]
  (-> file-data hash/sha256 codecs/bytes->hex))

(defn ->backup [{:keys [id schedule identity file-data]}]
  (as-> {:id (or id (random-uuid))
         :schedule-id (:id schedule)
         :file-data file-data
         :file-hash (file-hash file-data)} $
    (assoc $ :file-path (str (:file-hash $) ".zip"))
    (model/add-metadata $ identity)))

(defn- zip [bytes]
  (let [byte-out (ByteArrayOutputStream.)]
    (with-open [zip-out (ZipOutputStream. byte-out)]
      (.putNextEntry zip-out (ZipEntry. "main.txt"))
      (.write zip-out ^bytes bytes)
      (.closeEntry zip-out))
    (.toByteArray byte-out)))

(defn- write-file! [backups-dir {:keys [file-path file-data] :as _backup}]
  (let [backup-file (io/file backups-dir file-path)]
    (when-not (.exists backup-file)
      (io/make-parents backup-file)
      (with-open [out (FileOutputStream. backup-file)]
        (.write out ^bytes (zip file-data))))))

(defn default-backups-dir []
  (io/file "data" "backups"))

(defn- do-backup! [{:keys [::model config-promise]}]
  (try
    (when (realized? config-promise)
      (let [latest-backup (get-latest-backup model {})
            schedule (get-schedule model {})]
        (when (and schedule (needs-backup? latest-backup schedule))
          (let [backups-dir (default-backups-dir)
                file-data (.getBytes "hello world")
                new-backup (->backup {:identity admin/system-user
                                      :schedule schedule
                                      :file-data file-data})]
            (write-file! backups-dir new-backup)
            (create-backup! model new-backup)))))
    (catch Throwable e
      (log/error e))))

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
