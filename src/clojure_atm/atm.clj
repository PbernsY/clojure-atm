(ns clojure-atm.atm
  (:require [clojure-atm.account :as account]))

(defn available-notes
  "Returns all available notes IE notes which have a count > 0"
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
  being called"
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
     :balance (get-in @atm [:accounts account-number :balance])}))

(defn withdraw-money [withdrawal-amount
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

(defn account-balance [account-number
                       pin
                       atm]
  (let [validations {:valid-account-number? {:passed? (account/valid-account-number? account-number
                                                                                     atm)
                                             :description "The account number supplied is invalid/unknown"}
                     :valid-pin? {:passed? (account/valid-pin?
                                             account-number
                                             pin
                                             atm)
                                  :description (str "The pin " pin " supplied for account number " account-number " is incorrect")}}]
    (if (every? true? (map :passed? (vals validations)))
      {:account-number account-number
       :balance (account/account-number->balance account-number
                                                 atm
                                                 false)
       :total-withdrawable-amount (account/account-number->balance account-number
                                                                   atm
                                                                   true)}
      {:allow-balance-display? false :reasons (let [parsed-validations
                                                    (select-keys validations (for [[k v] validations :when (false? (:passed? v))] k)) ]
                                                (if (false? (:passed? (:valid-pin? parsed-validations)))
                                                  (select-keys parsed-validations [:valid-pin?])
                                                  parsed-validations))})))