(ns arachne.pedestal
  (:require [arachne.core.config :as cfg]
            [arachne.pedestal.config :as ped-cfg]
            [arachne.pedestal.server :as server]
            [arachne.pedestal.schema :as schema]
            [arachne.pedestal.specs]))

(defn schema
  "Return the schema for the core module"
  []
  schema/schema)

(defn configure
  "Configure the core module"
  [cfg]
  (-> cfg
    (ped-cfg/add-default-interceptors)
    (ped-cfg/add-endpoint-types)))
