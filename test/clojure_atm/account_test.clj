(ns clojure-atm.account-test
  (:require [clojure.test :refer :all]
            [clojure-atm.account :as account]
            [clojure-atm.atm :as atm]))

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

(deftest testing-account->balance
  (testing "Testing the correct balance is returned depending on execution mode"
    (is (= (account/account-number->balance 123456789
                                            atm
                                            true)
           1000))
    (is (= (account/account-number->balance 123456789
                                            atm
                                            false)
           800))
    (reset! atm {:total 1500
                 :notes {:50 {:value 50 :count 10}
                         :20 {:value 20 :count 30}
                         :10 {:value 10 :count 30}
                         :5 {:value 5 :count 20}}
                 :accounts {123456789 {:pin 1234
                                       :balance 800
                                       :overdraft 200}
                            987654321 {:pin 4321
                                       :balance 1230
                                       :overdraft 150}}})
    (atm/withdraw-money 100
                        123456789
                        1234
                        atm)
    (testing "After a withdrawal , the balance is updated accordingly"
      (is (= (account/account-number->balance 123456789
                                              atm
                                              true)
             900))
      (is (= (account/account-number->balance 123456789
                                              atm
                                              false)
             700)))))

(deftest testing-pin-validation
  (testing "A valid pin ensures validation"
    (is (= (account/valid-pin? 123456789
                               1234
                               atm)
           true))
    (is (= (account/valid-pin? 987654321
                               4321
                               atm)
           true)))
  (testing "An invalid pin does not allow validation"
    (is (= (account/valid-pin? 123456789
                               4321
                               atm)
           false))
    (is (= (account/valid-pin? 987654321
                               1234
                               atm)
           false))))

(deftest testing-account-number-validation
  (testing "Ensure valid account numbers are returned as existing"
    (is (= (account/valid-account-number? 123456789
                                          atm)
           true))
    (is (= (account/valid-account-number? 987654321
                                          atm)
           true)))
  (testing "Ensure invalid account numbers are returned as non existent"
    (is (= (account/valid-account-number? 12345678
                                          atm)
           false))
    (is (= (account/valid-account-number? 1
                                          atm)
           false))
    (is (= (account/valid-account-number? 12345678111111111111
                                          atm)
           false))))

(deftest integration-test-account-verification
  (testing "Integration testing of the withdrawal validation functionality , based on account"
    (is (= (account/allow-withdrawal? 100
                                      123456789
                                      1234
                                      atm)
           true))
    (testing "Withdrawing up to balance + OD is allowed , once below atm total"
      (is (= (account/allow-withdrawal? 1000
                                        123456789
                                        1234
                                        atm)
             true)))
    (testing "Attempting to withdraw balance + OD which is larger than atm total fails"
      (reset! atm {:total 1
                   :notes {:50 {:value 50 :count 10}
                           :20 {:value 20 :count 30}
                           :10 {:value 10 :count 30}
                           :5 {:value 5 :count 20}}
                   :accounts {123456789 {:pin 1234
                                         :balance 800
                                         :overdraft 200}
                              987654321 {:pin 4321
                                         :balance 1230
                                         :overdraft 150}}})
      (is (= (account/allow-withdrawal? 1000
                                        123456789
                                        1234
                                        atm)

             {:allow-withdrawal? false
              :reasons           {:requested-amount-less-than-atm-total? {:description "The amount requested 1000 is larger than atm total of 1"
                                                                          :passed?     false}}})))
    (testing "Attempting to withdraw with an incorrect pin and amount larger than the atm fails"
      (is (= (account/allow-withdrawal? 1000
                                        123456789
                                        12345
                                        atm)
             {:allow-withdrawal? false
              :reasons           {:valid-pin? {:description "The pin 12345 supplied for account number 123456789 is incorrect"
                                               :passed?     false}}})))))