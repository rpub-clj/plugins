(ns rpub.plugins.seo
  (:require [hiccup2.core :as hiccup]
            [ring.util.response :as response]
            [rpub.core :as rpub]
            [rpub.lib.html :as html]
            [rpub.model :as model]
            [rpub.plugins.admin.helpers :as admin-helpers]
            [rpub.plugins.api :as api]
            [rpub.plugins.app.helpers :as app-helpers])
  (:import (java.time Instant ZoneOffset ZonedDateTime)
           (java.time.format DateTimeFormatter)))

^:clj-reload/keep
(defprotocol Model
  (migrate! [model])
  (get-meta-tags [model opts])
  (update-meta-tag! [model opts]))

(defn add-meta-tags [head meta-tags]
  (let [elements (map (fn [attrs]
                        [:meta (select-keys attrs [:name :content :property])])
                      meta-tags)]
    (into head elements)))

(defmulti ->model :db-type)

(def seo-script "import '/js/rpub/plugins/seo.js';")

(def admin-menu-items
  {:plugins [{:label "SEO"
              :href "/admin/seo"}]})

(defn wrap-seo [handler]
  (fn [{:keys [head inline-scripts] :as req}]
    (let [model (->model (model/db-info (:model req)))
          meta-tags (get-meta-tags model {})
          admin-menu-items' (conj (:admin-menu-items req) admin-menu-items)
          inline-scripts' (conj inline-scripts seo-script)
          head' (add-meta-tags head meta-tags)
          req' (merge req {::model model
                           :admin-menu-items admin-menu-items'
                           :head head'
                           :inline-scripts inline-scripts'})]
      (handler req'))))

(defn seo-page [{:keys [::model] :as req}]
  (let [meta-tags (get-meta-tags model {})]
    (admin-helpers/page-response
      req
      {:title "SEO"
       :primary
       [:div
        [:script {:type :module} (hiccup/raw seo-script)]
        (html/cljs
          [:seo-page {:meta-tags meta-tags}]
          {:format :json})]})))

(defn- w3c-datetime [instant]
  (let [zoned-time (ZonedDateTime/ofInstant instant ZoneOffset/UTC)
        formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ssXXX")]
    (.format formatter zoned-time)))

(defn sitemap-response [{:keys [urls]}]
  {:status 200
   :headers {"Content-Type" "application/xml"}
   :body (str
           (hiccup/html
             {:mode :xml}
             (hiccup/raw "<?xml version=\"1.0\" encoding=\"utf-8\"?>")
             [:urlset {:xmlns "http://www.sitemaps.org/schemas/sitemap/0.9"}
              (for [{:keys [loc lastmod changefreq priority]} urls]
                [:url
                 [:loc loc]
                 [:lastmod (w3c-datetime lastmod)]
                 [:changefreq (name changefreq)]
                 [:priority (str priority)]])]))})

(defn sitemap-lastmod [{:keys [updated-at created-at]}]
  (or updated-at created-at))

(defn ->sitemap-url [{:keys [loc lastmod] :as _opts}]
  {:loc loc
   :lastmod lastmod
   :changefreq :monthly
   :priority 0.6})

(defn sitemap-urls [{:keys [site-base-url] :as req} content-items]
  (let [index-lastmod (->> (map sitemap-lastmod content-items)
                           (sort >)
                           first)
        index-url (->sitemap-url
                    {:loc site-base-url
                     :lastmod (or index-lastmod (Instant/ofEpochMilli 0))})
        content-item-urls (->> content-items
                               (map (fn [ci]
                                      (->sitemap-url
                                        {:loc (app-helpers/url-for ci req)
                                         :lastmod (sitemap-lastmod ci)}))))]
    (concat [index-url] content-item-urls)))

(defn sitemap-xml [{:keys [model] :as req}]
  (let [default-content-type-slug (keyword (get-in req [:settings :default-content-type-slug :value]))
        content-items (rpub/get-content-items
                        model
                        {:content-type-slugs [default-content-type-slug]})
        urls (sitemap-urls req content-items)]
    (sitemap-response {:urls urls})))

(defn update-meta-tag [{:keys [::model current-user body-params] :as _req}]
  (let [{:keys [id content]} body-params
        [meta-tag] (get-meta-tags model {:ids [id]})
        meta-tag' (-> meta-tag
                      (merge {:content content
                              :updated-by (:id current-user)
                              :updated-at (Instant/now)}))]
    (update-meta-tag! model meta-tag')
    (response/response {:success true})))

(defn routes [opts]
  [["/sitemap.xml" {:get sitemap-xml
                    :middleware (app-helpers/app-middleware opts)}]
   ["/api/seo" {:middleware (api/api-middleware opts)}
    ["/update-meta-tag" {:post update-meta-tag}]]
   ["/admin/seo" {:get seo-page
                  :middleware (admin-helpers/admin-middleware opts)}]])

(defn init [{:keys [model current-user] :as _opts}]
  (let [db-info (model/db-info model)
        _ (case (:db-type db-info)
            :sqlite (require 'rpub.plugins.seo.sqlite))
        model (->model (merge db-info {:current-user current-user}))]
    (migrate! model)))

(defn middleware [_]
  [wrap-seo])

(defmethod rpub/plugin ::plugin [_]
  {:label "SEO"
   :description "Add meta tags and site maps."
   :init init
   :middleware middleware
   :routes routes})
