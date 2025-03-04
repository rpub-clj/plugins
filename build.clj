(ns build
  (:require [babashka.fs :as fs]
            [babashka.json :as json]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [edamame.core :as e]))

(def index-file "target/public/index.json")

(defn- resolve-key [path unresolved-key]
  (let [suffix (-> (fs/file-name path)
                   (str/replace #"\.clj$" "")
                   (str/replace "_" "-"))]
    (keyword (str "rpub.plugins." suffix) (name unresolved-key))))

(defn- get-plugins []
  (->> (fs/glob "plugins" "*/src/rpub/plugins/*.clj")
       (map str)
       (mapcat (fn [path]
                 (let [forms (e/parse-string-all (slurp path) {:all true :auto-resolve name})]
                   (map (fn [form] [path form]) forms))))
       (filter (fn [[_ form]] (and (= (first form) 'defmethod)
                                   (= (second form) 'rpub/plugin))))
       (map (fn [[path form]]
              (let [unresolved-key (nth form 2)
                    resolved-key (resolve-key path unresolved-key)
                    plugin (nth form 4)]
                (-> plugin
                    (assoc :key resolved-key)
                    (select-keys [:key :label :description])))))))

(defn get-current-sha []
  (let [result (shell/sh "git" "rev-parse" "HEAD")]
    (if (= 0 (:exit result))
      (str/trim (:out result))
      (throw (Exception. (str "Git command failed: " (:err result)))))))

(def redirect-html
  (str/trim "
<!DOCTYPE html>
<html lang=\"en\">
<head>
  <meta charset=\"UTF-8\">
  <title>Redirecting...</title>
  <meta http-equiv=\"refresh\" content=\"0; url=https://github.com/rpub-clj/plugins\">
</head>
<body>
  <p>If you are not redirected automatically, <a href=\"https://github.com/rpub-clj/plugins\">click here</a>.</p>
</body>
</html>
"))

(defn- index->json [index]
  (-> index
      (update :plugins
              (fn [plugins]
                (map #(update % :key str) plugins)))))

(defn generate-index [_]
  (let [index {:sha (get-current-sha)
               :plugins (get-plugins)}]
    (fs/create-dirs (fs/parent index-file))
    (spit index-file (json/write-str (index->json index)))
    (spit (fs/file (fs/parent index-file) "index.html") redirect-html)))

(defn all [opts]
  (-> opts
      generate-index))

(comment
  (generate-index nil))
