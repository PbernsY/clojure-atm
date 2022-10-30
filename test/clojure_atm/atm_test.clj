(ns clojure-atm.atm-test
  (:require [clojure.test :refer :all]
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

(def atm-no-notes (atom {:total 0
                         :notes {:50 {:value 50 :count 0}
                                 :20 {:value 20 :count 0}
                                 :10 {:value 10 :count 0}
                                 :5 {:value 5 :count 0}}
                         :accounts {123456789 {:pin 1234
                                               :balance 800
                                               :overdraft 200}
                                    987654321 {:pin 4321
                                               :balance 1230
                                               :overdraft 150}}}))

(deftest test-available-notes
  (testing "ALl notes that have a count of > 0 are returned"
    (is (= (atm/available-notes atm)
           '(5 10 20 50))))
  (testing "Notes with a count of 0 are not returned"
    (swap! atm (fn [atm]
                 (update-in atm [:notes :50 :count]
                            (fn [count]
                              0))))
    (is (= (atm/available-notes atm)
           '(5 10 20))))
  (testing "If no notes are available, nothing is returned"
    (is (= (atm/available-notes atm-no-notes)
           []))))

(deftest test-maximum-note
  (testing "The maximum note is returned dependent on value, and deducted from atm"
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
                                       :overdraft 150}}} )
    (is (= (atm/maximum-note 10
                             atm)
           10))
    (is (= (get-in @atm [:notes :10 :count])
           29))
    (is (= (atm/maximum-note 15
                             atm)
           10))
    (is (= (get-in @atm [:notes :10 :count])
           28))
    (is (= (atm/maximum-note 5000000000000
                             atm)
           50))
    (is (= (get-in @atm [:notes :50 :count])
           9))
    (testing "If only a certain note is available , that is used agnostic of requested amount"
      (reset! atm {:total 1500
                   :notes {:50 {:value 50 :count 0}
                           :20 {:value 20 :count 0}
                           :10 {:value 10 :count 0}
                           :5 {:value 5 :count 20}}
                   :accounts {123456789 {:pin 1234
                                         :balance 800
                                         :overdraft 200}
                              987654321 {:pin 4321
                                         :balance 1230
                                         :overdraft 150}}})
      (is (= (atm/maximum-note 30
                               atm)
             5))
      (is (= (atm/maximum-note 500000000000000000
                               atm)
             5)))))

(deftest minimum-notes
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
  (testing "Given an amount and account number , return the minimum number of notes needed to satisfy withdraw
  NO VALIDATION IS USED IN THIS FUNC AT THIS POINT , ALL VALIDATION DONE PRIOR SO OD+BALANCE IS NOT RESPECTED"
    (is (= (atm/minimum-notes 50
                              123456789
                              atm)
           {:balance        750
            :note->quantity {50 1}}))
    (is (= (atm/minimum-notes 20
                              123456789
                              atm)
           {:balance        730
            :note->quantity {20 1}}))
    (is (= (atm/minimum-notes 10
                              123456789
                              atm)
           {:balance        720
            :note->quantity {10 1}}))
    (is (= (atm/minimum-notes 5
                              123456789
                              atm)
           {:balance        715
            :note->quantity {5 1}}))
    (is (= (atm/minimum-notes 25
                              123456789
                              atm)
           {:balance        690
            :note->quantity {20 1
                             5  1}}))
    (is (= (atm/minimum-notes 100
                              123456789
                              atm)
           {:balance        590
            :note->quantity {50 2}}))
    (is (= (atm/minimum-notes 1000
                              123456789
                              atm)
           {:balance        -410
            :note->quantity {10 9
                             20 28
                             50 7}}))
    (testing "ATM was updated accurately with the above withdrawals"
      (is (= @atm
             {:accounts {123456789 {:balance   -410
                                    :overdraft 200
                                    :pin       1234}
                         987654321 {:balance   1230
                                    :overdraft 150
                                    :pin       4321}}
              :notes    {:10 {:count 20
                              :value 10}
                         :20 {:count 0
                              :value 20}
                         :5  {:count 18
                              :value 5}
                         :50 {:count 0
                              :value 50}}
              :total    290})))
    (testing "ATM will dispense max at time , ie if asked for 100 , but only has 5's , will only use 5"
      (reset! atm {:total 1500
                   :notes {:50 {:value 50 :count 0}
                           :20 {:value 20 :count 0}
                           :10 {:value 10 :count 0}
                           :5 {:value 5 :count 300}}
                   :accounts {123456789 {:pin 1234
                                         :balance 1500
                                         :overdraft 200}
                              987654321 {:pin 4321
                                         :balance 1230
                                         :overdraft 150}}})
      (is (= (count (atm/minimum-notes 1500
                                       123456789
                                       atm))
             2)))))

(deftest integration-testing-withdrawing-money
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
  (testing "Below test will fully end to end integrate test the withdrawal functionality"
    (is (= (atm/withdraw-money 100
                               123456789
                               1234
                               atm)
           {:balance        700
            :note->quantity {50 2}}))
    (is (= (get-in @atm [:accounts 123456789 :balance])
           700)))
  (testing "Testing the overdraft facility and ensuring it is respected"
    (is (= (atm/withdraw-money 900
                               123456789
                               1234
                               atm)
           {:balance              -200
            :note->quantity  {20 25
                              50 8}}))
    (is (= (get-in @atm [:accounts 123456789 :balance])
           -200)))
  (testing "Trying to withdraw when an account is exhausted fails"
    (is (= (atm/withdraw-money 200
                               123456789
                               1234
                               atm)
           {:allow-withdrawal? false
            :reasons           {:requested-amount-less-than-account-total? {:description "The amount requested is larger than the total withdrawable amount from your account of 0"
                                                                            :passed?     false}}})))
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
  (testing "Trying to withdraw when an account is exhausted fails"
    (is (= (atm/withdraw-money 200
                               123456789
                               1234
                               atm)
           {:allow-withdrawal? false
            :reasons           {:requested-amount-less-than-atm-total? {:description "The amount requested 200 is larger than atm total of 1"
                                                                        :passed?     false}}})))
  (testing "Trying to withdraw balance + OD that exceeds total available fails"
    (reset! atm {:total 1500
                 :notes {:50 {:value 50 :count 10}
                         :20 {:value 20 :count 30}
                         :10 {:value 10 :count 30}
                         :5 {:value 5 :count 20}}
                 :accounts {123456789 {:pin 1234
                                       :balance 1500
                                       :overdraft 200}
                            987654321 {:pin 4321
                                       :balance 1230
                                       :overdraft 150}}})
    (is (= (atm/withdraw-money 1700
                               123456789
                               1234
                               atm)
           {:allow-withdrawal? false
            :reasons           {:requested-amount-less-than-atm-total? {:description "The amount requested 1700 is larger than atm total of 1500"
                                                                        :passed?     false}}})))
  (testing "Trying to withdraw with an account that doesnt exist fails"
    (reset! atm {:total 1500
                 :notes {:50 {:value 50 :count 10}
                         :20 {:value 20 :count 30}
                         :10 {:value 10 :count 30}
                         :5 {:value 5 :count 20}}
                 :accounts {123456789 {:pin 1234
                                       :balance 1500
                                       :overdraft 200}
                            987654321 {:pin 4321
                                       :balance 1230
                                       :overdraft 150}}})
    (is (= (atm/withdraw-money 1700
                               123456
                               1234
                               atm)
           {:allow-withdrawal? false
            :reason            "Invalid/Unknown account number 123456"}))))

(deftest testing-account-balance
  (testing "Account balance is returned once verification succeeds"
    (is (= (atm/account-balance 123456789
                                1234
                                atm)
           {:account-number            123456789
            :balance                   0
            :total-withdrawable-amount 200}))
    (reset! atm {:total 1500
                 :notes {:50 {:value 50 :count 10}
                         :20 {:value 20 :count 30}
                         :10 {:value 10 :count 30}
                         :5 {:value 5 :count 20}}
                 :accounts {123456789 {:pin 1234
                                       :balance 1500
                                       :overdraft 200}
                            987654321 {:pin 4321
                                       :balance 1230
                                       :overdraft 150}}})
    (testing "Changing the atm reflects in balance"
      (is (= (atm/account-balance 123456789
                                  1234
                                  atm)
             {:account-number            123456789
              :balance                   1500
              :total-withdrawable-amount 1700})))
    (testing "Supplying invalid details wont expose customer balance"
      (is (= (atm/account-balance 123456789
                                  1235
                                  atm)
             {:allow-balance-display? false
              :reasons                {:valid-pin? {:description "The pin 1235 supplied for account number 123456789 is incorrect"
                                                    :passed?     false}}})))))