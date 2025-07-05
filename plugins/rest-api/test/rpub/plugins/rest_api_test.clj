(ns rpub.plugins.rest-api-test
  (:require [babashka.fs :as fs]
            [buddy.core.nonce :as nonce]
            [clojure.test :refer [deftest is]]
            [matcher-combinators.test :refer [match?]]
            [reitit.ring :as reitit-ring]
            [ring.mock.request :as mock]
            [rpub.lib.db :as db]
            [rpub.model.content-types :as ct-model]
            [rpub.plugins.content-types.sqlite]
            [rpub.plugins.rest-api :as rest-api]))

(defn test-handler []
  (reitit-ring/ring-handler
    (reitit-ring/router
      (rest-api/routes {:session-store-key (nonce/random-bytes 16)
                        :auth-required false}))))

(defn test-ds []
  (let [temp-dir (-> (fs/create-temp-dir) fs/delete-on-exit)]
    (db/get-datasource (format "jdbc:sqlite:%s/test.db" temp-dir))))

(def test-document
  {ct-model/slug-field-id "test-title-2"
   ct-model/content-field-id "<p>test content 2</p>"
   ct-model/title-field-id "test title 2"})

(deftest create-test
  (let [handler (test-handler)
        ds (test-ds)
        current-user {:id (random-uuid)}
        content-types-model (ct-model/->model {:db-type :sqlite
                                               :ds ds
                                               :current-user current-user})
        _ (ct-model/migrate! content-types-model)
        body-params {:data (update-keys test-document #(keyword (str %)))}
        create-req (-> (mock/request :post "/api/rest/posts")
                       (merge {:model {:content-types-model content-types-model}
                               :body-params body-params
                               :current-user current-user}))
        create-res (handler create-req)]
    (is (match? {:status 201
                 :body {:data (merge {:id uuid?, :created-at inst?}
                                     test-document)}}
                create-res))))

(deftest delete-existing-test
  (let [handler (test-handler)
        ds (test-ds)
        current-user {:id (random-uuid)}
        content-types-model (ct-model/->model {:db-type :sqlite
                                               :ds ds
                                               :current-user current-user})
        _ (ct-model/migrate! content-types-model)
        body-params {:data (update-keys test-document #(keyword (str %)))}
        create-req (-> (mock/request :post "/api/rest/posts")
                       (merge {:model {:content-types-model content-types-model}
                               :body-params body-params
                               :current-user current-user}))
        create-res (handler create-req)
        delete-path (format "/api/rest/posts/%s"
                            (get-in create-res [:body :data :id]))
        delete-req (-> (mock/request :delete delete-path)
                       (merge {:model {:content-types-model content-types-model}}))
        delete-res (handler delete-req)]
    (is (match? {:status 204} delete-res))))

(deftest delete-non-existing-test
  (let [handler (test-handler)
        ds (test-ds)
        current-user {:id (random-uuid)}
        non-existing-content-item-id (random-uuid)
        content-types-model (ct-model/->model {:db-type :sqlite
                                               :ds ds
                                               :current-user current-user})
        _ (ct-model/migrate! content-types-model)
        delete-path (format "/api/rest/posts/%s" non-existing-content-item-id)
        delete-req (-> (mock/request :delete delete-path)
                       (merge {:model {:content-types-model content-types-model}}))
        delete-res (handler delete-req)]
    (is (match? {:status 404} delete-res))))
