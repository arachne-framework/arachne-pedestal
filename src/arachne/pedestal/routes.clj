(ns arachne.pedestal.routes
  (:require [arachne.core.config :as cfg]
            [arachne.core.util :as util]
            [arachne.error :as e :refer [error deferror]]
            [arachne.core.runtime :as rt]
            [arachne.http.config :as http-cfg]
            [arachne.http :as http]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.http.route :as r]
            [clojure.string :as str]
            [io.pedestal.interceptor :as i]))

(defn- endpoints
  "Return the EIDs of all endpoints that are children of a root route segment"
  [cfg root-eid]
  (cfg/q cfg '[:find [?e ...]
               :in $ % ?root
               :where
               (endpoints ?root ?e)]
    http-cfg/route-rules root-eid))

(defn- route-segments
  "Return an ordered list of segments between the root server and a given endpoint"
  [cfg eid]
  (reverse
    (take-while identity
      (iterate (fn [eid]
                 (cfg/attr cfg eid :arachne.http.route-segment/parent :db/id))
        (cfg/attr cfg eid :arachne.http.endpoint/route :db/id)))))


(defn- route-path
  "Build a route path , given an endpoint eid."
  [cfg endpoint]
  (let [segments (route-segments cfg endpoint)
        path (str/join "/"
              (for [seg segments]
                (let [s (cfg/pull cfg '[*] seg)]
                  (or (:arachne.http.route-segment/pattern s)
                    (:arachne.http.route-segment/param s)
                    (when (:arachne.http.route-segment/wildcard s) "*")))))]
    (if (str/blank? path)
      "/"
      path)))

(defn interceptors-for
  "Given a route segment eid, return a seq of the EIDs of its directly attached
  interceptors (in correct order)"
  [cfg eid]
  (let [is (:arachne.pedestal.interceptor/_route
             (cfg/pull cfg '[{:arachne.pedestal.interceptor/_route
                          [:db/id
                           (default :arachne.pedestal.interceptor/priority 0)]}]
             eid))]
    (->> is
      (sort-by :arachne.pedestal.interceptor/priority >)
      (map :db/id))))

(defn- interceptor-eids
  "Calculate the eids of interceptors that should be active for a route given an
  endpoint eid. These are interceptors attached to the endpoint route segment
  and each of its parent route segments (but not the root server)"
  [cfg endpoint]
  (let [segments (drop 1 (route-segments cfg endpoint))]
    (concat (mapcat #(interceptors-for cfg %) segments) [endpoint])))

(deferror ::invalid-interceptor
  :message "Component with class `:class` cannot be used as Interceptor."
  :explanation "The config indicated that a component with entity ID `:eid` (Arachne ID: `:aid`) should be used as an Pedestal Interceptor. However, the component was of class `:class`, which the system does not know how to use as an interceptor."
  :suggestions ["Ensure that the component instance satisfies either `arachne.http/Handler` or `io.pedestal.interceptor/IntoInterceptor`"]
  :ex-data-docs {:eid "The component's entity ID"
                 :aid "The component's Arachne ID"
                 :class "The class of the component instance"})

(defn interceptor
  "Given a component instance, coerce it to a Pedestal interceptor"
  [cfg component-eid component]
  (cond
    (satisfies? arachne.http/Handler component) (i/interceptor #(http/handle component %))
    (satisfies? i/IntoInterceptor component) (i/interceptor component)
    :else (error ::invalid-interceptor
            {:eid component-eid
             :aid (cfg/attr cfg component-eid :arachne/id)
             :class (class component)})))

(defn- route
  "Return a seq of Pedestal route maps for the given endpoint EID"
  [router cfg eid]
  (for [method (cfg/attr cfg eid :arachne.http.endpoint/methods)]
    {:route-name (cfg/attr cfg eid :arachne.http.endpoint/name)
     :method method
     :path (route-path cfg eid)
     :interceptors (map #(interceptor cfg % (get router %))
                     (interceptor-eids cfg eid))}))

(defn- server-router
  "Find server object associated with a given router"
  [cfg router-eid]
  (cfg/attr cfg router-eid :arachne.pedestal.interceptor/route :db/id))

(defn routes
  "Given a Router component, an Arachne config and the entity ID of a
  RouteSegment, return a Pedestal ExpandableRoutes object representing the
  routing structure of the segment and all its children."
  [router cfg eid]
  (reify r/ExpandableRoutes
    (-expand-routes [_]
      (mapcat #(route router cfg %) (endpoints cfg eid)))))

(defrecord Router [cfg eid]
  interceptor/IntoInterceptor
  (-interceptor [this]
    (r/router (r/expand-routes (routes this cfg (server-router cfg eid)))
      :map-tree)))
