(ns arachne.pedestal.routes
  (:require [arachne.core.config :as cfg]
            [arachne.core.util :as util]
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

(util/deferror ::cannot-coerce-to-interceptor "Could not convert component with class :class to an interceptor; it did not satisfy arachne.http/Handler or io.pedestal.interceptor/IntoInterceptor.")

(defn interceptor
  "Given a component instance, coerce it to a Pedestal interceptor"
  [obj]
  (cond
    (satisfies? arachne.http/Handler obj) (i/interceptor #(http/handle obj %))
    (satisfies? i/IntoInterceptor obj) (i/interceptor obj)
    :else (util/error ::cannot-coerce-to-interceptor {:class (class obj)})))

(defn- route
  "Return a seq of Pedestal route maps for the given endpoint EID"
  [router cfg eid]
  (for [method (cfg/attr cfg eid :arachne.http.endpoint/methods)]
    {:route-name (cfg/attr cfg eid :arachne.http.endpoint/name)
     :method method
     :path (route-path cfg eid)
     :interceptors (->> (interceptor-eids cfg eid)
                     (map #(rt/dependency-instance router cfg %))
                     (map interceptor))}))

(defn- server-router
  "Find server object associated with a given router"
  [cfg router-eid]
  (cfg/q cfg '[:find ?s .
               :in $ ?r
               :where
               [?d :arachne.component.dependency/entity ?r]
               [?d :arachne.component.dependency/key :arachne.pedestal.config/router]
               [?s :arachne.component/dependencies ?d]] router-eid))

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
