(ns repl
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [com.biffweb :as biff :refer [q]]
   [happyapi.temporal :as main]
   [happyapi.temporal.middleware :as mid]
   [happyapi.temporal.sheets-v4 :as sheets]
   [happyapi.temporal.workflows.auth :as auth]))

;; REPL-driven development
;; ----------------------------------------------------------------------------------------
;; If you're new to REPL-driven development, Biff makes it easy to get started: whenever
;; you save a file, your changes will be evaluated. Biff is structured so that in most
;; cases, that's all you'll need to do for your changes to take effect. (See main/refresh
;; below for more details.)
;;
;; The `clj -M:dev dev` command also starts an nREPL server on port 7888, so if you're
;; already familiar with REPL-driven development, you can connect to that with your editor.
;;
;; If you're used to jacking in with your editor first and then starting your app via the
;; REPL, you will need to instead connect your editor to the nREPL server that `clj -M:dev
;; dev` starts. e.g. if you use emacs, instead of running `cider-jack-in`, you would run
;; `cider-connect`. See "Connecting to a Running nREPL Server:"
;; https://docs.cider.mx/cider/basics/up_and_running.html#connect-to-a-running-nrepl-server
;; ----------------------------------------------------------------------------------------

;; This function should only be used from the REPL. Regular application code
;; should receive the system map from the parent Biff component. For example,
;; the use-jetty component merges the system map into incoming Ring requests.
(defn get-context []
  (biff/merge-context @main/system))

(defn add-fixtures []
  (biff/submit-tx (get-context)
    (-> (io/resource "fixtures.edn")
        slurp
        edn/read-string)))

(defn check-config []
  (let [prod-config (biff/use-aero-config {:biff.config/profile "prod"})
        dev-config  (biff/use-aero-config {:biff.config/profile "dev"})
        ;; Add keys for any other secrets you've added to resources/config.edn
        secret-keys [:biff.middleware/cookie-secret
                     :biff/jwt-secret
                     :mailersend/api-key
                     :recaptcha/secret-key
                     ; ...
                     ]
        get-secrets (fn [{:keys [biff/secret] :as config}]
                      (into {}
                            (map (fn [k]
                                   [k (secret k)]))
                            secret-keys))]
    {:prod-config prod-config
     :dev-config dev-config
     :prod-secrets (get-secrets prod-config)
     :dev-secrets (get-secrets dev-config)}))

(comment
  ;; Call this function if you make a change to main/initial-system,
  ;; main/components, :tasks, :queues, config.env, or deps.edn.
  (main/refresh)

  (let [spreadsheet-id "1vI2MXnZXTxOM-TEEmzIFx3z-ulz8wGZyO5Simj2vxHM"
        req (mid/wrap-happyapi-request (fn [_] (sheets/spreadsheets-get spreadsheet-id)))]
    (req (get-context)))

  (:happyapi/config (main/use-happyapi-config (biff/use-aero-config {})))

  (let [{:keys [biff/db] :as ctx} (get-context)
        user (biff/lookup db :user/email "kyle@unifica.ai" )]
    (auth/load ctx {:user (:xt/id user) :provider :google }))
  
  ;; Get all auths for user
  (let [{:keys [biff/db]} (get-context)]
    (biff/lookup db '[:xt/id {:auth/_user [:auth/provider :auth/access_token]}] :user/email "kyle@unifica.ai"))

  (->> (let [{:keys [biff/db]} (get-context)]
        (biff/lookup-all db '[:xt/id :auth/access_token] :auth/user #uuid "3e788b71-3e85-4327-a81d-012f358303c4")))

  ;; Call this in dev if you'd like to add some seed data to your database. If
  ;; you edit the seed data (in resources/fixtures.edn), you can reset the
  ;; database by running `rm -r storage/xtdb` (DON'T run that in prod),
  ;; restarting your app, and calling add-fixtures again.
  (add-fixtures)

  ;; Query the database
  (let [{:keys [biff/db] :as ctx} (get-context)]
    (q db
       '{:find (pull user [*])
         :where [[user :user/email]]}))

  ;; Update an existing user's email address
  (let [{:keys [biff/db] :as ctx} (get-context)
        user-id (biff/lookup-id db :user/email "hello@example.com")]
    (biff/submit-tx ctx
      [{:db/doc-type :user
        :xt/id user-id
        :db/op :update
        :user/email "new.address@example.com"}]))

  (let [{:keys [biff/db]} (get-context)
        user (biff/lookup-id db :user/email "kyle@unifica.ai")
        scopes ["https://www.googleapis.com/auth/drive"
                "https://www.googleapis.com/auth/drive.file"
                "https://www.googleapis.com/auth/drive.readonly"
                "https://www.googleapis.com/auth/spreadsheets"
                "https://www.googleapis.com/auth/spreadsheets.readonly"]]
    (auth/create (get-context) {:user user
                                 :provider :google
                                 :scopes scopes}))

  (sort (keys (get-context)))

  (let [{:keys [biff/db]} (get-context)]
    (auth/create (get-context) {:user (biff/lookup-id db :user/email "kyle@unifica.ai")
                                :provider :google
                                :scopes ["https://www.googleapis.com/auth/drive"
                                         "https://www.googleapis.com/auth/drive.file"
                                         "https://www.googleapis.com/auth/drive.readonly"
                                         "https://www.googleapis.com/auth/spreadsheets"
                                         "https://www.googleapis.com/auth/spreadsheets.readonly"]}))

  (def state
    {:scopes ["https://www.googleapis.com/auth/drive"
              "https://www.googleapis.com/auth/drive.file"
              "https://www.googleapis.com/auth/drive.readonly"
              "https://www.googleapis.com/auth/spreadsheets"
              "https://www.googleapis.com/auth/spreadsheets.readonly"]

     :created_at #inst "2024-09-05T02:24:41.765-00:00",
     :expires_at #inst "2024-09-05T02:24:41.765-00:00",
     :access_token "token",
     :refresh_token "refresh",
     :scope "https://www.googleapis.com/auth/spreadsheets.readonly https://www.googleapis.com/auth/drive https://www.googleapis.com/auth/drive.readonly https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive.file",
     :token_type "Bearer",
     :state-and-challenge "3ab0a819-5502-4e3f-b1aa-9a5ff5da6548",
     :expires_in 3599,
     :provider :google,
     :user #uuid "3e788b71-3e85-4327-a81d-012f358303c4",
     :login-url "url"})

  (let [ctx (assoc (get-context) :temporal.activity/get-info (constantly {:workflow-id "38587950-f771-45e2-abaf-e758446fde2d"}))]
    (auth/persist-auth ctx state))

  )
