(ns build
  (:refer-clojure :exclude [compile])
  (:require [clojure.tools.build.api :as b]))

(defn compile [_]
  (b/delete {:path "target"})
  (b/delete {:path "resources/public"})
  (b/process {:command-args ["npm" "ci"]})
  (b/process {:command-args ["./node_modules/.bin/cherry"
                             "compile" "src/rpub/plugins/seo.cljs"
                             "--output-dir" "target/cherry"]})
  (b/process {:command-args ["./node_modules/.bin/esbuild"
                             "target/cherry/src/rpub/plugins/seo.mjs"
                             "--format=esm"
                             "--bundle"
                             "--minify"
                             "--external:rpub.*"
                             "--external:react"
                             "--external:preact"
                             "--external:cherry-cljs"
                             "--outfile=resources/public/js/rpub/plugins/seo.js"
                             "--loader:.jsx=jsx"]}))
