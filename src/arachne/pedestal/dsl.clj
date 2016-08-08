(ns arachne.pedestal.dsl
  (:require [arachne.pedestal.dsl.specs]
            [arachne.http.dsl :as http-dsl]
            [arachne.core.config :as cfg]
            [arachne.core.util :as util]
            [arachne.core.config.init :as script]
            [clojure.spec :as s]
            [clojure.string :as str]))

(defn interceptor
  "Define a Pedestal interceptor at the specified path."
  [& args]
  (apply util/validate-args `interceptor args)
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
