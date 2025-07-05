; Copyright © Radford Smith. All rights reserved.
;
; The use and distribution terms for this software are covered by the
; MIT License (https://opensource.org/license/mit).
;
; WordPress® is a registered trademark of the WordPress Foundation. rPub is not
; affiliated with or endorsed by the WordPress Foundation or WordPress.org. This
; plugin provides compatibility with WordPress APIs for integration purposes.
;
; You must not remove this notice, or any other, from this software.

(ns rpub.plugins.external-editing
  (:require [clojure.data.xml :as xml]
            [clojure.data.zip.xml :as zip-xml]
            [clojure.java.io :as io]
            [clojure.zip :as zip]
            [hiccup2.core :as hiccup]
            [rads.inflections :as inflections]
            [ring.util.response :as response]
            [rpub.core :as rpub]
            [rpub.model :as model]
            [rpub.plugins.admin.helpers :as helpers]
            [rpub.plugins.app :as app]
            [rpub.plugins.content-types :as content-types])
  (:import (java.time Instant ZoneId ZonedDateTime)
           (java.time.format DateTimeFormatter)))

(defn xml-response [& args]
  (let [[opts body] (if (map? (first args))
                      [(first args) (rest args)]
                      [nil args])
        {:keys [content-type]
         :or {content-type "text/xml; charset=UTF-8"}} opts]
    {:status 200
     :headers {"Content-Type" content-type}
     :body (str
             (hiccup/html
               {:mode :xml}
               (hiccup/raw "<?xml version=\"1.0\" encoding=\"utf-8\"?>")
               body))}))

(defn parse-xml [body]
  (xml/parse (io/reader body)))

(defn parse-datetime [datetime-str]
  (let [formatter (DateTimeFormatter/ofPattern "yyyyMMdd'T'HH:mm:ss")]
    (.toInstant (ZonedDateTime/parse datetime-str formatter))))

(defn param->clj [{:keys [tag content] :as loc}]
  (case tag
    :string (first content)
    :base64 (first content)
    :dateTime.iso8601 (parse-datetime (first content))
    :int (Integer/parseInt (first content))
    :struct (->> (zip-xml/xml-> (zip/xml-zip loc) :struct :member)
                 (map (fn [member]
                        [(-> (zip-xml/xml1-> member :name zip/node)
                             :content first)
                         (param->clj (-> (zip-xml/xml1-> member :value zip/node)
                                         :content first))]))
                 (into {}))
    :array (->> (zip-xml/xml-> (zip/xml-zip loc) :array :data :value)
                (map #(-> % zip/node :content first param->clj)))))

(defn ->method-call [xml-root]
  (let [loc (zip/xml-zip xml-root)
        method-name (-> (zip-xml/xml1-> loc :methodCall :methodName zip/node)
                        :content first)
        method-params (->> (zip-xml/xml-> loc :methodCall :params :param :value)
                           (map #(-> % zip/node :content first param->clj)))]
    (merge
      {:method-name method-name}
      (case method-name
        "wp.getPosts"
        (let [[_blog-id username password constraints] method-params]
          {:username username
           :password password
           :constraints (update-keys constraints #(keyword (inflections/hyphenate %)))})

        "wp.getPost"
        (let [[_blog-id username password post-id _extra-field-names] method-params]
          {:username username
           :password password
           :post-id (parse-uuid post-id)})

        "wp.newPost"
        (let [[_blog-id username password post-fields] method-params]
          {:username username
           :password password
           :post-fields (update-keys post-fields #(keyword (inflections/hyphenate %)))})

        "wp.editPost"
        (let [[_blog-id username password post-id post-fields] method-params]
          {:username username
           :password password
           :post-id (parse-uuid post-id)
           :post-fields (update-keys post-fields #(keyword (inflections/hyphenate %)))})

        "wp.deletePost"
        (let [[_blog-id username password post-id] method-params]
          {:username username
           :password password
           :post-id (parse-uuid post-id)})

        (let [[_blog-id username password] method-params]
          {:username username
           :password password})))))

(defn format-datetime [instant]
  (let [zoned-date-time (ZonedDateTime/ofInstant instant (ZoneId/of "UTC"))
        formatter (DateTimeFormatter/ofPattern "yyyyMMdd'T'HH:mm:ss")]
    (.format formatter zoned-date-time)))

(defn xml-value [v]
  (cond
    (string? v) [:string v]
    (keyword? v) [:string (name v)]
    (integer? v) [:integer v]
    (uuid? v) [:string (str v)]
    (boolean? v) [:boolean (if v "1" "0")]
    (inst? v) (hiccup/raw
                (format "<dateTime.iso8601>%s</dateTime.iso8601>"
                        (format-datetime v)))
    (map? v) [:struct
              (for [[k v] v]
                [:member
                 [:name (inflections/underscore (name k))]
                 [:value (xml-value v)]])]
    (seqable? v) [:array
                  [:data
                   (for [item v]
                     [:value (xml-value item)])]]
    :else (throw (ex-info "Unexpected type" {:value v}))))

(defn content-item->post
  [{:keys [id content-type fields created-at updated-at] :as ci}
   req]
  {:post-id id
   :post-title (get fields "Title" "")
   :post-date created-at
   :post-date-gmt created-at
   :post-modified (or updated-at created-at)
   :post-modified-gmt (or updated-at created-at)
   :post-status "published"
   :post-type (-> content-type :slug inflections/singular)
   :post-name (get fields "Slug")
   :post-author ""
   :post-password ""
   :post-excerpt ""
   :post-content (or (get fields "Content") "")
   :post-parent 0
   :post-mime-type ""
   :link (app/url-for ci req)
   :guid (app/url-for ci req)
   :menu-order 0
   :comment-status "closed"
   :ping-status "open"
   :sticky false
   :post-thumbnail []
   :post-format "standard"
   :terms []
   :custom-fields []
   :enclosure []})

(defn get-post-response
  [{:keys [model] :as req}
   {:keys [post-id] :as _method-call}]
  (let [content-items (rpub/get-content-items
                        model
                        {:content-item-ids [post-id]})
        [post] (map #(content-item->post % req) content-items)]
    [:methodResponse
     [:params
      [:param
       [:value
        (xml-value post)]]]]))

(defn get-posts-response [{:keys [model] :as req}
                          {:keys [constraints] :as _method-call}]
  (let [content-type-slug (-> constraints :post-type inflections/plural keyword)
        content-items (rpub/get-content-items
                        model
                        {:content-type-slugs [content-type-slug]})
        posts (map #(content-item->post % req) content-items)]
    [:methodResponse
     [:params
      [:param
       [:value
        (xml-value posts)]]]]))

(defn empty-response []
  [:methodResponse [:params [:param [:value [:array [:data]]]]]])

(defn new-post-response [{:keys [model current-user] :as req} method-call]
  (let [{:keys [post-fields]} method-call
        post-fields' (update post-fields :post-type
                             (fn [post-type]
                               (-> (or post-type "post")
                                   inflections/plural
                                   keyword)))
        [content-type] (rpub/get-content-types
                         model
                         {:content-type-slugs [(:post-type post-fields')]})
        slug (or (:post-name post-fields')
                 (model/->slug (:post-title post-fields')))
        new-content-item (-> {:id (random-uuid)
                              :content-type content-type
                              :created-at (Instant/now)
                              :created-by (:id current-user)}
                             (assoc-in [:document content-types/slug-field-id]
                                       slug)
                             (assoc-in [:document content-types/content-field-id]
                                       (:post-content post-fields'))
                             (assoc-in [:document content-types/title-field-id]
                                       (:post-title post-fields'))
                             (select-keys [:id :content-type :document
                                           :created-at :created-by]))]
    (content-types/create-content-item!
      (::content-types/model req)
      new-content-item)
    [:methodResponse
     [:params
      [:param
       [:value
        (xml-value (:id new-content-item))]]]]))

(defn edit-post-response [{:keys [model current-user] :as req} method-call]
  (let [{:keys [post-id post-fields]} method-call
        [existing-content-item] (rpub/get-content-items
                                  model
                                  {:content-item-ids [post-id]})
        slug (or (:post-name post-fields)
                 (model/->slug (:post-title post-fields)))
        updated-content-item (-> existing-content-item
                                 (assoc-in [:document content-types/slug-field-id]
                                           slug)
                                 (assoc-in [:document content-types/content-field-id]
                                           (:post-content post-fields))
                                 (assoc-in [:document content-types/title-field-id]
                                           (:post-title post-fields))
                                 (merge {:updated-at (Instant/now)
                                         :updated-by (:id current-user)})
                                 (select-keys [:id :document :created-at
                                               :created-by :updated-at
                                               :updated-by]))]
    (content-types/update-content-item!
      (::content-types/model req)
      updated-content-item)
    [:methodResponse
     [:params
      [:param
       [:value
        [:boolean "1"]]]]]))

(defn delete-post-response [{:keys [model] :as req} method-call]
  (let [{:keys [post-id]} method-call
        [existing-content-item] (rpub/get-content-items
                                  model
                                  {:content-item-ids [post-id]})]
    (content-types/delete-content-item!
      (::content-types/model req)
      existing-content-item)
    [:methodResponse
     [:params
      [:param
       [:value
        [:boolean "1"]]]]]))

(defn method-response [req {:keys [method-name] :as method-call}]
  (xml-response
    (case method-name
      "wp.getPosts" (get-posts-response req method-call)
      "wp.getPost" (get-post-response req method-call)
      "wp.newPost" (new-post-response req method-call)
      "wp.editPost" (edit-post-response req method-call)
      "wp.deletePost" (delete-post-response req method-call)
      (empty-response))))

(defn authorize [{:keys [username password] :as _method-call} model]
  (let [[found-user] (model/get-users model {:usernames [username]
                                             :password true})]
    (when (and found-user (model/verify-password found-user password))
      found-user)))

(defn rsd-response [{:keys [site-base-url]}]
  (let [api-link (str site-base-url "/admin/xmlrpc")]
    (xml-response
      {:content-type "application/rsd+xml"}
      [:rsd {:version "1.0" :xmlns "http://archipelago.phrasewise.com/rsd"}
       [:service
        [:engineName "rPub"]
        [:engineLink "https://rpub.dev"]
        [:homePageLink]
        [:apis
         [:api {:name "WordPress"
                :blogID "1"
                :preferred "true"
                :apiLink api-link}]]]])))

(defn wrap-rsd-link-tag [handler]
  (fn [{:keys [site-base-url] :as req}]
    (let [link-tag [:link {:rel "EditURI"
                           :type "application/rsd+xml"
                           :title "RSD"
                           :href (str site-base-url "/admin/xmlrpc?rsd")}]
          req' (update req :head conj link-tag)]
      (handler req'))))

(defn xmlrpc-get [{:keys [query-string] :as req}]
  (if (= query-string "rsd")
    (rsd-response req)
    (response/bad-request "Bad Request")))

(defn forbidden-response []
  (xml-response
    [:methodResponse
     [:fault
      [:value
       [:struct
        [:member
         [:name "faultCode"]
         [:value
          [:int "403"]]]
        [:member
         [:name "faultString"]
         [:value
          [:string "Incorrect username or password."]]]]]]]))

(defn xmlrpc-post [{:keys [model body] :as req}]
  (let [method-call (->method-call (parse-xml body))]
    (if-let [current-user (authorize method-call model)]
      (let [req' (assoc req :current-user current-user)]
        (method-response req' method-call))
      (forbidden-response))))

(defn routes [opts]
  (let [opts' (assoc opts :auth-required false)]
    [["/admin/xmlrpc" {:get xmlrpc-get
                       :post xmlrpc-post
                       :middleware (helpers/admin-middleware opts')}]]))

(defn middleware [_]
  [wrap-rsd-link-tag])

(defmethod rpub/plugin ::plugin [_]
  {:label "External Editing"
   :description "Add support to view and edit posts from external editors (e.g. MarsEdit)."
   :middleware middleware
   :routes routes})
