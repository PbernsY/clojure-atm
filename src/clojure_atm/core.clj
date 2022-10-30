(ns clojure-atm.core
  (:require [clojure-atm.atm :as atm]
            [reitit.ring :as ring]
            [muuntaja.core :as m]
            [reitit.ring.middleware.muuntaja :as munntaga]
            [jsonista.core :as json]
            [com.appsflyer.donkey.core :refer [create-donkey create-server]]
            [com.appsflyer.donkey.server :refer [start]]
            [com.appsflyer.donkey.result :refer [on-success]]
            [reitit.coercion.schema]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.exception :as exception]
            [schema.core :as s])
  (:gen-class)
  (:import (clojure.lang ExceptionInfo)))

(def atm (atom {:total 1500
                :notes {:50 {:value 50 :count 10}
                        :20 {:value 20 :count 30}
                        :10 {:value 10 :count 30}
                        :5 {:value 5 :count 20}}
                :accounts {123456789 {:pin 1234
                                      :balance 800
                                      :overdraft 200}
                           987654321 {:pin 4321
                                      :balance 1230
                                      :overdraft 150}}}))

(def default-response {:status 200
                       :body (json/write-value-as-string {:body 1})
                       :headers {"Content-type" "application/json"}})

(defn handler [message exception request]
  {:status 400
   :body {:message message
          :exception (.getClass exception)
          :data (ex-data exception)
          :uri (:uri request)}})

(def exception-middleware
  (exception/create-exception-middleware
    (merge
      exception/default-handlers
      {::error             (partial handler "error")

       ::exception         (partial handler "exception")

       ExceptionInfo       (partial handler "clojure-exception")

       ::exception/default (partial handler "default")

       ::exception/wrap    (fn [handler e request]
                             (println "ERROR" (pr-str (:uri request)))
                             (handler e request))})))

(def PositiveInt (s/constrained s/Int pos? 'PositiveInt))

(def atm-api
  (ring/ring-handler
    (ring/router
      [["/balance" {:post {:handler (fn [{{account-number :account-number
                                           pin :pin} :body-params}]
                                      (assoc default-response :body
                                                              (atm/account-balance account-number
                                                                                   pin
                                                                                   atm)))
                           :parameters {:body {:account-number PositiveInt
                                               :pin PositiveInt}}
                           :responses {200 {:body {:account-number s/Int
                                                   :balance s/Int
                                                   :total-withdrawable-amount PositiveInt}}}}}]
       ["/withdraw" {:post {:handler (fn [{{withdrawal-amount :withdrawal-amount
                                            account-number :account-number
                                            pin :pin} :body-params}]
                                       (assoc default-response :body
                                                               (atm/withdraw-money withdrawal-amount
                                                                                   account-number
                                                                                   pin
                                                                                   atm)))
                            :parameters {:body {:account-number s/Int
                                                :pin s/Int
                                                :withdrawal-amount PositiveInt}}
                            :responses {200 {:body {:note->quantity s/->MapEntry
                                                    :balance s/Int}}}}}]]
      {:data {:muuntaja   m/instance
              :coercion reitit.coercion.schema/coercion
              :middleware [munntaga/format-middleware
                           exception-middleware
                           rrc/coerce-request-middleware]}})))

(defn start-atm-server []
  (-> (create-donkey)
      (create-server {:port 8080
                      :routes [{:handler atm-api
                                :handler-mode :blocking}]})
      start
      (on-success (fn [_] (println "Server started listening on port 8080")))))