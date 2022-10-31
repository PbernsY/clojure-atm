(ns clojure-atm.atm
  (:require [clojure-atm.account :as account]))

(defn available-notes
  "Returns all available notes (sorted) IE notes which have a count > 0"
  [atm]
  (transduce
    (comp
      (filter (fn [note]
                (> (:count note) 0)))
      (map (fn [note]
             (:value note))))
    (fn
      ([] [])
      ([available-notes] (sort available-notes))
      ([notes-so-far next-note] (conj notes-so-far next-note)))
    (vals (:notes @atm))))

(defn maximum-note
  "Given a withdrawal amount , return the largest possible note available
  This is called repeatedly , up until withdrawal amount = 0
  Deducts the note returned from the total count of that note, no further checks on this necessary"
  [withdrawal-amount
   atm]
  (let [max-note
        (last
          (filter (fn [note]
                    (<= note withdrawal-amount))
                  (available-notes
                    atm)))]
    (swap! atm (fn [atm]
                 (update-in atm [:notes (keyword (str max-note)) :count]
                            (fn [note-count]
                              (dec note-count)))))
    max-note))

(defn minimum-notes
  "Given a withdrawal amount and an account number , return the amount in the minimum number of notes
  Algorithm ->
  1. Continuously select the largest available note until the withdrawal amount reaches 0
  2. If the value of the atm > withdrawal amount , we are certain this can be done. Caveat being we may use small
  denominations. This simulates real life behaviour
  3. If the value of the atm < withdrawal amount, do not attempt as not feasible. This is caught prior to this function
  being called
  This cannot be memoized , as there is a finite number of notes. Similar to Knapsack probelm , but DP also not worth
  trying to make work"
  [withdrawal-amount
   account-number
   atm]
  (let [notes-for-withdrawal
        (loop [notes' (transient [])
               number' withdrawal-amount]
          (if (< 0 number')
            (let [max-note (maximum-note number'
                                         atm)]
              (recur (conj! notes' max-note)
                     (- number' max-note)))
            (persistent! notes')))]
    (swap! atm (fn [atm]
                 (update atm :total (fn [total]
                                      (- total (reduce + notes-for-withdrawal))))))
    (swap! atm (fn [atm]
                 (update-in atm [:accounts account-number :balance]
                            (fn [balance]
                              (- balance withdrawal-amount)))))
    {:note->quantity (frequencies notes-for-withdrawal)
     :balance (get-in @atm [:accounts account-number :balance])
     :status 200}))

(defn withdraw-money
  "Given a withdrawal amount, Account Number and pin , perform withdrawl in the minimum number of notes possible"
  [withdrawal-amount
   account-number
   pin
   atm]
  (let [allow-withdrawal?
        (account/allow-withdrawal? withdrawal-amount
                                   account-number
                                   pin
                                   atm)]
    (if (true? allow-withdrawal?)
      (minimum-notes withdrawal-amount
                     account-number
                     atm)
      allow-withdrawal?)))

(defn deposit-into-account
  "Update the account within the atm with the given deposit amount"
  [deposit-amount
   account-number
   atm]
  (swap! atm (fn [atm]
               (update-in atm [:accounts account-number :balance]
                          (fn [balance]
                            (+ balance deposit-amount))))))

(defn update-atm-notes-with-deposit
  "Increase the count of the notes to reflect what was deposited"
  [notes->quantity
   atm]
  (reduce-kv (fn [_ key value]
               ; Leverage reduce-key-value as the key will be the note and value the count
               (swap! atm (fn [atm]
                            (update-in atm [:notes (keyword (str key)) :count]
                                       (fn [count]
                                         (+ count value))))))
             []
             notes->quantity))

(defn update-atm-total-with-deposit
  "Increase the total within the atm to the sum of the notes given"
  [deposit-amount
   atm]
  (swap! atm (fn [atm]
               (update atm :total (fn [total]
                                    (+ total deposit-amount))))))

(defn deposit-money [notes->quantity
                     account-number
                     pin
                     atm]
  (let [allow-deposit?
        (account/allow-deposit? notes->quantity
                                account-number
                                pin
                                atm)]
    (if (true? allow-deposit?)
      (let [balance-before-deposit (account/account-number->balance account-number
                                                                    atm
                                                                    false)
            total-withdrawable-before-deposit (account/account-number->balance account-number
                                                                               atm
                                                                               true)
            deposit-amount (account/notes->quantity->sum notes->quantity)]
        (update-atm-notes-with-deposit notes->quantity
                                       atm)
        (update-atm-total-with-deposit deposit-amount
                                       atm)
        (deposit-into-account deposit-amount
                              account-number
                              atm)
        {:account-number account-number
         :balance-before-deposit balance-before-deposit
         :total-withdrawable-before-deposit total-withdrawable-before-deposit
         :balance-after-deposit (account/account-number->balance account-number
                                                       atm
                                                       false)
         :total-withdrawable-after-deposit (account/account-number->balance account-number
                                                                            atm
                                                                            true)
         :status 200})
      allow-deposit?)))

(defn account-balance [account-number
                       pin
                       atm]
  (let [allow-balance-check?
        (account/allow-balance-check? account-number
                                      pin
                                      atm)]
    (if (true? allow-balance-check?)
      {:account-number account-number
       :balance (account/account-number->balance account-number
                                                 atm
                                                 false)
       :total-withdrawable-amount (account/account-number->balance account-number
                                                                   atm
                                                                   true)
       :status 200}
      allow-balance-check?)))