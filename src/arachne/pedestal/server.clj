(ns arachne.pedestal.server
  (:require [arachne.pedestal.routes :as routes]
            [arachne.core.runtime :as rt]
            [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.http.route :as ped-route]
            [io.pedestal.http.ring-middlewares :as middlewares]))

(defn- content-type-interceptor
  "Build the default content-type interceptor"
  []
  (middlewares/content-type {:mime-types {}}))

(defn- method-param-interceptor
  "Build the method param interceptor for verb smuggling"
  []
  (ped-route/method-param))

(defn- service-map
  "Build a new a service map for the given Server component"
  [cfg eid server]
  {::http/type :jetty
   ::http/join? false
   ::http/interceptors (->> (routes/interceptors-for cfg eid)
                            (map #(rt/dependency-instance server cfg %))
                            (map routes/interceptor))
   ::http/port (:arachne.http.server/port server)})

(defrecord Server [cfg eid server]
  component/Lifecycle
  (start [this]
    (assoc this :server (http/start
                          (http/create-server (service-map cfg eid this)))))
  (stop [this]
    (when server (http/stop server))
    (assoc this :server nil)))

(defn constructor
  "Component constructor for a Pedestal server"
  [cfg eid]
  (->Server cfg eid nil))
