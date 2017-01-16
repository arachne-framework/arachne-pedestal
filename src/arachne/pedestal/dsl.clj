(ns arachne.pedestal.dsl
  (:require [arachne.core.dsl :as core-dsl]
            [arachne.http.dsl :as http-dsl]
            [arachne.core.config :as cfg]
            [arachne.core.util :as util]
            [arachne.error :as e]
            [arachne.core.config.script :as script :refer [defdsl]]
            [clojure.spec :as s]
            [clojure.string :as str]))

(defdsl create-server
  "Define an Pedestal HTTP server entity with the given Arachne ID and port. Return
  the tempid of the new server."
  (s/cat :arachne-id ::core-dsl/arachne-id
         :port integer?)
  [arachne-id port]
  (let [server-tid (cfg/tempid)]
    (script/transact
      [{:db/id server-tid
        :arachne/id arachne-id
        :arachne/instance-of {:db/ident :arachne.pedestal/Server}
        :arachne.component/constructor :arachne.pedestal.server/constructor
        :arachne.http.server/port port}]
      server-tid)))

(s/fdef server
  :args (s/cat :arachne-id ::core-dsl/arachne-id
          :port integer?
          :body (s/* any?)))

(defmacro server
  "Define a Pedestal HTTP server in the current configuration. Evaluates the body with
  the server bound as the context server. Returns the eid of the Server
  component."
  [arachne-id port & body]
  (apply e/assert-args `server arachne-id port body)
  `(let [server-eid# (create-server ~arachne-id ~port)]
     (binding [http-dsl/*context-server* server-eid#]
       ~@body)
     server-eid#))

(s/def ::priority integer?)

(defdsl interceptor
  "Define a component to be a Pedestal interceptor attached to the specified path.

  Arguments are:

  - path (optional) - the path to attach the interceptor (defaults to '/' in the current context)
  - component (mandatory) - the Arachne component. The runtime instance of the component must satisfy
    Pedestal's IntoInterceptor protocol.
  - options (optional) - A map (or kwargs) of additional options.

  Currently supported options are:

  - priority (optional) - the priority relative to other interceptors defined at the same path. If omitted,
    defaults to the lexical order of the config script"

  (s/cat
    :path (s/? string?)
    :component ::core-dsl/ref
    :opts (util/keys** :opt-un [::priority]))
  [<path> component & opts]
  (let [path (http-dsl/with-context (or (:path &args) ""))
        eid (core-dsl/resolved-ref (:component &args))
        segment (http-dsl/ensure-path path)
        priority (-> &args :opts second :priority)
        entity (util/mkeep
                 {:db/id eid
                  :arachne.pedestal.interceptor/route segment
                  :arachne.pedestal.interceptor/priority priority})]
    (script/transact [entity])
    eid))
