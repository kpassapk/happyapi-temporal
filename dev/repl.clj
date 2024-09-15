(ns repl
  (:require
   [taoensso.nippy :as nippy]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [com.biffweb :as biff :refer [q]]
   [happyapi.temporal :as main]
   [happyapi.temporal.middleware :as mid]
   [happyapi.temporal.sheets-v4 :as sheets]
   [happyapi.temporal.workflows.auth :as auth]
   [clj-http.client :as client]
   [cheshire.core :as json]))

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

  ;; get all auth requests for app user
  (let [{:keys [biff/db app/get-user-fn] :as ctx} ((mid/wrap-kyle identity) (get-context))
        user (get-user-fn ctx)]
    (q db
       '{:find (pull auth [*])
         :where [[auth :auth-request/user]]}))
  
  (let [spreadsheet-id "1vI2MXnZXTxOM-TEEmzIFx3z-ulz8wGZyO5Simj2vxHM"
        req (mid/wrap-happyapi-request (fn [_] (sheets/spreadsheets-get spreadsheet-id)))]
    (req (get-context)))

  (:happyapi/config (main/use-happyapi-config (biff/use-aero-config {})))

  (let [{:keys [biff/db] :as ctx} (get-context)
        user (biff/lookup db :user/email "kyle@unifica.ai" )]
    (auth/start ctx {:user (:xt/id user) :provider :google :scopes ["scope1"]}))
  
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
    (auth/start (get-context) {:user user
                                 :provider :google
                                 :scopes scopes}))

  (sort (keys (get-context)))

  (let [{:keys [biff/db]} (get-context)]
    (auth/request (get-context) {:user (biff/lookup-id db :user/email "kyle@unifica.ai")
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

  (def data "TlBZAHAKagZzY29wZXNuBWklaHR0cHM6Ly93d3cuZ29vZ2xlYXBpcy5jb20vYXV0aC9kcml2ZWkqaHR0cHM6Ly93d3cuZ29vZ2xlYXBpcy5jb20vYXV0aC9kcml2ZS5maWxlaS5odHRwczovL3d3dy5nb29nbGVhcGlzLmNvbS9hdXRoL2RyaXZlLnJlYWRvbmx5aSxodHRwczovL3d3dy5nb29nbGVhcGlzLmNvbS9hdXRoL3NwcmVhZHNoZWV0c2k1aHR0cHM6Ly93d3cuZ29vZ2xlYXBpcy5jb20vYXV0aC9zcHJlYWRzaGVldHMucmVhZG9ubHlqCmV4cGlyZXNfYXRaAAABke4fittqDGFjY2Vzc190b2tlbhAA3nlhMjkuYTBBY002MTJ4UHA0SmhydkV5Z05GZHBvam54aDVsZEp2OG0tWHNhYnAzanVJcTVmTjhLc1lNWUlhWVE3bG9yWFFoTGZmNThzUGk1YnZiOWRoTW1GZllNX0FLYnlFOEdqUTBycFEzWENPckxtOEJob0dmQk9HU2poX3l0b1dDVmliNkZ2dU1DbmZpOU1VN21VVU1mM1k2R282ZmhlMm4wUWdkV0pUX0Q2aUxhQ2dZS0FmNFNBUk1TRlFIR1gyTWk3dEh0MnJaMk1VblhkVDdYNGMtaEhRMDE3NWoNcmVmcmVzaF90b2tlbmlnMS8vMGh4RlN1WXhPSm1BOENnWUlBUkFBR0JFU053Ri1MOUlydktSM0ZOd1dtLVFqUjJ6VEEyZHFRNkh3V01weUNQOHVYWWV4U1ZZMldGSmF0TktMaTFOWmxrcFF3UzZoU3hwczh5RWoFc3RhdGVpJDc4Y2FkZTllLTA4NjQtNDA0Zi05MTJkLTQ2MGU4OTdjMjJmZGoFc2NvcGUQAOJodHRwczovL3d3dy5nb29nbGVhcGlzLmNvbS9hdXRoL3NwcmVhZHNoZWV0cy5yZWFkb25seSBodHRwczovL3d3dy5nb29nbGVhcGlzLmNvbS9hdXRoL2RyaXZlLnJlYWRvbmx5IGh0dHBzOi8vd3d3Lmdvb2dsZWFwaXMuY29tL2F1dGgvZHJpdmUgaHR0cHM6Ly93d3cuZ29vZ2xlYXBpcy5jb20vYXV0aC9kcml2ZS5maWxlIGh0dHBzOi8vd3d3Lmdvb2dsZWFwaXMuY29tL2F1dGgvc3ByZWFkc2hlZXRzagp0b2tlbl90eXBlaQZCZWFyZXJqCmV4cGlyZXNfaW4qAAAOD2oIcHJvdmlkZXJqBmdvb2dsZWoEdXNlcltyJJfTNThKU43Iw+DziICX")
  
  (def payload
    {:payloads 
     [{:metadata
       {:encoding "YmluYXJ5L3BsYWlu", :encodingDecoded "binary/plain"},
       :data data}]})

  (client/post "http://localhost:8080/codec/decode"
               {:content-type :json
                :as :json
                :accept :json
                :body (json/encode payload)
                :headers {"origin" "http://localhost:8233"
                          "access-control-request-method" "POST"}})

  (add-tap (fn [v] (def v* v)))
  
  )
