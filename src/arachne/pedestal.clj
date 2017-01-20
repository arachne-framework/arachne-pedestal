(ns arachne.pedestal
  (:require [arachne.core.config :as cfg]
            [arachne.pedestal.config :as ped-cfg]
            [arachne.pedestal.server :as server]
            [arachne.pedestal.schema :as schema]
            [arachne.pedestal.specs]
            [io.pedestal.interceptor :as i]))

(defn ^:no-doc schema
  "Return the schema for the core module"
  []
  schema/schema)

(defn ^:no-doc configure
  "Configure the core module"
  [cfg]
  (-> cfg
    (ped-cfg/add-default-interceptors)
    (ped-cfg/add-endpoint-types)
    (ped-cfg/add-interceptor-default-ordering)))

(defrecord InterceptorComponent [name enter error leave]
  i/IntoInterceptor
  (-interceptor [component]
    (i/->Interceptor name
      (when enter (fn [ctx] (enter component ctx)))
      (when leave (fn [ctx] (leave component ctx)))
      (when error (fn [ctx ex] (error component ctx ex))))))

(defn component-interceptor
  "Takes a Pedestal-style interceptor map and returns a Pedestal IntoInterceptor.

  However, in the map passed to this function, each of the handler functions
  (for :enter, :leave and :error) takes an additional first argument, the
  component instance itself. This is intended to make it easy to write
  interceptors that have access to their dependencies.

  For example:

  (component-interceptor
    {:enter (fn [component ctx] ...)
     :leave (fn [component ctx] ...)
     :error (fn [component ctx ex] ...)})
  "
  [interceptor-map]
  (map->InterceptorComponent interceptor-map))