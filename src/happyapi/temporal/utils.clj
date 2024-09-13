(ns happyapi.temporal.utils
(:require [temporal.client.core :as tc]
          [clojure.core.protocols :as p]
          [malli.core :as malc]
          [malli.error :as male]))

(defn valid? [{:keys [biff/malli-opts] :as ctx} schema m]
  (malc/validate schema m @malli-opts))

(defn explain [{:keys [biff/malli-opts]} schema m]
  (-> schema
      (malc/explain m @malli-opts)
      (male/with-spell-checking)
      (male/humanize)))
