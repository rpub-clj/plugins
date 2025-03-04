(ns rpub.plugins.seo
  (:require ["react" :refer [useCallback useState]]
            [rpub.admin.impl :as admin-impl]
            [rpub.lib.html :as html]
            [rpub.lib.http :as http]
            [rpub.lib.reagent :as r]))

(defn- index-by [f coll]
  (->> coll
       (map (fn [v] [(f v) v]))
       (into {})))

(defn seo-page [{:keys [meta-tags anti-forgery-token]}]
  (let [http-opts {:anti-forgery-token anti-forgery-token}
        [state set-state] (useState {:meta-tag-index (index-by :id meta-tags)})
        {:keys [meta-tag-index]} state
        update-meta-tag (useCallback
                          (fn [meta-tag-id]
                            (html/debounce
                              (fn [e]
                                (let [value (-> e .-target .-value)
                                      meta-tag (get meta-tag-index meta-tag-id)
                                      body (assoc meta-tag :content value)
                                      http-opts' (assoc http-opts :body body)]
                                  (http/post "/api/seo/update-meta-tag" http-opts')
                                  (set-state #(assoc-in % [:meta-tag-index meta-tag-id :content] value))))
                              html/default-debounce-timeout-ms)))]
    [:div
     [:div {:class "p-4"}
      [admin-impl/box
       {:title "SEO"}]]
     [:div {:class "p-4 pt-0"}
      [admin-impl/box
       {:title [:span {:class "text-2xl"} "Meta Tags"]
        :content
        (if-not (seq meta-tag-index)
          [:div "No meta tags."]
          [:div {:class "grid gap-4 sm:grid-cols-2 sm:gap-6 max-w-2xl"}
           (for [meta-tag (vals meta-tag-index)]
             [:div {:class "sm:col-span-2" :key (:id meta-tag)}
              [:label {:class "block mb-2 text-sm font-semibold text-gray-900 dark:text-white" :for "name"}
               (:name meta-tag)]
              [html/input
               {:name :meta-tag-value
                :type :text
                :default-value (or (:content meta-tag) (:property meta-tag))
                :on-change (update-meta-tag (:id meta-tag))}]])])}]]]))

(html/add-element :seo-page (r/reactify-component seo-page))
