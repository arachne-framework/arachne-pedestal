(ns arachne.pedestal
  (:require [arachne.core.util :as util]
            [arachne.pedestal.schema :as schema]))

(defprotocol Handler
  (handler [this] "Return a Ring-style request handler function"))

(defn schema
  "Return the schema for the core module"
  []
  schema/schema)

(defn configure
  "Configure the core module"
  [cfg]
  cfg  )
