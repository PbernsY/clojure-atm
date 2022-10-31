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
            [jsonista.core :as json]
            [reitit.ring.middleware.exception :as exception]
            [schema.core :as s])
  (:gen-class)
  (:import (clojure.lang ExceptionInfo)
           (com.fasterxml.jackson.core JsonParseException)))

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

(derive ::error ::exception)
(derive ::failure ::exception)
(derive ::horror ::exception)

(def exception-middleware
  (exception/create-exception-middleware
    (merge
      exception/default-handlers
      {::exception      (partial handler "exception")
       ExceptionInfo    (partial handler "clojure-exception2")
       :muuntaja/decode (partial handler "clojure-exception")
       JsonParseException (partial handler "json-parse-exception")
                        ::exception/default (partial handler "default")})))

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
                                                    :balance s/Int}}}}}]
       ["/deposit" {:post {:handler (fn [{{notes->quantity :notes->quantity
                                            account-number :account-number
                                            pin :pin} :body-params}]
                                      (let [keyword->int {:50 50 :20 20 :10 10 :5 5}
                                            converted-notes->quantity
                                            (reduce-kv (fn [acc key value]
                                                         (assoc acc (key keyword->int) value))
                                                       {}
                                                       notes->quantity)]
                                        (assoc default-response :body
                                                                (atm/deposit-money converted-notes->quantity
                                                                                   account-number
                                                                                   pin
                                                                                   atm))))
                            :parameters {:body {:notes->quantity s/Any
                                                :account-number s/Int
                                                :pin s/Int}}
                            :responses {200 {:body {:account-number s/Int
                                                    :balance-after-deposit s/Int
                                                    :balance-before-deposit s/Int
                                                    :total-withdrawable-amount s/Int
                                                    :total-withdrawable-after-deposit s/Int}}}}}]]
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

(defn -main [& args]
  (start-atm-server))