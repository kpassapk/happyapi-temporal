(ns happyapi.temporal.utils
(:require [temporal.client.core :as tc]
          [clojure.core.protocols :as p]
          [malli.core :as malc]
          [malli.error :as male])
(:import
   (io.temporal.api.common.v1 WorkflowExecution)))

(extend-protocol p/Datafiable
  WorkflowExecution
  (datafy [d]
    {::id     (parse-uuid (.getWorkflowId d))
     ::run-id (parse-uuid (.getRunId d))}))

(defn trigger
  "Starts a workflow.
   Options:

  - id:     workflow ID
  - signal: signal name
  - params: workflow parameters
  - signal-params: signal parameters

  Any other options will be passed to create-workflow."
  [{:temporal/keys [client workflow-options] :as ctx}
   workflow
   &
   {:keys [id params signal signal-params]
    :or {params        {}
         signal-params {}} :as options}]
  (let [wf-options (-> workflow-options
                    (merge (dissoc options :id :params :signal :signal-params))
                    (cond-> id
                      (assoc :workflow-id (str id))))
        stub (tc/create-workflow client workflow wf-options)
        execution (if signal
                    (tc/signal-with-start stub signal signal-params params)
                    (tc/start             stub params))]

    (merge stub (p/datafy execution))))

(defn valid? [{:keys [biff/malli-opts] :as ctx} schema m]
  (malc/validate schema m @malli-opts))

(defn explain [{:keys [biff/malli-opts]} schema m]
  (-> schema
      (malc/explain m @malli-opts)
      (male/with-spell-checking)
      (male/humanize)))
