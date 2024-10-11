(ns rpub.plugins.solarized-theme
  (:require [hiccup2.core :as hiccup]
            [rpub.app :as app]
            [rpub.model :as model]))

(defn layout [{:keys [page content]}]
  [:div.mx-auto.max-w-4xl.p-8.bg-blue-500
   [:h1.text-4xl.font-semibold.text-center (:title page)]
   [:div content]])

(defn page-page [{:keys [page]}]
  (layout
    {:page page
     :content
     [:div.mb-8
      (hiccup/raw (:content page))]}))

(defn post-page [{:keys [page post]}]
  (layout
    {:page page
     :content
     [:div.mb-8
      (hiccup/raw (:content post))]}))

(defn index-page [{:keys [page posts]}]
  (layout
    {:page page
     :content
     (for [post posts]
       [:div.mb-8
        [:h3.text-2xl.font-semibold
         [:a {:href (app/link-to post)}
          (:title post)]]
        [:div
         (hiccup/raw (:content post))]])}))

(def description
  "A theme based on the Solarized color scheme.")

(defn theme [_]
  {:name "Solarized"
   :description description
   :page-page page-page
   :post-page post-page
   :index-page index-page})

(defn plugin [_]
  {:id #uuid"57c300e5-96fc-4635-8085-cc0d7dd8f51f"
   :name "Solarized Theme"
   :description description
   :themes [theme]})

(model/add-plugin ::plugin plugin)
