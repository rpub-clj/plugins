(ns ^:clj-reload/no-unload rpub.plugins.seo
  (:require [hiccup2.core :as hiccup]
            [rpub.admin :as admin]
            [rpub.app :as app]
            [rpub.model :as model]
            [rpub.plugins.content-types :as content-types])
  (:import (java.time ZoneOffset ZonedDateTime)
           (java.time.format DateTimeFormatter)))

^:clj-reload/keep
(defprotocol Model
  (schema [model])
  (get-meta-tags [model opts]))

(defn add-meta-tags [head meta-tags]
  (let [elements (map (fn [attrs]
                        [:meta (select-keys attrs [:name :content :property])])
                      meta-tags)]
    (into head elements)))

(defmulti ->model :db-type)

(defn wrap-seo [handler]
  (fn [{:keys [head db-type] :as req}]
    (let [ds (get-in req [:model :ds])
          model (->model {:db-type db-type :ds ds})
          meta-tags (get-meta-tags model {})
          head' (add-meta-tags head meta-tags)
          req' (merge req {::model model :head head'})]
      (handler req'))))

(defn meta-tags-page [{:keys [::model] :as req}]
  (admin/page-response
    req
    {:title "SEO"
     :primary
     (for [meta-tag (get-meta-tags model {})]
       [:pre (pr-str meta-tag)])}))

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
                           (sort #(compare %2 %1))
                           first)
        index-url (->sitemap-url
                    {:loc site-base-url
                     :lastmod index-lastmod})
        content-item-urls (->> content-items
                               (map (fn [ci]
                                      (->sitemap-url
                                        {:loc (app/link-to ci req)
                                         :lastmod (sitemap-lastmod ci)}))))]
    (concat [index-url] content-item-urls)))

(defn sitemap-xml [{:keys [model port] :as req}]
  (let [content-items (content-types/get-content-items
                        (::content-types/model req)
                        {:content-type-slugs [:posts]})
        [setting] (model/get-settings model {:keys [:site-base-url]})
        site-base-url (app/->site-base-url setting port)
        req' (assoc req :site-base-url site-base-url)
        urls (sitemap-urls req' content-items)]
    (sitemap-response {:urls urls})))

(defn routes [opts]
  [["/sitemap.xml" {:get sitemap-xml
                    :middleware (app/app-middleware opts)}]
   ["/admin/seo" {:get meta-tags-page
                  :middleware (admin/admin-middleware opts)}]])

(defn menu-items [_]
  {:plugins [{:name "SEO"
              :href "/admin/seo"}]})

(defn plugin [_]
  {:name "Search Engine Optimization (SEO)"
   :description "Adds meta tags and site maps."
   :schema (fn [opts] (schema (->model opts)))
   :menu-items menu-items
   :middleware [wrap-seo]
   :routes routes})

(model/add-plugin ::plugin plugin)
