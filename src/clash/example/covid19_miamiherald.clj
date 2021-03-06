(ns clash.example.covid19_miamiherald
  (:require [clojure.string :as s]
            [clojure.spec.alpha :as cs]
            [clash.core :as cl]
            [clash.tools :as ct]))

;; From Miami Herald CSV data (thanks to them)
(defrecord MiamiHeraldCovid19Data [state_code state positive death total negative pending updated x pp])

(defn mh-parser
  "A CSV parser that creates a defrecord from a 'defrecord and a line of data. "
  [line]
  (try
    (when (not-empty line)
      (apply ->MiamiHeraldCovid19Data (map #(if (not-empty %) (read-string %) nil) (s/split line #","))))
    (catch Exception e (println e))
    ) )

(defn mh-issued
  "Grab the total number of tests issued per state."
  [data]
  (try
    (when data
      {(:state data) (Integer/parseInt (:total data))})
     (catch Exception _)
     ) )

(defn mh-percent-positive
  "A percentage for how many tests are positive?"
  [data]
  (try
    (when data
      {(:state data) {:total (:total data) :percent_positive (* 100.0 (/ (:positive data) (:total data)))}})
    (catch Exception _)
    ))

(defn mh-percent-uncertainty
  "What patients have symptoms but test negative? What is causing their symptoms?
  Are there test limitations (too early, too late) or quality issues?"
  [data]
  (try
    (when data
      (let [total (:total data)]
        {(:state data) {:total total :percent_unknown (* 100.0 (/ (- total (:positive data)) total))}}
        ) )
    (catch Exception _)
    ))