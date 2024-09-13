(ns happyapi.temporal.workflows.auth
  (:require
   [clj-http.client :as http]
   [com.biffweb :as biff]
   [happyapi.oauth2.auth :as oauth2]
   [happyapi.temporal.oauth2.credentials :as credentials]
   [happyapi.temporal.utils :as u]
   [slingshot.slingshot :refer [throw+]]
   [temporal.activity :refer [defactivity] :as a]
   [temporal.client.core :as tc]
   [temporal.exceptions :as te]
   [temporal.signals :as sig]
   [temporal.workflow :refer [defworkflow] :as w]
   [unifica.temporal.workflow :as workflow]))

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

;; - perhaps change state-and-challenge -> state (challenge is not relevant  here)
;; - activity -> regular function (no side effects here).
;; - return a URL instead of a map

;; Get a provider login link
(defn get-link [{:keys [happyapi/config] :as ctx}

                {:keys [provider scopes state] :as args}]

  (let [config (get config provider)

        {default-scopes :scopes
         :keys [authorization_options
                code_challenge_method]} config

        scopes (->> (concat default-scopes scopes) (into []))
        optional (merge authorization_options
                        {:state state}
                        (when code_challenge_method
                          {:code_challenge state}))]

    (oauth2/provider-login-url config scopes optional)))

;; Exchange an authenticaiton code for a token
(defactivity exchange-code
  [{:keys [happyapi/config http/debug] :as ctx}

   {:keys [provider code code_verifier] :as args}]
  (let [config (get config provider)
        {:keys [token_uri client_id client_secret redirect_uri]} config]
    (-> (http/post
         token_uri
         {:as  :json
          :basic-auth [client_id client_secret]
          :content-type :json
          :form-params   (cond-> {:code  code
                                  :grant_type   "authorization_code"
                                  :redirect_uri redirect_uri}
                           code_verifier (assoc :code_verifier code_verifier))
          :debug-body debug})
        :body
        oauth2/with-timestamp)))

;; Persist auth info
(defactivity persist-auth
  [{:temporal.activity/keys [get-info] :as ctx} {:keys [provider] :as args}]
  (let [ctx (biff/assoc-db ctx)
        {request-id :workflow-id} (get-info)
        request-id (parse-uuid request-id)]
    (credentials/save ctx provider request-id args)))

(defn- request->state
  "Converts an auth request to an OAuth2 state string"
  [request]
  (str (:xt/id request)))

(defn- state->request
  "Converts an OAuth2 state string to an auth request"
  [{:keys [biff/db app/get-user-fn] :as ctx} auth-state]
  (let [request-id (parse-uuid auth-state)
        user (get-user-fn ctx)]
    (biff/lookup db :xt/id request-id :auth-request/user user)))

;; Creates a new authentication
(defworkflow authentication [{:keys [state] :as wf-args}]
  ;; Use workflow arguments as initial workflow state
  (let [wf-state (atom wf-args)]

    (sig/register-signal-handler!
     (fn [signal-name {:keys [state] :as args}]
       (let [expected (:state @wf-state)
             args     (merge @wf-state args)             
             result   (when (= (keyword signal-name) ::callback)
                        (if (= state expected)
                          @(a/invoke exchange-code args)
                          (throw+ {:type ::invalid-state
                                   :state state
                                   :expected expected
                                   ::te/non-retryable true})))]

         (swap! wf-state merge result))))

    ;; Wait until we have an access token
    (w/await
     (fn []
       (some? (:access_token @wf-state))))

    @(a/invoke persist-auth @wf-state)
    @wf-state))

;; Create authentication request for a user  ID, provider ID, provider name (:google etc) and a
;; list of scopes.
(defn start [ctx {:keys [user provider scopes] :as params}]
  (if (u/valid? ctx ::create params)
    (let [request-id (random-uuid)
          request {:db/doc-type :auth-request
                   :xt/id request-id
                   :auth-request/user user
                   :auth-request/provider provider
                   :auth-request/scopes scopes}
          
          state (request->state request)
          params (assoc params :state state)]

      (do
        ;; Persist auth request, then fire off workflow with the same ID
        (biff/submit-tx ctx [request])
        (workflow/trigger ctx authentication {:id request-id :params params})
        {:login-url (get-link ctx params)}))
    (throw+ 
     {:type ::invalid-params
      :cause (u/explain ctx ::create params)})))

(defn finish [ctx {:keys [code state] :as params}]
  (if (u/valid? ctx ::finish params)
    (let [request (state->request ctx state)
          id      (:xt/id request)]
      @(-> (workflow/trigger ctx authentication
                            {:id id
                             :signal ::callback
                             :signal-params {:code code
                                             :state state}})
          tc/get-result))
    (throw+ {:type ::invalid-params
             :explain (u/explain ctx ::finish params)})))

(def module
  {:schema schema})
