(ns rpub.plugins.backups
  (:require [babashka.fs :as fs]
            [chime.core :as ch]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.tools.logging.readable :as logr]
            [hiccup2.core :as hiccup]
            [rpub.admin :as admin]
            [rpub.core :as rpub]
            [rpub.lib.html :as html]
            [rpub.model :as model])
  (:import (io.minio MinioClient UploadObjectArgs)
           (java.io BufferedOutputStream FileInputStream FileOutputStream)
           (java.security MessageDigest)
           (java.time Duration Instant)
           (java.util.zip ZipEntry ZipOutputStream)
           (javax.xml.bind DatatypeConverter)))

^:clj-reload/keep
(defprotocol Model
  (migrate! [model])
  (get-schedules [model opts])
  (get-backups [model opts])
  (source-path [model])
  (start-backup! [model opts])
  (finish-backup! [model opts]))

(defmulti ->model :db-type)

(def backups-script "import '/js/rpub/plugins/backups.js';")

(defn backups-handler [{:keys [::model] :as req}]
  (let [backups (get-backups model {})]
    (admin/page-response
      req
      {:title "Backups"
       :primary
       [:div
        [:script {:type :module} (hiccup/raw backups-script)]
        (html/cljs
          [:backups-page {:backups backups}]
          {:format :json})]})))

(defn needs-backup? [latest-backup schedule]
  (and schedule
       (or (not latest-backup)
           (let [now (Instant/now)
                 duration (Duration/between (:created-at latest-backup) now)]
             (>= (.toMillis duration) (:interval-ms schedule))))))

(defn ->backup [{:keys [id schedule source-path temp-dir current-user]}]
  (as-> {:id (or id (random-uuid))
         :schedule-id (:id schedule)
         :source-path source-path
         :source-zip (str (fs/file temp-dir (str (fs/file-name source-path) ".zip")))} $
    (model/add-metadata $ current-user)))

(defn ->minio-client [{:keys [endpoint access-key secret-key] :as _params}]
  (-> (MinioClient/builder)
      (.endpoint ^String endpoint)
      (.credentials ^String access-key ^String secret-key)
      .build))

(defn upload-object! [minio-client {:keys [bucket object filename] :as _params}]
  (let [args (-> (UploadObjectArgs/builder)
                 (.bucket ^String bucket)
                 (.object ^String object)
                 (.filename ^String filename)
                 .build)]
    (.uploadObject minio-client args)))

(defn upload-backup! [minio-client backup]
  (upload-object! minio-client
                  {:bucket "backups"
                   :object (:target-path backup)
                   :filename (:source-zip backup)}))

(defn- zip-file! [input-file output-zip]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (with-open [fos (FileOutputStream. ^String output-zip)
                zos (ZipOutputStream. (BufferedOutputStream. fos))]
      (let [file (fs/file input-file)]
        (.putNextEntry zos (ZipEntry. (.getName file)))
        (with-open [fis (FileInputStream. file)]
          (let [buffer (byte-array 1024)]
            (loop []
              (let [len (.read fis buffer)]
                (when (pos? len)
                  (.update digest buffer 0 len)
                  (.write zos buffer 0 len)
                  (recur))))))))
    {:sha-256 (str/lower-case (DatatypeConverter/printHexBinary (.digest digest)))}))

(defn compress-backup! [backup]
  (zip-file! (:source-path backup) (:source-zip backup)))

(defn- do-backup! [{:keys [::model ::minio-client]}]
  (log/info "Checking backup schedule")
  (try
    (let [[latest-backup] (get-backups model {:order-by [:created-at :desc]
                                              :limit 1})
          [schedule] (get-schedules model {:order-by [:created-at :desc]
                                           :limit 1})]
      (if-not (needs-backup? latest-backup schedule)
        (log/info "No backup needed")
        (fs/with-temp-dir [temp-dir {}]
          (let [new-backup (->backup {:current-user admin/system-user
                                      :schedule schedule
                                      :source-path (source-path model)
                                      :temp-dir temp-dir})
                log-info (select-keys new-backup [:id])]
            (logr/info "Starting backup" log-info)
            (start-backup! model new-backup)
            (let [{:keys [sha-256]} (compress-backup! new-backup)
                  target-path (str sha-256 ".zip")
                  new-backup' (merge new-backup
                                     {:sha-256 sha-256
                                      :target-path target-path
                                      :updated-at (Instant/now)
                                      :updated-by (:id admin/system-user)})]
              (upload-backup! @minio-client new-backup')
              (finish-backup! model new-backup')
              (logr/info "Finished backup" log-info))))))
    (catch Throwable e
      (log/error e))))

(def admin-menu-items
  {:plugins [{:label "Backups"
              :href "/admin/backups"}]})

(defn wrap-backups [handler]
  (fn [{:keys [db-type database-url inline-scripts] :as req}]
    (let [ds (get-in req [:model :ds])
          backup-model (->model {:db-type db-type
                                 :ds ds
                                 :database-url database-url})
          inline-scripts' (conj inline-scripts backups-script)
          admin-menu-items' (conj (:admin-menu-items req) admin-menu-items)
          req' (merge req {::model backup-model
                           :inline-scripts inline-scripts'
                           :admin-menu-items admin-menu-items'})]
      (handler req'))))

(defn default-backup-schedule []
  (ch/periodic-seq (Instant/now) (Duration/ofHours 1)))

(defn init-minio-client [{:keys [config] :as _req}]
  (let [{:keys [::s3-endpoint ::s3-access-key ::s3-secret-key]} config]
    (when (and s3-endpoint s3-access-key s3-secret-key)
      (->minio-client {:endpoint s3-endpoint
                       :access-key s3-access-key
                       :secret-key s3-secret-key}))))

(defonce current-timer (atom nil))

(defn init [{:keys [model db-type database-url] :as opts}]
  (let [backup-model (->model {:db-type db-type
                               :ds (:ds model)
                               :database-url database-url})
        minio-client (init-minio-client opts)
        opts' (merge opts {::model backup-model
                           ::minio-client minio-client})]
    (when (and minio-client (not @current-timer))
      (compare-and-set!
        current-timer
        nil
        (ch/chime-at
          (default-backup-schedule)
          (fn [_] (do-backup! opts')))))))

(defn routes [opts]
  [["/admin/backups" {:middleware (admin/admin-middleware opts)
                      :get backups-handler}]])

(defn init [{:keys [model] :as opts}]
  (let [db-info (model/db-info model)
        _ (case (:db-type db-info)
            :sqlite (require 'rpub.plugins.backups.sqlite))
        model (->model (merge db-info (select-keys opts [:current-user
                                                         :database-url])))]
    (migrate! model)))

(defn middleware [_]
  [wrap-backups])

(defmethod rpub/plugin ::plugin [_]
  {:label "Backups"
   :description "Back up your site."
   :init init
   :middleware middleware
   :routes routes})
