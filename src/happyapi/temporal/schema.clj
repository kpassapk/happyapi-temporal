(ns happyapi.temporal.schema)

(def schema
  {:user/id :uuid
   :user [:map {:closed true}
          [:xt/id                     :user/id]
          [:user/email                :string]
          [:user/joined-at            inst?]]

   :auth/id :uuid
   :auth [:map {:closed true}
          [:xt/id              :auth/id]
          [:auth/provider      :keyword]
          [:auth/user          :user/id]
          [:auth/access_token  :string]
          [:auth/refresh_token :string]
          [:auth/scope         :string]
          [:auth/token_type    :string]
          [:auth/created_at    inst?]
          [:auth/expires_at    inst?]]})

(def module
  {:schema schema})
