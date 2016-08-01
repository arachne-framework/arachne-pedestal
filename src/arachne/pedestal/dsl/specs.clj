(ns arachne.pedestal.dsl.specs
  (:require [clojure.spec :as s]
            [arachne.core.dsl.specs :as cspec]))

(s/fdef arachne.pedestal.dsl/interceptor
  :args (s/cat
          :path (s/? string?)
          :priority (s/? integer?)
          :identity (s/alt
                      :by-eid pos-int?
                      :by-arachne-id ::cspec/id)))