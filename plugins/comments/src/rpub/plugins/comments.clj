(ns rpub.plugins.comments
  (:require [rpub.model :as model]))

(defn plugin [_]
  {:id #uuid"90d70988-3f65-473e-956e-638075471d7b"
   :name "Comments"
   :description "Allow readers to add comments to posts."
   :routes
   (fn [_]
     [["/admin/comments"
       (fn [_]
         {:status 200
          :body {:message "Hello world!"}})]])})

(model/add-plugin ::plugin plugin)
