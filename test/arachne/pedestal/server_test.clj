(ns arachne.pedestal.server-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [io.pedestal.http.route :as r]
            [com.stuartsierra.component :as c]
            [arachne.core :as core]
            [arachne.http :as http]
            [arachne.core.config :as cfg]
            [arachne.core.runtime :as rt]
            [arachne.pedestal :as ped]
            [ring.util.response :as ring-resp]
            [clj-http.client :as client]
            [arachne.core.dsl :as a]
            [arachne.http.dsl :as h]
            [arachne.pedestal.dsl :as p]))

(defn hello-world
  [request]
  (ring-resp/response "Hello, world!")
  )

(defn hello-world-handler
  "Constructor for test interceptor"
  []
  hello-world)

(defn a-interceptor
  []
  {:leave (fn [ctx]
            (update-in ctx [:response :body]
              #(str/replace % "world" "Luke")))})

(defn handler
  "Sample request handler"
  [req]
  (ring-resp/response "Hello from a handler!"))

(defn hello-world-server-cfg []

  (a/id :test/rt (a/runtime [:test/server]))

  (a/id :test/server
    (p/server 8080
      (h/endpoint :get "/" (a/component `hello-world-handler) :name :hello-world)
      (h/endpoint :get "/handler" (h/handler `handler))
      (h/context "a"
        (p/interceptor (a/component `a-interceptor))
        (h/endpoint :get "b" (a/component `hello-world-handler) :name :hello-world-b)))))

(deftest ^:integration hello-world-server
  (let [cfg (core/build-config [:org.arachne-framework/arachne-pedestal]
              `(hello-world-server-cfg))
        rt (atom (rt/init cfg [:arachne/id :test/rt]))]
    (try
      (swap! rt c/start)
      (is (= "Hello, world!" (slurp "http://localhost:8080")))
      (is (= "Hello from a handler!" (slurp "http://localhost:8080/handler")))
      (is (= "Hello, Luke!" (slurp "http://localhost:8080/a/b")))
      (let [result (try (client/get "http://localhost:8080/no-such-url")
                        (catch Exception e (ex-data e)))]
        (is (= 404 (:status result)))
        (is (= "Not Found" (:body result))))
      (finally
        (swap! rt c/stop)))))


(defn invalid-handler
  []
  (java.util.Date.))

(defn port-expression-cfg []
  (a/id :test/rt (a/runtime [:test/server]))
  (a/id :test/server (p/server (do 8080)
                       (h/endpoint :get "/"
                         (a/component `hello-world-handler) :name :hello-world))))

(defn failing-port-type-cfg []
  (a/id :test/rt (a/runtime [:test/server]))
  (a/id :test/server (p/server "8080"
                       (h/endpoint :get "/"
                         (a/component `hello-world-handler) :name :hello-world))))

(deftest port-expression-server
  (is (core/build-config [:org.arachne-framework/arachne-pedestal]
        `(port-expression-cfg)))
  (is (thrown? arachne.ArachneException
        (core/build-config [:org.arachne-framework/arachne-pedestal]
          `(failing-port-type-cfg)))))

(defn endpoint-validity-cfg []
  (a/id :test/rt (a/runtime [:test/server]))
  (a/id :test/invalid-handler (a/component `invalid-handler))
  (a/id :test/server (p/server 8080
                       (h/endpoint :get "/" :test/invalid-handler))))

(deftest ^:integration endpoint-validity
  (let [cfg (core/build-config [:org.arachne-framework/arachne-pedestal]
              `(endpoint-validity-cfg))
        rt (atom (rt/init cfg [:arachne/id :test/rt]))]
    (try
      (is (thrown-with-msg? Throwable #"Error in component"
            (swap! rt c/start)))

      ;(println "result:" (slurp "http://localhost:8080"))

      (finally
        (swap! rt c/stop)))))

(comment
  (def cfg arachne.pedestal.routes/*cfg*)

  (clojure.pprint/pprint
    (cfg/pull cfg '[*] 17592186045440))

  (@#'arachne.pedestal.routes/interceptors-for cfg 17592186045440)


  (cfg/pull cfg '[*] 17592186045450)


  (arachne.pedestal.routes/routes cfg 17592186045440)

  )

(defn root-interceptor
  []
  {:leave (fn [ctx]
            (assoc ctx :response {:status 200
                                  :body "intercepted!"}))})

(defn root-interceptors-cfg []

  (a/id :test/rt (a/runtime [:test/server]))
  (a/id :test/hello-world-handler (a/component `hello-world-handler))
  (a/id :test/root-interceptor (a/component `root-interceptor))

  (a/id :test/server
    (p/server 8080
      (p/interceptor "/" :test/root-interceptor)
      (h/endpoint :get "/" :test/hello-world-handler))))

(deftest ^:integration root-interceptors
  (let [cfg (core/build-config [:org.arachne-framework/arachne-pedestal]
              `(root-interceptors-cfg))
        rt (atom (rt/init cfg [:arachne/id :test/rt]))]
    (try
      (swap! rt c/start)
      (is (= "intercepted!" (slurp "http://localhost:8080")))
      (finally
        (swap! rt c/stop)))))

(defn interceptor-with-dep
  "ctor for interceptor with dependency"
  []
  (ped/component-interceptor
    {:leave (fn [component context]
              (assoc context :response
                             {:status 200
                              :body (str (:dep component) "-interceptor")}))}))

(defn handler-with-dep
  "handler function that has a dependency"
  [req]
  {:status 200
   :body (str (:dep req) "-handler")})

(defn dep-ctor
  "constructor for the dependency"
  []
  "testdep")

(defn interceptor-and-handler-deps-cfg []
  (a/id :test/rt (a/runtime [:test/server]))

  (a/id :test/dep (a/component `dep-ctor))

  (a/id :test/interceptor (a/component `interceptor-with-dep {:dep :test/dep}))

  (a/id :test/handler (h/handler `handler-with-dep {:dep :test/dep}))

  (a/id :test/server
    (p/server 8080
      (h/endpoint :get "/interceptor" :test/interceptor)
      (h/endpoint :get "/handler" :test/handler))))

(deftest ^:integration interceptor-and-handler-deps
  (let [cfg (core/build-config [:org.arachne-framework/arachne-pedestal]
              `(interceptor-and-handler-deps-cfg))
        rt (atom (rt/init cfg [:arachne/id :test/rt]))]
    (try
      (swap! rt c/start)
      (is (= "testdep-interceptor" (slurp "http://localhost:8080/interceptor")))
      (is (= "testdep-handler" (slurp "http://localhost:8080/handler")))
      (finally
        (swap! rt c/stop)))))

(defn handler-a
  [request]
  (ring-resp/response (:wild (:path-params request))))

(defn wildcard-path-cfg []
  (a/id :test/rt (a/runtime [:test/server]))
  (a/id :test/server
    (p/server 8080
      (h/endpoint :get "/a/b/*wild" (h/handler `handler-a)))))

(deftest ^:integration wildcard-path-test
  (let [cfg (core/build-config [:org.arachne-framework/arachne-pedestal]
              `(wildcard-path-cfg))
        rt (atom (rt/init cfg [:arachne/id :test/rt]))]
    (try
      (swap! rt c/start)
      (is (= "foo/bar" (slurp "http://localhost:8080/a/b/foo/bar")))
      (finally
        (swap! rt c/stop)))))

(defn handler-url-for
  [request]
  (ring-resp/response (r/url-for ::handler-url-for)))

(defn url-for-cfg []
  (a/id :test/rt (a/runtime [:test/server]))
  (a/id :test/server (p/server 8080
                       (h/endpoint :get "/foo/:a/baz" (h/handler `handler-url-for)))))

(deftest ^:integration url-for-test
  (let [cfg (core/build-config [:org.arachne-framework/arachne-pedestal]
                               `(url-for-cfg))
        rt (atom (rt/init cfg [:arachne/id :test/rt]))]
    (try
      (swap! rt c/start)
      (is (= "/foo/bar/baz" (slurp "http://localhost:8080/foo/bar/baz")))
      (finally
        (swap! rt c/stop)))))
