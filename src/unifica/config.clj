(ns unifica.config
  (:require [aero.core :as aero]
            [java-time.api :as jt]))

(defmethod aero/reader 'unifica/duration
  [_ _ value]
  (apply jt/duration value))
