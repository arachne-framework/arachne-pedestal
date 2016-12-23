(ns arachne.pedestal.specs
  (:require [clojure.spec :as s]
            [io.pedestal.interceptor :as i]
            [arachne.http :as http]))

(s/def ::interceptor (partial satisfies? i/IntoInterceptor))

(s/def ::handler (partial satisfies? http/Handler))

(s/def ::endpoint (s/or :interceptor ::interceptor
                        :handler ::handler))

(s/def :arachne.pedestal.interceptor/name (s/and keyword? namespace))

(s/def ::context map?)

(s/def :arachne.pedestal.component-interceptor/enter
  (s/fspec
    :args (s/cat :component any? :context ::context)
    :ret ::context))

(s/def :arachne.pedestal.component-interceptor/leave
  (s/fspec
    :args (s/cat :component any? :context ::context)
    :ret ::context))

(s/def :arachne.pedestal.component-interceptor/error
  (s/fspec
    :args (s/cat :component any?
                 :context ::context
                 :exception #(instance? Throwable %))
    :ret ::context))

(s/fdef component-interceptor
  :args (s/cat
          :interceptor-map
          (s/keys :opt-un [:arachne.pedestal.interceptor/name
                           :arachne.pedestal.component-interceptor/enter
                           :arachne.pedestal.component-interceptor/leave
                           :arachne.pedestal.component-interceptor/error]))
  :ret ::interceptor)