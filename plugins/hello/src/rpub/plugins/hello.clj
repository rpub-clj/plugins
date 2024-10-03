(ns rpub.plugins.hello
  (:require [rpub.model :as model]))

(defn plugin [_]
  {:id #uuid"019253a2-6a70-75dd-be77-584c658f7134"
   :name "Hello World"
   :description "A minimal example plugin."
   :routes
   (fn [_]
     [["/admin/hello"
       (fn [_]
         {:status 200
          :body {:message "Hello world!"}})]])})

(model/add-plugin plugin)
