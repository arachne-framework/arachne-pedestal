(ns arachne.pedestal.specs
  (:require [clojure.spec :as s]
            [io.pedestal.interceptor :as i]
            [arachne.http :as http]))

(s/def ::interceptor (partial satisfies? i/IntoInterceptor))

(s/def ::handler (partial satisfies? http/Handler))

(s/def ::endpoint (s/or :interceptor ::interceptor
                        :handler ::handler))