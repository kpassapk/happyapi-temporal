(ns happyapi.temporal.home
  (:require
   [cheshire.core :as json]
   [com.biffweb :as biff]
   [happyapi.temporal.middleware :as mid]
   [happyapi.temporal.ui :as ui]
   [happyapi.temporal.workflows.auth :as auth]))

(def scopes ["https://www.googleapis.com/auth/drive"
             "https://www.googleapis.com/auth/drive.file"
             "https://www.googleapis.com/auth/drive.readonly"
             "https://www.googleapis.com/auth/spreadsheets"
             "https://www.googleapis.com/auth/spreadsheets.readonly"])

(defn home-page [ctx]
  (ui/page
   ctx
   [:h1 "Welcome to your app!"]
   [:.h-4]
   [:.flex
    [:a.link {:href "/start"} "Start authentication"]]))

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

(defn spreadsheet [{:keys [happyapi/request] :as ctx}]
  (ui/page
   nil
   (let [spreadsheet-id "1vI2MXnZXTxOM-TEEmzIFx3z-ulz8wGZyO5Simj2vxHM"
         result (request
                 {:ctx ctx
                  :method :get,
                  :uri-template
                  "https://sheets.googleapis.com/v4/spreadsheets/{spreadsheetId}",
                  :uri-template-args {"spreadsheetId" spreadsheet-id},
                  :query-params {},
                  :scopes
                  ["https://www.googleapis.com/auth/drive"
                   "https://www.googleapis.com/auth/drive.file"
                   "https://www.googleapis.com/auth/drive.readonly"
                   "https://www.googleapis.com/auth/spreadsheets"
                   "https://www.googleapis.com/auth/spreadsheets.readonly"]})
         _ (def result* result)]
     [:h1 "Welcome to your app!"]
     [:pre
      (json/generate-string result {:pretty true})])))

(def module
  {:routes [["" {:middleware [mid/wrap-redirect-signed-in]}
             ["/" {:get home-page}]
             ["/start" {:get start-auth}]
             ["/redirect" {:get finish-auth}]]]})
