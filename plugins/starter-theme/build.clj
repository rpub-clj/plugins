(ns build
  (:refer-clojure :exclude [compile])
  (:require [clojure.tools.build.api :as b]))

(defn compile [_]
  (b/delete {:path "target"})
  (b/delete {:path "resources/public/css"})
  (b/process {:command-args ["npm" "ci"]})
  (b/process {:env {"NODE_ENV" "production"}
              :command-args ["./node_modules/.bin/tailwindcss"
                             "--minify"
                             "--postcss" "resources/css/postcss.config.js"
                             "--config" "resources/css/tailwind.config.js"
                             "--input" "resources/css/tailwind.css"
                             "--output" "resources/public/css/starter-theme/main.css"]}))
