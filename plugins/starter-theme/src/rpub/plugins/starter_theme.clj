(ns rpub.plugins.starter-theme
  (:require [hiccup2.core :as hiccup]
            [markdown.core :as markdown]
            [rpub.core :as rpub]
            [rpub.lib.html :as html]
            [rpub.model :as model]
            [rpub.plugins.admin.helpers :as helpers])
  (:import (java.time ZonedDateTime ZoneId)
           (java.time.format DateTimeFormatter)))

(defn md->html [md]
  (markdown/md-to-html-string md))

(defn format-date [instant]
  (let [formatter (DateTimeFormatter/ofPattern "MMM dd, yyyy")
        zdt (ZonedDateTime/ofInstant instant (ZoneId/systemDefault))]
    (.format formatter zdt)))

(defn header [{:keys [site-title] :as _page}]
  [:div.mb-8.py-3.border-b.border-gray-200.shrink-0.flex.items-center.justify-between
   [:div.w-auto
    [:h1.text-2xl
     [:a {:class "visited:text-theme-body-color" :href "/"}
      site-title]]]
   [:div
    [:a {:href "/pages/about"}
     "About"]]])

(defn footer
  [{:keys [site-title site-description site-subtitle contact-email footer-links]
    :as _page}]
  [:div.py-8.border-t.border-gray-200.shrink-0
   [:div.text-xl.mb-4 site-title]
   [:div.flex.flex-wrap.text-sm
    {:class "basis-1/2 md:flex-nowrap"}
    [:div {:class "w-1/2 md:w-auto md:min-w-[264px]"}
     [:div.text-gray-500
      site-subtitle]
     [:div
      [:a {:href "#"}
       contact-email]]]
    [:div {:class "w-1/2 md:w-auto md:min-w-[151px]"}
     [:ul.text-gray-500 {:class "list-[disc]"}
      (for [link footer-links]
        [:li
         [:a {:href (:url link)}
          (:title link)]])]]
    [:div.text-gray-500.mt-8
     {:class "md:mt-0"}
     site-description]]])

(defn layout [{:keys [page content]}]
  [:div.px-4
   [:div#app.mx-auto.min-h-screen.justify-center.flex.flex-col
    {:class "max-w-[740px]"}
    (header page)
    [:div.grow.shrink-0 content]
    (footer page)]])

(defn post-page [{:keys [page post] :as _req}]
  (let [content-type (:content-type post)]
    (layout
      {:page page
       :content
       [:div
        (case (:slug content-type)
          :posts
          [:div
           [:h2.text-4xl.mb-6 (get-in post [:fields "Title"])]
           [:div.text-gray-500.text-xsm
            (format-date (get post :created-at))]
           [:div.prose.prose-theme.mt-8.mb-12
            (hiccup/raw (md->html (get-in post [:fields "Content"])))]]

          :pages
          [:div
           [:h2.text-4xl.mb-6 (get-in post [:fields "Title"])]
           [:div.prose.prose-theme.mt-8.mb-12
            (hiccup/raw (md->html (get-in post [:fields "Content"])))]])]})))

(defn index-page [{:keys [page model] :as req}]
  (let [content-items (rpub/get-content-items
                        model
                        {:content-type-slugs [:posts]})
        {:keys [posts]} (->> content-items
                             (group-by #(get-in % [:content-type :slug])))]
    (layout
      {:page page
       :content
       [:div
        [:div
         [:h2.text-3xl.mb-6 "Posts"]
         (for [post (sort-by :created-at #(compare %2 %1) posts)]
           [:div.mb-6
            [:div.text-gray-500.text-xsm
             (format-date (get post :created-at))]
            [:h3.text-2xl
             [:a {:href (rpub/url-for post req)}
              (get-in post [:fields "Title"])]]])]
        [:div.my-8
         "subscribe " [:a {:href "/feeds/main"} "via RSS"]]]})))

(def theme
  {:label "Starter"
   :description "A theme to get started."
   :post-page #'post-page
   :index-page #'index-page})

(defn theme-active? [{:keys [uri] :as req}]
  (and (= (:label (model/active-theme req)) (:label theme))
       (not (helpers/admin-path? uri))))

(defn wrap-theme [handler]
  (fn [req]
    (let [req' (update req :themes conj theme)]
      (if-not (theme-active? req')
        (handler req')
        (let [main-css (html/stylesheet-tag "starter-theme/main.css")
              req'' (update req' :head conj main-css)]
          (handler req''))))))

(defmethod rpub/plugin ::plugin [_]
  {:label "Starter Theme"
   :description "A theme to get started."
   :middleware (fn [_] [wrap-theme])})
