(ns arachne.pedestal.config
  (:require [arachne.core.config :as cfg]
            [arachne.core.util :as util]
            [arachne.http.config :as http-cfg]
            [arachne.pedestal.server :as server]
            [arachne.pedestal.routes :as routes]
            [io.pedestal.http.route :as ped-route]
            [io.pedestal.interceptor :as i]))

(defn add-server-constructors
  "Add the correct constructor to server entities"
  [cfg]
  (cfg/with-provenance :module `add-server-constructors
    (reduce (fn [cfg server-eid]
              (cfg/update cfg [{:db/id server-eid
                                :arachne.component/constructor
                                (keyword `server/constructor)}]))
      cfg (http-cfg/servers cfg))))

(def interceptor-rules
  "Datalog rules to find all interceptors attached to a route and any of its children"
  (concat http-cfg/route-rules
    '[[(interceptors ?route ?interceptor)
       [?interceptor :arachne.pedestal.interceptor/route ?route]]

      [(interceptors ?ancestor ?interceptor)
       (routes ?ancestor ?route)
       [?interceptor :arachne.pedestal.interceptor/route ?route]]]))

(defn find-route-interceptors
  "Find interceptors in the routing table for a server (but not to the server
  itself)"
  [cfg server-eid]
  (cfg/q cfg '[:find [?interceptor ...]
               :in $ ?server %
               :where
               [?segment :arachne.http.route-segment/parent ?server]
               (interceptors ?segment ?interceptor)]
    server-eid interceptor-rules))

(defn- add-router-interceptor
  "Create a router interceptor, attached to the given server eid, with
  dependencies on all child endpoints and interceptors"
  [cfg server-eid]
  (let [endpoints (http-cfg/find-endpoints cfg server-eid)
        interceptors (find-route-interceptors cfg server-eid)
        deps (for [dep (concat endpoints interceptors)]
               {:arachne.component.dependency/entity dep})
        router-eid (cfg/tempid)]
    (cfg/with-provenance :module `add-router-interceptor
      (cfg/update cfg
        [{:db/id server-eid
          :arachne.component/dependencies
          {:arachne.component.dependency/entity router-eid
           :arachne.component.dependency/key ::router}}
         (util/mkeep
           {:db/id router-eid
            :arachne.pedestal.interceptor/route server-eid
            :arachne.pedestal.interceptor/priority 5
            :arachne.component/constructor :arachne.pedestal.routes/->Router
            :arachne.component/dependencies deps})]))))

(defn- add-standard-interceptors
  "Add the standard default interceptors.

  Note: a lot of these should probably be more configurable. This is
  straightforward to do, just read the config first."
  [cfg server-eid]
  (cfg/with-provenance :module `add-standard-interceptors
    (cfg/update cfg
      [{:db/id server-eid
        :arachne.component/dependencies
        [{:arachne.component.dependency/entity (cfg/tempid -1)}
         {:arachne.component.dependency/entity (cfg/tempid -2)}
         {:arachne.component.dependency/entity (cfg/tempid -3)}
         {:arachne.component.dependency/entity (cfg/tempid -4)}
         {:arachne.component.dependency/entity (cfg/tempid -5)}]}
       {:db/id (cfg/tempid -1)
        :arachne.pedestal.interceptor/route server-eid
        :arachne.pedestal.interceptor/priority 10
        :arachne.component/instance :io.pedestal.http/not-found}
       {:db/id (cfg/tempid -2)
        :arachne.pedestal.interceptor/route server-eid
        :arachne.pedestal.interceptor/priority 9
        :arachne.component/instance :io.pedestal.http/log-request}
       {:db/id (cfg/tempid -3)
        :arachne.pedestal.interceptor/route server-eid
        :arachne.pedestal.interceptor/priority 8
        :arachne.component/constructor :arachne.pedestal.server/content-type-interceptor}
       {:db/id (cfg/tempid -4)
        :arachne.pedestal.interceptor/route server-eid
        :arachne.pedestal.interceptor/priority 7
        :arachne.component/instance :io.pedestal.http.route/query-params}
       {:db/id (cfg/tempid -5)
        :arachne.pedestal.interceptor/route server-eid
        :arachne.pedestal.interceptor/priority 6
        :arachne.component/constructor :arachne.pedestal.server/method-param-interceptor}])))

(defn add-default-interceptors
  "Explicitly add the default interceptors to each server entity"
  [cfg]
  (reduce (fn [cfg server-eid]
            (-> cfg
              (add-standard-interceptors server-eid)
              (add-router-interceptor server-eid)))
    cfg
    (http-cfg/servers cfg)))
