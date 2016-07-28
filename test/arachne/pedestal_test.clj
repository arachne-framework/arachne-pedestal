(ns arachne.pedestal-test
  (:require [clojure.test :refer :all]
            [arachne.core :as core]
            [arachne.core.config :as cfg]))

(deftest hello-world
  (is (= 2 (+ 1 1))))
