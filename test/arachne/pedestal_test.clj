(ns arachne.pedestal-test
  (:require [clojure.test :refer :all]
            [arachne.core :as core]
            [arachne.core.config :as cfg]))

(deftest basic-interceptors
  (let [cfg (core/build-config [:org.arachne-framework/arachne-pedestal]
              '(do
                 (require '[arachne.core.dsl :as core])
                 (require '[arachne.http.dsl :as http])
                 (require '[arachne.pedestal.dsl :as ped])

                 (core/runtime :test/rt [:test/server])

                 (core/component :test/i1 {} 'test/ctor)
                 (core/component :test/i2 {} 'test/ctor)
                 (core/component :test/i3 {} 'test/ctor)

                 (http/server :test/server 8080

                   (ped/interceptor :test/i1)

                   (ped/interceptor "a/b" :test/i2)
                   (ped/interceptor "a/b" :test/i3)

                   (http/context "x/y"
                     (ped/interceptor (core/component :test/i4 {} 'test/ctor))))
                 ))]

    (is (cfg/q cfg '[:find [?server]
                     :where

                     [?server :arachne.http.server/port 8080]

                     [?i1 :arachne/id :test/i1]
                     [?i1 :arachne.pedestal.interceptor/route ?server]
                     [?i1 :arachne.pedestal.interceptor/priority _]

                     [?b :arachne.http.route-segment/parent ?a]
                     [?a :arachne.http.route-segment/parent ?server]

                     [?i2 :arachne/id :test/i2]
                     [?i2 :arachne.pedestal.interceptor/route ?b]
                     [?i2 :arachne.pedestal.interceptor/priority ?i2-pri]

                     [?i3 :arachne/id :test/i3]
                     [?i3 :arachne.pedestal.interceptor/route ?b]
                     [?i3 :arachne.pedestal.interceptor/priority ?i3-pri]

                     [(< ?i2-pri ?i3-pri)]

                     [?y :arachne.http.route-segment/parent ?x]
                     [?x :arachne.http.route-segment/parent ?server]

                     [?i4 :arachne/id :test/i4]
                     [?i4 :arachne.pedestal.interceptor/route ?y]
                     [?i4 :arachne.pedestal.interceptor/priority _]]))))