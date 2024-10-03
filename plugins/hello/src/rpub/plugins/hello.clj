(ns rpub.plugins.hello)

(defn plugin [_]
  {:name "Hello World"
   :description "A minimal example plugin."
   :routes
   (fn [_]
     [["/admin/hello"
       (fn [_]
         {:status 200
          :body {:message "Hello world!"}})]])})
