(ns arachne.pedestal.server-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [com.stuartsierra.component :as c]
            [arachne.core :as core]
            [arachne.http :as http]
            [arachne.core.config :as cfg]
            [arachne.core.runtime :as rt]
            [ring.util.response :as ring-resp]
            [clj-http.client :as client]))

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

(defrecord HandlerHandler []
  http/Handler
  (handle [_ _] (ring-resp/response "Hello from a handler!")))

(deftest ^:integration hello-world-server
  (let [cfg (core/build-config [:org.arachne-framework/arachne-pedestal]
                  '(do
                     (require '[arachne.core.dsl :as core])
                     (require '[arachne.http.dsl :as http])
                     (require '[arachne.pedestal.dsl :as ped])

                     (core/runtime :test/rt [:test/server])

                     (core/component :test/hello-world-handler {}
                       'arachne.pedestal.server-test/hello-world-handler)

                     (core/component :test/b-handler {}
                       'arachne.pedestal.server-test/hello-world-handler)

                     (core/component :test/handler-handler {}
                       'arachne.pedestal.server-test/->HandlerHandler)

                     (core/component :test/a-interceptor {}
                       'arachne.pedestal.server-test/a-interceptor)

                     (ped/server :test/server 8080

                       (http/endpoint :get "/" :test/hello-world-handler)

                       (http/endpoint :get "/handler" :test/handler-handler)

                       (http/context "a"

                         (ped/interceptor :test/a-interceptor)

                         (http/endpoint :get "b" :test/b-handler)
                         )

                       )


                     ))
        rt (rt/init cfg [:arachne/id :test/rt])]

    (let [rt (c/start rt)]
      (is (= "Hello, world!" (slurp "http://localhost:8080")))
      (is (= "Hello from a handler!" (slurp "http://localhost:8080/handler")))
      (is (= "Hello, Luke!" (slurp "http://localhost:8080/a/b")))
      (let [result (try (client/get "http://localhost:8080/no-such-url")
                        (catch Exception e (ex-data e)))]
        (is (= 404 (:status result)))
        (is (= "Not Found" (:body result))))
      (c/stop rt))))


(defn invalid-handler
  []
  (java.util.Date.))

(deftest ^:integration endpoint-validity
  (let [cfg (core/build-config [:org.arachne-framework/arachne-pedestal]
                  '(do
                     (require '[arachne.core.dsl :as core])
                     (require '[arachne.http.dsl :as http])
                     (require '[arachne.pedestal.dsl :as ped])

                     (core/runtime :test/rt [:test/server])

                     (core/component :test/invalid-handler {}
                       'arachne.pedestal.server-test/invalid-handler)

                     (ped/server :test/server 8080
                       (http/endpoint :get "/" :test/invalid-handler))))
        rt (rt/init cfg [:arachne/id :test/rt])]
    (is (thrown-with-msg? Throwable #"Error in component"
          (c/start rt)))))
