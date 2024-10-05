(ns rpub.plugins.seo
  (:require [hiccup2.core :as hiccup]
            [rpub.admin :as admin]
            [rpub.app :as app]
            [rpub.model :as model])
  (:import (java.time ZoneOffset ZonedDateTime)
           (java.time.format DateTimeFormatter)))

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

(defn meta-tags-page [req]
  (admin/page-handler
    req
    {:title "Search Engine Optimization (SEO)"
     :primary
     (fn [{:keys [::model]}]
       (for [meta-tag (get-meta-tags model {})]
         [:pre (pr-str meta-tag)]))}))

(defn- w3c-datetime [instant]
  (let [zoned-time (ZonedDateTime/ofInstant instant ZoneOffset/UTC)
        formatter  (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ssXXX")]
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

(defn sitemap-xml [{:keys [model port] :as _req}]
  (let [posts (model/get-posts model {})
        [setting] (model/get-settings model {:keys [:site-base-url]})
        site-base-url (app/->site-base-url setting port)
        urls (map (fn [{:keys [slug updated-at created-at]}]
                    {:loc (str site-base-url "/posts/" slug)
                     :lastmod (or updated-at created-at)
                     :changefreq :monthly
                     :priority 0.6})
                  posts)]
    (sitemap-response {:urls urls})))

(defn routes [opts]
  [["/sitemap.xml" {:get sitemap-xml
                    :conflicting true}]
   ["/admin/seo" {:get meta-tags-page
                  :middleware (admin/admin-middleware opts)}]])

(defn plugin [_]
  {:name "Search Engine Optimization (SEO)"
   :description "Adds meta tags and site maps."
   :schema (fn [opts] (schema (->model opts)))
   :middleware [wrap-seo]
   :routes routes})

(model/add-plugin ::plugin plugin)
