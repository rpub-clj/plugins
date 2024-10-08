(ns ^:clj-reload/no-unload rpub.plugins.content-types
  (:require [rpub.admin :as admin]
            [rpub.model :as model]))

^:clj-reload/keep
(defprotocol Model
  (schema [model])
  (get-content-types [model opts])
  (create-content-type! [model opts]))

(defmulti ->model :db-type)

(defn menu-items [{:keys [::model]}]
  {:content-types (->> (get-content-types model {})
                       (map (fn [{:keys [name slug]}]
                              {:name name
                               :href (str "/admin/content-types/" slug)})))
   :plugins [{:name "Content Types"
              :href "/admin/content-types"}]})

(defn content-type-fields-form [{:keys [content-type]}]
  [:div
   (for [[i field] (map-indexed vector (sort-by :rank (:fields content-type)))]
     [:div.mb-2.pb-2.pt-2
      {:class (str "border-b " (when (= i 0) "border-t"))}
      [:label {:for :field-name}]
      [:input
       {:type :text
        :class (str "px-2 py-1 font-semibold border border-gray-300 "
                    "rounded-[6px] mr-4")
        :name :field-name
        :value (:name field)}]
      [:label {:for :field-type}]
      [:select
       {:class (str "appearance-none px-2 py-1 border border-gray-300 "
                    "rounded-[6px] mr-4")
        :name :field-type}
       [:option (if (= (:type field) :text) {:selected true})
        "Text"]
       [:option (if (= (:type field) :number) {:selected true})
        "Number"]]
      (admin/button "Remove")])
   (admin/button "Add Field")])

(defn all-content-types-page [{:keys [::model] :as req}]
  (admin/page-response
    req
    {:title "Content Types"
     :primary
     (let [content-types (->> (get-content-types model {}) (sort-by :name))]
       [:div
        [:div.mb-8 (admin/button "Add Content Type")]
        (for [content-type content-types]
          [:div.mb-8
           [:h3.text-2xl.font-semibold.mb-4
            (:name content-type)]
           (content-type-fields-form {:content-type content-type})])])}))

(defn update-content-types [{:keys [::model] :as req}]
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
       :primary
       [:div.mb-8
        [:h3.text-2xl.font-semibold.mb-4 "Fields"]
        (content-type-fields-form {:content-type content-type})]})))

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
