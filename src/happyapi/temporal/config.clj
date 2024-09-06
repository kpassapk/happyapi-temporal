(ns happyapi.temporal.config
  (:require
   [clojure.string :as str]
   [happyapi.oauth2.client :as oauth2.client]
   [happyapi.middleware :as middleware]))

(defn verify-happyapi-config [provider {:as config {:keys [request]} :fns}]
  (def config* config)
  (when-let [ks (oauth2.client/missing-config config)]
    (throw (ex-info (str "Invalid config: missing " (str/join "," ks))
                    {:id      ::invalid-config
                     :missing (vec ks)
                     :config  config})))
  (when-not (middleware/fn-or-var? request)
    (throw (ex-info "request must be a function or var"
                    {:id           ::request-must-be-a-function
                     :request      request
                     :request-type (type request)})))
  (let [query-string (get-in config [:fns :query-string])]
    (when-not (middleware/fn-or-var? query-string)
      (throw (ex-info "query-string must be provided in config :fns :query-string as a function or var"
                      {:id           ::query-string-must-be-a-function
                       :query-string query-string
                       :config       config}))))
  [provider config])
