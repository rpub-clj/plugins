(ns rpub.plugins.comments)

(defn plugin [_]
  {:name "Comments"
   :description "Allow readers to add comments to posts."
   :routes
   (fn [_]
     [["/admin/comments"
       (fn [_]
         {:status 200
          :body {:message "Hello world!"}})]])})
