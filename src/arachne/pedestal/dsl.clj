(ns arachne.pedestal.dsl
  (:require [arachne.pedestal.dsl.specs]
            [arachne.http.dsl :as http-dsl]
            [arachne.core.config :as cfg]
            [arachne.core.util :as util]
            [arachne.core.config.init :as script :refer [defdsl]]
            [clojure.spec :as s]
            [clojure.string :as str]))

(defdsl create-server
  "Define an Pedestal HTTP server entity with the given Arachne ID and port. Return
  the tempid of the new server."
  [arachne-id port]
  (let [server-tid (cfg/tempid)
        new-cfg (script/transact
                  [{:db/id server-tid
                    :arachne/id arachne-id
                    :arachne/instance-of {:db/ident :arachne.pedestal/Server}
                    :arachne.component/constructor :arachne.pedestal.server/constructor
                    :arachne.http.server/port port}])]
    (cfg/resolve-tempid new-cfg server-tid)))

(defmacro server
  "Define a Pedestal HTTP server in the current configuration. Evaluates the body with
  the server bound as the context server. Returns the eid of the Server
  component."
  [arachne-id port & body]
  (apply util/validate-args `server arachne-id port body)
  `(let [server-eid# (create-server ~arachne-id ~port)]
     (binding [http-dsl/*context-server* server-eid#]
       ~@body)
     server-eid#))

(defdsl interceptor
  "Define a Pedestal interceptor at the specified path."
  [& args]
  (let [conformed (s/conform (:args (s/get-spec `interceptor)) args)
        path (http-dsl/with-context (or (:path conformed) ""))
        id (-> conformed :identity val)
        arachne-id (when (keyword? id) id)
        eid (when (pos-int? id) id)
        tid (cfg/tempid)
        segment (http-dsl/ensure-path path)
        priority (or (:priority conformed)
                   ((fnil inc 0)
                     (cfg/q @script/*config*
                       '[:find (max ?p) .
                         :in $ ?segment
                         :where
                         [?i :arachne.pedestal.interceptor/route ?segment]
                         [?i :arachne.pedestal.interceptor/priority ?p]]
                       segment)))
        txdata (util/mkeep
                 {:db/id (or eid tid)
                  :arachne/id arachne-id
                  :arachne.pedestal.interceptor/route segment
                  :arachne.pedestal.interceptor/priority priority})
        new-cfg (script/transact [txdata])]
    (or eid (cfg/resolve-tempid new-cfg tid))))
