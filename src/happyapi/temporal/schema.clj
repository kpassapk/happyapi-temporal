(ns happyapi.temporal.schema)

(def schema
  {:user/id :uuid
   :user [:map {:closed true}
          [:xt/id                     :user/id]
          [:user/email                :string]
          [:user/joined-at            inst?]]

   :auth-request/id :uuid
   :auth-request [:map {:closed true}
                  [:xt/id :auth-request/id]
                  [:auth-request/user :user/id]
                  [:auth-request/provider :keyword]
                  [:auth-request/scopes [:vector :string]]]

   :auth/id ::uuid
   :auth [:map {:closed true}
          [:xt/id              :auth/id]
          [:auth/request       :auth-request/id]
          [:auth/provider      :keyword]
          [:auth/user          :user/id]
          [:auth/access_token  :string]
          [:auth/refresh_token :string]
          [:auth/scope         :string]
          [:auth/token_type    :string]
          [:auth/expires_at    inst?]]

   })

(def module
  {:schema schema})
