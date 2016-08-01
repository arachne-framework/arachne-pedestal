(ns arachne.pedestal.schema
  (:require [arachne.core.config :refer [tempid]]
            [arachne.core.config.ontology :as o]))

(def schema

  (o/class :arachne.pedestal/Interceptor [:arachne/Component]
    "A Pedestal interceptor as part of an Arachne HTTP routing structure. The runtime value must satisfy io.pedestal.interceptor/IntoInterceptor"
    (o/attr :arachne.pedestal.interceptor/route :one :arachne.http/RouteSegment
      "The node in the routing tree to which attach the interceptor. The interceptor will be applied to all requests for this node and its descendants.")
    (o/attr :arachne.pedestal.interceptor/priority :one-or-none :long
      "The priority of the interceptor relative to other interceptors on the same route segment. Higher priority interceptors will be placed earlier on the interceptor chain. If priority is not present or equal, ordering will be arbitrary."))

  )
