(ns rpub.plugins.backups
  (:require ["react" :refer [useState useEffect]]
            [rpub.lib.html :as html]
            [rpub.lib.reagent :as r]
            [rpub.plugins.admin.impl :as admin-impl]))

(defn backup-download-path [backup]
  (str "/admin/backups/" (:target-path backup)))

(def columns
  [{:name "File"
    :value
    (fn [backup]
      [:a {:class "underline" :href (backup-download-path backup)}
       (:target-path backup)])}

   {:name "Uploaded At"
    :value #(some-> % :updated-at .toLocaleString)}])

(defn backups-page [{:keys [backups]}]
  (let [backups' (map (fn [b] (update b :updated-at #(js/Date. %))) backups)]
    [:div {:class "p-4"}
     [admin-impl/table
      {:title "Backups"
       :rows backups'
       :columns columns}]]))

(html/add-element :backups-page (r/reactify-component backups-page))
