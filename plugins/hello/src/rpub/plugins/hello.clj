(ns rpub.plugins.hello
  (:require [rpub.model :as model]))

(defn plugin [_]
  {:name "Hello World"
   :description "A minimal example plugin."
   :routes
   (fn [_]
     [["/admin/hello"
       (fn [_]
         {:status 200
          :body {:message "Hello world!"}})]])})

(model/add-plugin ::plugin plugin)
