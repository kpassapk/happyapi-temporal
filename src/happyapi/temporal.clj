(ns happyapi.temporal
  (:require
   [clojure.test :as test]
   [clojure.tools.logging :as log]
   [clojure.tools.namespace.repl :as tn-repl]
   [com.biffweb :as biff]
   [happyapi.temporal.config :as config]
   [happyapi.temporal.home :as home]
   [happyapi.temporal.middleware :as mid]
   [happyapi.temporal.schema :as schema]
   [happyapi.temporal.workflows.auth :as auth]
   [happyapi.temporal.ui :as ui]
   [malli.core :as malc]
   [malli.registry :as malr]
   [nrepl.cmdline :as nrepl-cmd]
   [temporal.activity :as a]
   [unifica.temporal :as temporal]
   [happyapi.setup :as happyapi]
   [happyapi.oauth2.client :as oauth2])
  (:gen-class))

(def modules
  [home/module
   schema/module
   auth/module])

(def routes [["" {:middleware [mid/wrap-site-defaults]}
              (keep :routes modules)]
             ["" {:middleware [mid/wrap-api-defaults]}
              (keep :api-routes modules)]])

(def handler (-> (biff/reitit-handler {:routes routes})
                 mid/wrap-base-defaults))

(def static-pages (apply biff/safe-merge (map :static modules)))

(defn generate-assets! [ctx]
  (biff/export-rum static-pages "target/resources/public")
  (biff/delete-old-files {:dir "target/resources/public"
                          :exts [".html"]}))

(defn on-save [ctx]
  (biff/add-libs)
  (biff/eval-files! ctx)
  (generate-assets! ctx)
  (test/run-all-tests #"happyapi.temporal.*-test"))

(def malli-opts
  {:registry (malr/composite-registry
              malc/default-registry
              (apply biff/safe-merge (keep :schema modules)))})

;; Load happyapi config
(defn use-happyapi-config
  "Expand happyapi configuration"
  [{:keys [happyapi/config] :as ctx}]
  (let [expand (fn [provider cfg]
                 [provider (-> cfg
                               (update :provider #(or % provider))
                               happyapi/with-deps
                               happyapi/resolve-fns
                               oauth2/with-endpoints)])
        provider-cofigs (->> (for [provider-config config]
                               (->> provider-config
                                   (apply expand)
                                   (apply config/verify-happyapi-config)))
                             (into {}))]
    (assoc ctx :happyapi/config provider-cofigs)))

(defn- get-user-fn [{:keys [session]}]
  (:uid session))

(def initial-system
  {:biff/modules #'modules
   :biff/handler #'handler
   :biff/malli-opts #'malli-opts
   :biff.beholder/on-save #'on-save
   :biff.middleware/on-error #'ui/on-error
   :biff.xtdb/tx-fns biff/tx-fns
   :app/get-user-fn #'get-user-fn
   :temporal.activity/get-info #'a/get-info})

(def worker-options
  [{:task-queue :unifica.workflows/queue}])

(defonce system (atom {}))

(def components
  [biff/use-aero-config
   biff/use-xtdb
   use-happyapi-config
   biff/use-xtdb-tx-listener
   biff/use-htmx-refresh
   (temporal/use-temporal {:worker-options worker-options})
   biff/use-jetty
   biff/use-chime
   biff/use-beholder])

(defn start []
  (let [new-system (reduce (fn [system component]
                             (log/info "starting:" (str component))
                             (component system))
                           initial-system
                           components)]
    (reset! system new-system)
    (generate-assets! new-system)
    (log/info "System started.")
    (log/info "Go to" (:biff/base-url new-system))
    new-system))

(defn -main []
  (let [{:keys [biff.nrepl/args]} (start)]
    (apply nrepl-cmd/-main args)))

(defn refresh []
  (doseq [f (:biff/stop @system)]
    (log/info "stopping:" (str f))
    (f))
  (tn-repl/refresh :after `start)
  :done)
