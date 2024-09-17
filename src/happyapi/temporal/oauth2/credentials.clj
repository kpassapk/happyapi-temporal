(ns happyapi.temporal.oauth2.credentials
  (:require
   [com.biffweb :as biff]))

(defn- ns-keys
  "Namespace-qualifies map keys"
  [m n]
  (reduce-kv (fn [acc k v]
               (let [new-kw (if (and (keyword? k)
                                     (not (qualified-keyword? k)))
                              (keyword (str n) (name k))
                              k) ]
                 (assoc acc new-kw v)))
             {} m))

(defn- un-ns-keys
  "Remove namespace qualification"
  [m]
  (update-keys m (comp keyword name)))

(defn load [{:keys [biff/db]} provider user]
  (-> (biff/lookup db :auth/user user)
      (dissoc :xt/id)
      un-ns-keys))

(defn save [ctx provider request-id credentials]
  (let [keys [:provider
              :user
              :access_token
              :refresh_token
              :scope
              :token_type
              :expires_at]]
    (biff/submit-tx ctx [(-> credentials
                             (select-keys keys)
                             (assoc :created_at :db/now)
                             (ns-keys "auth")
                             (assoc :db/doc-type :auth))])))
