(ns clojure-atm.account)

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

(defn valid-pin? [account-number
                  pin
                  atm]
  (let [account-details
        (get-in @atm [:accounts account-number])]
    (= pin (:pin account-details))))

(defn valid-account-number? [account-number
                             atm]
  (contains? (:accounts @atm) account-number))

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
                        :description (str "The pin " pin " supplied for account number " account-number " is incorrect")}}]
      (if (every? true?
                  (map :passed? (vals validations)))
        true
        {:allow-withdrawal? false :reasons (let [parsed-validations
                                                 (select-keys validations (for [[k v] validations :when (false? (:passed? v))] k)) ]
                                             (if (false? (:passed? (:valid-pin? parsed-validations)))
                                               (select-keys parsed-validations [:valid-pin?])
                                               parsed-validations))}))
    {:allow-withdrawal? false :reason (str "Invalid/Unknown account number " account-number)}))