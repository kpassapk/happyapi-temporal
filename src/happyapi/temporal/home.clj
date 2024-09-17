(ns happyapi.temporal.home
  (:require
   [cheshire.core :as json]
   [com.biffweb :as biff]
   [happyapi.temporal.middleware :as mid]
   [happyapi.temporal.ui :as ui]
   [happyapi.temporal.workflows.auth :as auth]
   [happyapi.temporal.sheets-v4 :as sheets]
   [happyapi.providers.google :as google]))

(defn home-page [{:keys [biff/db app/get-user-fn] :as ctx}]
  (let [user (get-user-fn ctx)
        auth (biff/lookup db :auth/user user)]
    (ui/page
     ctx
     [:h1 "Welcome to your app!"]
     [:.h-4]
     [:.flex
      (if auth
        [:a.link {:href "/spreadsheet"} "Show spreadsheet"]
        [:a.link {:href "/start"} "Start authentication"])])))

(defn start-auth [{:keys [biff/db] :as ctx}]
  (let [args (sheets/spreadsheets-get nil) ;; hacky
        scopes (:scopes args)
        user (biff/lookup-id db :user/email "kyle@unifica.ai")
        
        auth (auth/start ctx {:user user
                              :provider :google
                              :scopes scopes})
        login-url (:login-url auth)]
    (ui/page
     ctx
     [:h1 "Start authentication"]
     [:.flex
      [:a.btn {:href login-url} "Log in with Google"]])))

;; Get state + code
;; Continue auth workflow with the ID from the state
(defn finish-auth [{:keys [query-params] :as ctx}]
  (let [{:strs [code state]} query-params
        result (auth/finish ctx {:code code :state state})])
  {:status 303
   :headers {"location" "/"}})

(defn spreadsheet
  "Show a single spreadsheet"
  [{:keys [gsheets/spreadsheet-id] :as ctx}]
  (ui/page
   nil
   (let [args   (sheets/spreadsheets-get spreadsheet-id)
         result (google/*api-request* args)] ;
     [:<>
      [:h1 "Welcome to your app!"]
      [:pre
       (json/generate-string result {:pretty true})]])))

(def module
  {:routes [["" {:middleware [mid/wrap-kyle]}
             ["/" {:get home-page}]
             ["/start" {:get start-auth}]
             ["/redirect" {:get finish-auth}]
             ["/spreadsheet" {:get {:middleware [mid/wrap-happyapi-request]
                                    :handler spreadsheet}}]]]})

