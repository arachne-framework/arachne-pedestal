(ns arachne.pedestal.config
  (:require [arachne.core.config :as cfg]
            [arachne.core.config.model :as ont]
            [arachne.core.util :as util]
            [arachne.http.config :as http-cfg]
            [arachne.pedestal.server :as server]
            [arachne.pedestal.routes :as routes]
            [io.pedestal.http.route :as ped-route]
            [io.pedestal.interceptor :as i]))

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
        [(util/mkeep
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
      [{:arachne.pedestal.interceptor/route server-eid
        :arachne.pedestal.interceptor/priority 10
        :arachne.component/instance :io.pedestal.http/not-found}
       {:arachne.pedestal.interceptor/route server-eid
        :arachne.pedestal.interceptor/priority 9
        :arachne.component/instance :io.pedestal.http/log-request}
       {:arachne.pedestal.interceptor/route server-eid
        :arachne.pedestal.interceptor/priority 8
        :arachne.component/constructor :arachne.pedestal.server/content-type-interceptor}
       {:arachne.pedestal.interceptor/route server-eid
        :arachne.pedestal.interceptor/priority 7
        :arachne.component/instance :io.pedestal.http.route/query-params}
       {:arachne.pedestal.interceptor/route server-eid
        :arachne.pedestal.interceptor/priority 6
        :arachne.component/constructor :arachne.pedestal.server/method-param-interceptor}])))

(defn add-server-interceptor-deps
  "Ensure that the server has a component dependency on all of its directly
  attached interceptors. Interceptors attached to the routing table are
  dependencies of that (not the server directly)."
  [cfg server-eid]
  (let [root-interceptors (cfg/q cfg
                            '[:find [?i ...]
                              :in $ ?server
                              :where
                              [?i :arachne.pedestal.interceptor/route ?server]]
                            server-eid)]
    (cfg/with-provenance :module `add-server-interceptor-deps
      (cfg/update cfg
        [{:db/id server-eid
          :arachne.component/dependencies (for [i root-interceptors]
                                            {:arachne.component.dependency/entity i})}]))))

(defn add-default-interceptors
  "Explicitly add the default interceptors to each server entity"
  [cfg]
  (reduce (fn [cfg server-eid]
            (-> cfg
              (add-standard-interceptors server-eid)
              (add-router-interceptor server-eid)
              (add-server-interceptor-deps server-eid)))
    cfg
    (http-cfg/servers cfg)))

(defn add-endpoint-types
  "Finds all endpoints associated with a Pedestal server and asserts that they
  are of the Pedestal endpoint class"
  [cfg]
  (let [endpoints (cfg/q cfg '[:find [?endpoint ...]
                               :in $ %
                               :where
                               [?class :db/ident :arachne.pedestal/Server]
                               (type ?class ?server)
                               (endpoints ?server ?endpoint)]
                    (concat ont/rules http-cfg/route-rules))]
    (if (empty? endpoints)
      cfg
      (cfg/with-provenance :module `add-endpoint-types
        (cfg/update cfg (for [e endpoints]
                          {:db/id e
                           :arachne/instance-of
                           {:db/ident :arachne.pedestal/Endpoint}}))))))

(defn add-interceptor-default-ordering
  "Assigns a default ordering to all interceptors that don't have an explicit order.

   The assigned order is based on the tx, and therefore should correspond to lexical
   order in which interceptors were declared in a configuration file."
  [cfg]
  (let [interceptors (set (cfg/q cfg '[:find ?segment ?interceptor ?tx
                                       :in $
                                       :where
                                       [?interceptor :arachne.pedestal.interceptor/route ?segment ?tx]
                                       [(missing? $ ?interceptor :arachne.pedestal.interceptor/priority)]]))
        groups (vals (group-by first interceptors))
        txdata (mapcat (fn [group]
                         (let [in-order (->> group
                                          (map #(drop 1 %))
                                          (sort-by second)
                                          (map first))]
                           (map (fn [eid priority]
                                  [:db/add eid :arachne.pedestal.interceptor/priority priority])
                             in-order (iterate inc -10000)))) groups)]

    (if (empty? txdata)
      cfg
      (cfg/with-provenance :module `add-interceptor-default-ordering
        (cfg/update cfg txdata)))))

