(ns rpub.plugins.rest-api
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]
            [ring.util.response :as response]
            [rpub.core :as rpub]
            [rpub.plugins.api :as api])
  (:import (java.time Instant)))

(def QueryParameters
  [:map
   [:populate {:optional true}
    [:or :string [:map]]]
   [:fields {:optional true}
    [:seqable [:string {:min 1}]]]
   [:filters {:optional true}
    [:map-of :string [:map-of :string :string]]]
   [:sort {:optional true}
    [:or :string [:seqable :string]]]
   [:pagination
    {:optional true}
    [:orn
     [:by-page
      [:map {:closed true}
       [:page :int]
       [:page-size {:optional true} :int]
       [:with-count {:optional true} :boolean]]]
     [:by-offset
      [:map {:closed true}
       [:start :int]
       [:limit {:optional true} :int]
       [:with-count {:optional true} :boolean]]]]]])

(defn- parse-query-param-arrays [params]
  (reduce-kv
    (fn [acc k v]
      (cond
        ;; Handle "key[]" format
        (re-matches #"(.+)\[\]$" k)
        (let [[_ base-key] (re-matches #"(.+)\[\]$" k)]
          (assoc acc (keyword base-key) v))

        ;; Handle "key[subkey]" format
        (re-matches #"(.+)\[(.+)\]$" k)
        (let [[_ parent-key child-key] (re-matches #"(.+)\[(.+)\]$" k)
              parent-kw (keyword parent-key)
              child-kw (keyword child-key)]
          (update acc parent-kw #(assoc (or % {}) child-kw v)))

        ;; Regular keys
        :else
        (assoc acc (keyword k) v)))
    {}
    params))

(defn ->query-params [params]
  (let [v (->> (parse-query-param-arrays params)
               (cske/transform-keys csk/->kebab-case))]
    (m/coerce #'QueryParameters v (mt/string-transformer) identity identity)))

(defn- content-item->response [content-item]
  (merge (select-keys content-item [:id :created-at :updated-at])
         (if (:fields content-item)
           (dissoc (:fields content-item) [:id :created-at :updated-at])
           (:document content-item))))

(defn bad-request [explanation]
  (let [errors (->> (me/humanize explanation)
                    (cske/transform-keys #(if (or (string? %) (keyword? %)) (csk/->camelCase %) %)))]
    (response/bad-request {:errors errors})))

(defn list-content-items-handler [{:keys [model path-params query-params] :as _req}]
  (let [query-params' (->query-params query-params)]
    (if-let [explanation (:explain query-params')]
      (bad-request explanation)
      (let [content-type-slug (keyword (:content-type-slug path-params))
            content-items (->> (rpub/get-content-items
                                 model
                                 {:content-type-slugs [content-type-slug]})
                               (map content-item->response))]
        (response/response {:data content-items})))))

(defn create-content-item-handler [{:keys [model path-params body-params current-user]}]
  (let [content-type-slug (keyword (:content-type-slug path-params))
        [content-type] (rpub/get-content-types
                         model
                         {:content-type-slugs [content-type-slug]})
        document (-> (:data body-params)
                     (update-keys #(parse-uuid (name %))))
        content-item (rpub/content-item {:content-type content-type
                                         :document document
                                         :current-user current-user})]
    (rpub/create-content-item! model content-item)
    {:status 201
     :headers {}
     :body {:data (content-item->response content-item)}}))

(defn get-content-item-handler [{:keys [model path-params] :as _req}]
  (let [{:keys [content-item-id]} path-params
        content-type-slug (keyword (:content-type-slug path-params))
        content-item-id (parse-uuid content-item-id)
        [content-item] (rpub/get-content-items
                         model
                         {:content-type-slugs [content-type-slug]
                          :content-item-ids [content-item-id]})]
    (if content-item
      (response/response {:data (content-item->response content-item)})
      (response/not-found nil))))

(defn update-content-item-handler
  [{:keys [model path-params body-params current-user] :as _req}]
  (let [content-item-id (parse-uuid (:content-item-id path-params))
        [content-item] (rpub/get-content-items
                         model
                         {:content-item-id [content-item-id]})
        document (-> (:data body-params)
                     (update-keys #(parse-uuid (name %))))
        content-item' (-> content-item
                          (merge {:document document
                                  :updated-by (:id current-user)
                                  :updated-at (Instant/now)})
                          (select-keys [:id :document :created-at
                                        :created-by :updated-at
                                        :updated-by]))]
    (rpub/update-content-item! model content-item')
    (response/response {:data (content-item->response content-item')})))

(defn delete-content-item-handler [{:keys [model path-params] :as _req}]
  (let [content-item-id (parse-uuid (:content-item-id path-params))
        [content-item] (rpub/get-content-items
                         model
                         {:content-item-ids [content-item-id]})]
    (if content-item
      (do
        (rpub/delete-content-item! model content-item)
        {:status 204, :headers {}, :body nil})
      (response/not-found nil))))

(defn routes [opts]
  ["" {:middleware (api/api-middleware opts)}
   ["/api/rest/{content-type-slug}"
    {:get #'list-content-items-handler
     :post #'create-content-item-handler}]
   ["/api/rest/{content-type-slug}/{content-item-id}"
    {:get #'get-content-item-handler
     :put #'update-content-item-handler
     :delete #'delete-content-item-handler}]])

(defmethod rpub/plugin ::plugin [_]
  {:label "REST API"
   :description "Add endpoints for a REST API. Works as a drop-in replacement for the Strapi v5 REST API."
   :min-rpub-version "0.2.0"
   :routes routes})
