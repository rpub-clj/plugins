(ns rpub.plugins.gallery
  (:require [rpub.model :as model]))

(def block
  {:id #uuid"d77fa747-6c03-4ba8-a668-2c6a500a6b68"
   :alias :gallery
   :name "Gallery"
   :content (fn [{:keys [photos]}]
              [:div
               (for [{:keys [src alt]} photos]
                 [:img {:src src :alt alt}])])})

(defn plugin [_]
  {:name "Gallery Block"
   :description "A block for photo galleries."
   :blocks [block]})

(model/add-plugin ::plugin plugin)
