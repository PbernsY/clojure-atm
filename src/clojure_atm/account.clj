(ns clojure-atm.account)

(def valid-notes #{5 10 20 50})

(defn account-number->balance
  "Given an account number , return the balance associated with that account
  include-overdraft? will dictate whether or not to include this in the balance"
  [account-number
   atm
   include-overdraft?]
  (let [account-details
        (get-in @atm [:accounts account-number])]
    (+ (:balance account-details) (if include-overdraft?
                                    (:overdraft account-details)
                                    0))))
(defn valid-pin?
  "Given a pin and account , check if the pin matches"
  [account-number
   pin
   atm]
  (let [account-details
        (get-in @atm [:accounts account-number])]
    (= pin (:pin account-details))))

(defn valid-account-number? [account-number
                             atm]
  (contains? (:accounts @atm) account-number))

(defn notes->quantity-valid-format?
  "Given a notes->quantity we need to ensure the following
  1. All keys (which are the notes) are integers
  2. All values (which is the quanity of the notes) are integers
  3. All values are > 0 IE we don't try to add 0 50 notes
  4. All keys are valid notes (one of 5 10 20 50)"
  [notes->quantity]
  (every? true?
          [(every? int? (keys notes->quantity))
           (every? (fn [quantity]
                     (> quantity 0)) (vals notes->quantity))
           (every? int? (vals notes->quantity))
           (every? true? (map (fn [note]
                                (contains? valid-notes note))
                              (keys notes->quantity)))]))

{:50 50 :20 20 :10 10 :5 5}

(defn notes->quantity->sum [notes->quantity]
  (reduce-kv (fn [total key value]
               (+ total (* key value)))
             0
             notes->quantity))

(defn allow-balance-check? [account-number
                            pin
                            atm]
  (let [validations {:valid-account-number? {:passed? (valid-account-number? account-number
                                                                                     atm)
                                             :description "The account number supplied is invalid/unknown"}
                     :valid-pin? {:passed? (valid-pin?
                                             account-number
                                             pin
                                             atm)
                                  :description (str "The pin " pin " supplied for account number " account-number " is incorrect")}}]
    (if (every? true? (map :passed? (vals validations)))
      true
      {:allow-balance-display? false :reasons (let [parsed-validations
                                                    (select-keys validations (for [[k v] validations :when (false? (:passed? v))] k)) ]
                                                (if (false? (:passed? (:valid-pin? parsed-validations)))
                                                  (select-keys parsed-validations [:valid-pin?])
                                                  parsed-validations))
       :status 400})))

(defn allow-deposit? [notes->quantity
                      account-number
                      pin
                      atm]

  (if (valid-account-number? account-number
                             atm)
    (if (notes->quantity-valid-format? notes->quantity)
      (let [validations
            {:valid-pin? {:passed? (valid-pin? account-number
                                               pin
                                               atm)
                          :description (str "The pin " pin " supplied for account number " account-number " is incorrect")}}]
        (if (every? true?
                    (map :passed? (vals validations)))
          true
          {:allow-deposit? false :reasons (let [parsed-validations
                                                   (select-keys validations (for [[k v] validations :when (false? (:passed? v))] k)) ]
                                               ; Below checks to see if an invalid pin was supplied
                                               ; If it was , we should only return that error , not anything else
                                               (if (false? (:passed? (:valid-pin? parsed-validations)))
                                                 (select-keys parsed-validations [:valid-pin?])
                                                 parsed-validations))
           :status 400}))
      {:allow-deposit? false :reasons (str "The notes->quantity supplied " notes->quantity " is not in the format of {Int Note Int Quantity} , Note is one of  " valid-notes " and quantity > 0")
       :status 400})
    {:allow-withdrawal? false
     :reason            "Invalid/Unknown account number 123456"
     :status            400}))

(defn allow-withdrawal? [amount
                         account-number
                         pin
                         atm]
  (if (valid-account-number? account-number
                             atm)
    (let [atm-total (:total @atm)
          account-total (account-number->balance account-number
                                                 atm
                                                 true)
          validations
          {:requested-amount-less-than-atm-total? {:passed? (<= amount atm-total)
                                                   :description (str "The amount requested " amount " is larger than atm total of " (:total @atm))}
           :requested-amount-less-than-account-total? {:passed? (<= amount account-total)
                                                       :description (str "The amount requested is larger than the total withdrawable amount from your account of " account-total)}
           :valid-pin? {:passed? (valid-pin? account-number
                                             pin
                                             atm)
                        :description (str "The pin " pin " supplied for account number " account-number " is incorrect")}
           :dispensable-amount? {:passed? (= 0 (mod amount 5))
                                 :description (str "The amount requested " amount " is not dispensable using 50, 20 , 10 or 5 denominations")}}]
      (if (every? true?
                  (map :passed? (vals validations)))
        true
        {:allow-withdrawal? false :reasons (let [parsed-validations
                                                 (select-keys validations (for [[k v] validations :when (false? (:passed? v))] k)) ]
                                             (if (false? (:passed? (:valid-pin? parsed-validations)))
                                               (select-keys parsed-validations [:valid-pin?])
                                               parsed-validations))
         :status 400}))
    {:allow-withdrawal? false :reason (str "Invalid/Unknown account number " account-number)
     :status 400}))