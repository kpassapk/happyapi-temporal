(ns happyapi.temporal.home
  (:require
   [cheshire.core :as json]
   [com.biffweb :as biff]
   [happyapi.temporal.middleware :as mid]
   [happyapi.temporal.ui :as ui]
   [happyapi.temporal.workflows.auth :as auth]
   [happyapi.temporal.sheets-v4 :as sheets]))

(defn with-testuser [{:keys [biff/db] :as ctx} email]
  (let [userfn (fn [_] (biff/lookup-id db :user/email email))]
    (assoc ctx :app/get-user-fn userfn)))

(defn home-page [{:keys [biff/db] :as ctx}]
  (let [user (biff/lookup-id db :user/email "kyle@unifica.ai")
        _ (def ctx* ctx)
        _ (def user* user)
        auth (auth/load ctx* {:user user* :provider :google})]
    (ui/page
     ctx
     [:h1 "Welcome to your app!"]
     [:.h-4]
     [:.flex
      (if auth
        [:a.link {:href "/spreadsheet"} "Show spreadsheet"]
        [:a.link {:href "/start"} "Start authentication"])])))

(defn start-auth [{:keys [biff/db] :as ctx}]
  (let [user (biff/lookup-id db :user/email "kyle@unifica.ai") ;; TODO replace with session

        scopes ["https://www.googleapis.com/auth/drive"
                "https://www.googleapis.com/auth/drive.file"
                "https://www.googleapis.com/auth/drive.readonly"
                "https://www.googleapis.com/auth/spreadsheets"
                "https://www.googleapis.com/auth/spreadsheets.readonly"]

        auth (auth/create ctx {:user user
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
  [_]
  (ui/page
   nil
   (let [spreadsheet-id "1vI2MXnZXTxOM-TEEmzIFx3z-ulz8wGZyO5Simj2vxHM"
         result (sheets/spreadsheets-get spreadsheet-id)]
     [:h1 "Welcome to your app!"]
     [:pre
      (json/generate-string result {:pretty true})])))

(defn wrap-kyle [handler]
  (fn [ctx]
    (def handler* handler)
    (def ctx* ctx)
    (-> ctx*
        (with-testuser "kyle@unifica.ai")
        handler*)))

(def module
  {:routes [["" {:middleware [mid/wrap-redirect-signed-in]}
             ["/" {:get {:handler home-page
                         :middleware [wrap-kyle]}}]
             ["/start" {:get start-auth}]
             ["/redirect" {:get finish-auth}]
             ["/spreadsheet" {:get {:middleware [wrap-kyle
                                                 mid/wrap-happyapi-request]
                                    :handler spreadsheet}}]]]})
