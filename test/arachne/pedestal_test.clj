(ns arachne.pedestal-test
  (:require [clojure.test :refer :all]
            [arachne.core :as core]
            [arachne.core.config :as cfg]
            [arachne.core.dsl :as a]
            [arachne.http.dsl :as h]
            [arachne.pedestal.dsl :as p]
            [arachne.pedestal.routes :as routes]
            [io.pedestal.http.route :as http.route]
            [com.stuartsierra.component :as component]))

(defn basic-interceptors-cfg []

  (a/id :test/rt (a/runtime [:test/server]))

  (a/id :test/i1 (a/component 'test/ctor))
  (a/id :test/i2 (a/component 'test/ctor))
  (a/id :test/i3 (a/component 'test/ctor))

  (a/id :test/server
    (p/server 8080

      (p/interceptor :test/i1)

      (p/interceptor "a/b" :test/i2)
      (p/interceptor "a/b" :test/i3)

      (h/context "x/y"
        (p/interceptor (a/id :test/i4 (a/component 'test/ctor)))))))

(deftest basic-interceptors
  (let [cfg (core/build-config [:org.arachne-framework/arachne-pedestal]
              `(basic-interceptors-cfg) false)]
    (is (cfg/q cfg '[:find [?server]
                     :where

                     [?server :arachne.http.server/port 8080]

                     [?i1 :arachne.pedestal.interceptor/component ?c1]
                     [?c1 :arachne/id :test/i1]
                     [?i1 :arachne.pedestal.interceptor/route ?server]
                     [?i1 :arachne.pedestal.interceptor/priority _]

                     [?b :arachne.http.route-segment/parent ?a]
                     [?a :arachne.http.route-segment/parent ?server]

                     [?c2 :arachne/id :test/i2]
                     [?i2 :arachne.pedestal.interceptor/component ?c2]
                     [?i2 :arachne.pedestal.interceptor/route ?b]
                     [?i2 :arachne.pedestal.interceptor/priority ?i2-pri]

                     [?c3 :arachne/id :test/i3]
                     [?i3 :arachne.pedestal.interceptor/component ?c3]
                     [?i3 :arachne.pedestal.interceptor/route ?b]
                     [?i3 :arachne.pedestal.interceptor/priority ?i3-pri]

                     [(< ?i3-pri ?i2-pri)]

                     [?y :arachne.http.route-segment/parent ?x]
                     [?x :arachne.http.route-segment/parent ?server]

                     [?c4 :arachne/id :test/i4]
                     [?i4 :arachne.pedestal.interceptor/component ?c4]
                     [?i4 :arachne.pedestal.interceptor/route ?y]
                     [?i4 :arachne.pedestal.interceptor/priority _]]))))

(defn interceptor-priority-cfg []

  (a/id :test/rt (a/runtime [:test/server]))

  (a/id :test/server
    (p/server 8080

      (p/interceptor (a/id :test/a1 (a/component 'test/ctor)))
      (p/interceptor (a/id :test/a2 (a/component 'test/ctor)))
      (p/interceptor (a/id :test/a3 (a/component 'test/ctor)) :priority 1)
      (p/interceptor (a/id :test/a4 (a/component 'test/ctor)) :priority 2)

      (h/context "foo"

        (p/interceptor (a/id :test/b1 (a/component 'test/ctor)))
        (p/interceptor (a/id :test/b2 (a/component 'test/ctor)))
        (p/interceptor (a/id :test/b3 (a/component 'test/ctor)) :priority 1)
        (p/interceptor (a/id :test/b4 (a/component 'test/ctor)) :priority 2)))))

(deftest interceptor-priority
  (let [cfg (core/build-config [:org.arachne-framework/arachne-pedestal]
              `(interceptor-priority-cfg))
        pri (fn [aid]
              (cfg/q cfg '[:find ?pri .
                           :in $ ?aid
                           :where
                           [?c :arachne/id ?aid]
                           [?i :arachne.pedestal.interceptor/component ?c]
                           [?i :arachne.pedestal.interceptor/priority ?pri]] aid))]

    (is (= [:test/a2 :test/a1 :test/a3 :test/a4]
           (sort-by pri [:test/a1 :test/a2 :test/a3 :test/a4])))

    (is (= [:test/b2 :test/b1 :test/b3 :test/b4]
          (sort-by pri [:test/b1 :test/b2 :test/b3 :test/b4])))

    (let [roots (cfg/q cfg '[:find [?i ...]
                             :where
                             [?s :arachne/id :test/server]
                             [?i :arachne.pedestal.interceptor/route ?s]])
          roots (map #(cfg/pull cfg '[*] %) roots)
          built-in-roots (filter #(not (:arachne/id %)) roots)]

      (for [root built-in-roots]
        (is (< (:arachne.pedestal.interceptor/priority root) (pri :test/a2)))))))

(defn handler-url-for [_])

(defn url-for-cfg []
  (a/id :test/rt (a/runtime [:test/server]))
  (a/id :test/server
    (p/server 8080
      (h/endpoint :get "/foo/:a/baz" (h/handler `handler-url-for)))))

(deftest url-for-test
  (let [cfg (core/build-config [:org.arachne-framework/arachne-pedestal]
                               `(url-for-cfg))
        url-for (http.route/url-for-routes (routes/routes cfg [:arachne/id :test/server]))]
    (is (= "/foo/bar/baz" (url-for ::handler-url-for :path-params {:a "bar"})))))

(defn handler []
  {:status 200
   :body "OK"})

(defn multiple-method-config []

  (a/id :test/rt (a/runtime [:test/server]))

  (a/id :test/handler (h/handler `handler))

  (a/id :test/server
    (p/server 8080

      (h/endpoint #{:post :put} "/foo" :test/handler)

      )))

(deftest multiple-method-test
  (let [cfg (core/build-config [:org.arachne-framework/arachne-pedestal]
              `(multiple-method-config) true)
        routes (routes/routes cfg [:arachne/id :test/server])
        url-for (http.route/url-for-routes routes)]

    (is (= "/foo" (url-for :test/handler-post)))
    (is (= "/foo" (url-for :test/handler-put)))))