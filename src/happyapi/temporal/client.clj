(ns happyapi.temporal.client
  (:require [happyapi.middleware :as middleware]
            [happyapi.temporal.oauth2.credentials :as credentials]
            [com.biffweb :as biff]))

(defn wrap-debug-middleware [request]
  (fn
    ([args]
     (let [_ (println "--------------- REQUEST ----------------------")
           _ (biff/pprint args)
           _ (def request* request)
           _ (def args* args)
           response (request (assoc args :debug-body true))]
       (println "----------------- RESPONSE ------------------")
       (biff/pprint response)
       (def response* response)
       response))
    ([args respond raise]
     (request args respond raise))))

(defn wrap-oauth2 [{:keys [app/get-user-fn] :as ctx} provider request]
  (fn [args]
    (let [user        (get-user-fn ctx)
          credentials (credentials/load ctx provider user)
          {:keys [access_token]} credentials]
      (if access_token
        (request (middleware/auth-header args access_token))
        (request args)))))

(defn make-client [{:keys [happyapi/config] :as ctx} provider]
  (let [{:keys [keywordize-keys]
         {:keys [request]} :fns :as config} (get config provider)]
    (when-not (middleware/fn-or-var? request)
    (throw (ex-info "request must be a function or var"
                    {:id      ::request-must-be-a-function
                     :request request
                     :config  config})))
    (-> request
        (wrap-debug-middleware)
        (middleware/wrap-cookie-policy-standard)
        (middleware/wrap-informative-exceptions)
        (middleware/wrap-json config)
        (->> (wrap-oauth2 ctx provider))
        (middleware/wrap-uri-template)
        (middleware/wrap-paging)
        (middleware/wrap-extract-result)
        (middleware/wrap-keywordize-keys keywordize-keys))))
