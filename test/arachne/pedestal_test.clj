(ns arachne.pedestal-test
  (:require [clojure.test :refer :all]
            [arachne.core :as core]
            [arachne.core.config :as cfg]
            [arachne.core.dsl :as a]
            [arachne.http.dsl :as h]
            [arachne.pedestal.dsl :as p]))

(defn basic-interceptors-cfg []

  (a/runtime :test/rt [:test/server])

  (a/component :test/i1 'test/ctor)
  (a/component :test/i2 'test/ctor)
  (a/component :test/i3 'test/ctor)

  (p/server :test/server 8080

    (p/interceptor :test/i1)

    (p/interceptor "a/b" :test/i2)
    (p/interceptor "a/b" :test/i3)

    (h/context "x/y"
      (p/interceptor (a/component :test/i4 'test/ctor)))))

(deftest basic-interceptors
  (let [cfg (core/build-config [:org.arachne-framework/arachne-pedestal]
              `(basic-interceptors-cfg))]

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