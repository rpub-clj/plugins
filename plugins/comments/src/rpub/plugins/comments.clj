(ns rpub.plugins.comments
  (:require [rpub.model :as model]))

(defn plugin [_]
  {:name "Comments"
   :description "Allow readers to add comments to posts."
   :routes
   (fn [_]
     [["/admin/comments"
       (fn [_]
         {:status 200
          :body {:message "Hello world!"}})]])})

(model/add-plugin plugin)
