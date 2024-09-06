(ns happyapi.temporal.workflows.auth
  (:require
   [clj-http.client :as http]
   [com.biffweb :as biff]
   [happyapi.oauth2.auth :as oauth2]
   [happyapi.temporal.utils :as utils]
   [happyapi.temporal.oauth2.credentials :as credentials]
   [temporal.activity :refer [defactivity] :as a]
   [temporal.client.core :as tc]
   [temporal.signals :refer [<! >!] :as sig]
   [temporal.workflow :refer [defworkflow] :as w]
   [temporal.exceptions :as te]
   [slingshot.slingshot :refer [throw+]]))

(def schema
  {::create
   ;; Create an auth
   [:map {:closed true}
    [:user     :user/id]
    [:provider :keyword]
    [:scopes   [:vector :string]]]

   ::finish
   ;; Finish an auth
   [:map {:closed true}
    [:code :string]
    [:state :string]]})

;; Get a provider login link
(defactivity get-link [{:keys [happyapi/config] :as ctx}

                       {:keys [provider scopes state-and-challenge] :as args}]

  (let [config (get config provider)

        {default-scopes :scopes
         :keys [authorization_options
                code_challenge_method]} config

        scopes (->> (concat default-scopes scopes) (into []))
        optional (merge authorization_options
                        {:state state-and-challenge}
                        (when code_challenge_method
                          {:code_challenge state-and-challenge}))]

    {:login-url (oauth2/provider-login-url config scopes optional)}))

;; Exchange an authenticaiton code for a token
(defactivity exchange-code
  [{:keys [happyapi/config] :as ctx}

   {:keys [provider state code state-and-challenge code_verifier] :as args}]
  (let [config (get config provider)
        {:keys [token_uri client_id client_secret redirect_uri]} config
        resp (delay (-> (http/post
                         token_uri
                         {:as  :json
                          :basic-auth [client_id client_secret]
                          :content-type :json
                          :form-params   (cond-> {:code         code
                                           :grant_type   "authorization_code"
                                           :redirect_uri redirect_uri}
                                    code_verifier (assoc :code_verifier code_verifier))
                          :debug-body true})
                        :body))]
    (if (and (some? state)
             (= state state-and-challenge))
      (oauth2/with-timestamp @resp)
      (throw+ {:type ::invalid-auth-state
               :state state
               :expected state-and-challenge
               ::te/non-retriable? true}))))

(defactivity persist-auth
  [{:temporal.activity/keys [get-info] :as ctx} {:keys [provider] :as args}]
  (let [ctx (biff/assoc-db ctx)
        {request-id :workflow-id} (get-info)
        request-id (parse-uuid request-id)]
    (credentials/save ctx provider request-id args)))

(defn- auth->state
  "Converts an auth request ID to an OAuth2 state string"
  [request-id]
  (str request-id))

(defn- state->request-id
  "Converts an OAuth2 state string to a request ID"
  [auth-state] auth-state)

;; Creates a new authentication
(defworkflow authentication [wf-args]
  (let [{id :workflow-id} (w/get-info)
        wf-args           (assoc wf-args :state-and-challenge (auth->state id))
        state             (atom wf-args)
        signals           {:start    get-link
                           :callback exchange-code}]

    (sig/register-signal-handler!
     (fn [signal-name {:keys [workflow-id] :as args}]
       (let [args     (merge args @state)
             activity (get signals (keyword signal-name))
             result   (when activity
                        (doto @(a/invoke activity args)
                          (#(>! workflow-id :result %))))]

         (swap! state merge result))))

    (w/register-query-handler! (fn [_ _] @state))

    ;; Wait until we have an access token
    (w/await
     (fn []
       (some? (:access_token @state))))

    @(a/invoke persist-auth @state)

    @state))

(defworkflow auth-start
  [{:keys [workflow-id]}]
  (let [signals             (sig/create-signal-chan)
        {this :workflow-id} (w/get-info)]
    (>! (str workflow-id) :start {:workflow-id this})
    (<! signals           :result)))

(defworkflow auth-callback
  [{:keys [workflow-id code state]}]
  (let [signals (sig/create-signal-chan)
        {this :workflow-id} (w/get-info)]
    (>! (str workflow-id) :callback {:workflow-id this
                                     :code code
                                     :state state})
    (<! signals           :result)))

(defn load [{:keys [biff/db]} {:keys [user provider]}]
  (biff/lookup db
               :auth/user user
               :auth/provider provider))

;; Creates an authentication for a user ID, provider ID, provider name (:google etc) and a list of scopes
(defn create [ctx {:keys [user provider scopes] :as params}]
  (let [request-id (random-uuid)
        request    #:auth-request{:user     user
                                  :provider provider
                                  :scopes   scopes}
        request (assoc request
                       :db/doc-type :auth-request
                       :xt/id       request-id)

        main   (delay (utils/trigger ctx authentication {:id request-id :params params}))
        start  (delay (utils/trigger ctx auth-start {:params {:workflow-id request-id}}))]

    (if (utils/valid? ctx ::create params)
      (do
        ;; Persist request, then fire off main
        (biff/submit-tx ctx [request])
        @main
        @(tc/get-result @start))
      {:error ::invalid-params
       :cause (utils/explain ctx ::create params)})))

;; Finish an auth request
(defn finish [ctx {:keys [code state] :as params}]
  (let [request  (state->request-id state)
        main     (delay (utils/trigger ctx authentication {:id request :signal ::->finish}))
        callback (delay (utils/trigger ctx auth-callback {:params {:workflow-id request
                                                                   :code code
                                                                   :state state}}))]
    (if (utils/valid? ctx ::finish params)
      (do
        @main
        @(tc/get-result @callback))
      (throw+ {:type ::invalid-params
               :explain (utils/explain ctx ::finish params)}))))

(def module
  {:schema schema})
