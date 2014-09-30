;   Copyright (c) David Millett. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.
(ns ^{:author dmillett} clash.pivot
  (:require [clojure.core.reducers :as r]
            [clojure.math.combinatorics :as cmb]
            [clash.core :as c]
            [clash.tools :as t])
  )

(defn pivot-group-from-single
  "Create a list of functions given a list of values and add
  meta-data to them with {:base_msg 'msg' :name 'X'}"
  [pivotf values msg]
  (map #(with-meta (pivotf %) {:name (str msg "-" %)}) values) )


(defn combine-functions-with-meta
  "Carry the metadata :name forward from the pivot functions"
  [f preds metafs]
  ; copy meta data from pivot functions when appending them to predicates
  (map #(with-meta
          (apply f (conj preds %))
          {:name (:name (meta %))}) metafs) )

(defn s-pivot
  ([col preds pivotf pivotd] (s-pivot col c/all? preds pivotf pivotd ""))
  ([col preds pivotf pivotd msg] (s-pivot col c/all? preds pivotf pivotd msg))
  ([col f preds pivotf pivotd msg]
    (let [message (if (empty? msg) "pivot" (str msg "_pivot"))
          fpivots (pivot-group-from-single pivotf pivotd message)
          combos (combine-functions-with-meta f preds fpivots)]

      (t/sort-map-by-value
        (reduce
          (fn [r fx]
            (assoc-in r [(:name (meta fx))] (c/count-with col fx)) )
          {} combos) )
      ) )  )

(defn p-pivot
  "Parallel evaluation of each value in a collection (col) with a base set of
  predicates (preds) and a 'pivot' predicate with its list of corresponding
  pivot values. This function returns a map sorted descending by pivot count.
  By default, (pivot) will use the conditional all? (and), but any? (or) could
  also be used. For example:

  ; 6 is an even number dividable by 2, 3
  ; 8 is an even number dividable by 2
  ; 7 is an odd number (it does not satisfy any of the composite predicates)
  user=> (pivot '(6 7 8) [number? even?] divisible-by? '(2 3) \"is-even-number \")

  {is-even-number_pivot-by-2 2, is-even-number_pivot-by-3 1}
  "
  ([col preds pivotf pivotd] (p-pivot col c/all? preds pivotf pivotd ""))
  ([col preds pivotf pivotd msg] (p-pivot col c/all? preds pivotf pivotd msg))
  ([col f preds pivotf pivotd msg]
    (let [message (if (empty? msg) "pivot" (str msg "_pivot"))
          fpivots (pivot-group-from-single pivotf pivotd message)
          combos (combine-functions-with-meta f preds fpivots)]

      (t/sort-map-by-value
        (reduce
          (fn [r fx]
            (assoc-in r [(:name (meta fx))] (c/p-count-with col fx)) )
          {} combos) )
      ) ) )

(defn pivot
  "Evaluation of each value in a collection (col) with a base set of
  predicates (preds) and a 'pivot' predicate with its list of corresponding
  pivot values. This function returns a map sorted descending by pivot count.
  By default, (pivot) will use the conditional all? (and), but any? (or) could
  also be used. For example:

  ; 6 is an even number dividable by 2, 3
  ; 8 is an even number dividable by 2
  ; 7 is an odd number (it does not satisfy any of the composite predicates)
  (pivot '(6 7 8) 'is-even-number' :b [number? even?] :p divisible-by? :v '(2 3))
  or
  (pivot '(6 7 8) 'is-even-number' :b [number? even?] :p divisible-by? :v '(2 3) :plevel 2)
  {is-even-number_pivot-by-2 2, is-even-number_pivot-by-3 1}
  "
  [col msg & {:keys [b p v plevel] :or {b [] p [] v [] plevel 1}}]
  (cond
    (= 1 plevel) (s-pivot col c/all? b p v msg)
    (= 2 plevel) (p-pivot col c/all? b p v msg)
    ) )

(defn- s-pivot-compare
  "Compare the results (maps) of two pivots with a specific function. For
  example, perhaps it is helpful to compare the ratio of values from col1/col2.
  The output is sorted in descending order."
  [col1 col2 preds pivotf pivotd msg compf]
  (let [a (pivot col1 preds pivotf pivotd msg)
        b (pivot col2 preds pivotf pivotd msg)]
    (t/sort-map-by-value (t/compare-map-with a b compf))
    ) )

(defn- p-pivot-compare
  "Compare the results (maps) of two pivots with a specific function. For
  example, perhaps it is helpful to compare the ratio of values from col1/col2.
  The output is sorted in descending order."
  [col1 col2 preds pivotf pivotd msg compf]
  (let [a (p-pivot col1 preds pivotf pivotd msg)
        b (p-pivot col2 preds pivotf pivotd msg)]
    (t/sort-map-by-value (t/compare-map-with a b compf))
    ) )

(defn pivot-compare
  "Compare the results (maps) of two pivots with a specific function. For
  example, perhaps it is helpful to compare the ratio of values from col1/col2.
  The output is sorted in descending order. The default parallelism is 1 (single threaded).
  Specify :plevel 2 for parallel operation (r/fold)."
  [col1 col2 msg {:keys [b p v plevel] :or {b [] p [] v [] plevel 1}}]
  (cond
    (= 1 plevel) (s-pivot-compare col1 col2 b p v msg)
    (= 2 plevel) (p-pivot-compare col1 col2 b p v msg)
    ) )

;; **************************************************************************************

(defn- single-pivot-group-matrix
  "Create a list of functions given a list of values and add
  meta-data to them with {:name 'msg'-pivot_X :base_msg 'msg' :pivot 'X'}"
  [pivotf values msg]
  (let [name (str msg "-pivot_")]
    ; :name will get recalculated later when > 1 pivot groups exist
    (map #(with-meta (pivotf %) {:name (str name %) :base_msg msg :pivot %}) values)
    ) )

(defn- build-text-matrix-meta
  "Determine what the combined meta data from each function should look like."
  [a b delim mk]
  (cond
    (and (not (nil? (mk (meta a)))) (not (nil? (mk (meta b)))) ) (str (mk (meta a)) delim (mk (meta b)))
    (not (nil? (mk (meta a)))) (str (mk (meta a)))
    (nil? (mk (meta a)))  (str (mk (meta b)))
    :else nil
    ) )

(defn- merge-meta-matrix
  "Combine meta data from multiple functions into single string.
  'msg'-pivot_x|y|z"
  [a b delim]
  (let [bases (build-text-matrix-meta a b delim :base_msg)
        pivots (build-text-matrix-meta a b delim :pivot)
        names (build-text-matrix-meta a b delim :name)]

    {:name (str bases "-pivot_" pivots)}
    ) )

(defn- meta-name-text-matrix
  "Build the meta :name field for a function."
  ([txt a] (meta-name-text-matrix txt a nil))
  ([txt a delim]
    (if delim
      (str txt (:pivot (meta a)) delim)
      (str txt (:pivot (meta a)))
      ) ) )

(defn conj-meta-matrix
  "Use conj to combine values with a collection and to include the meta data from
  the old collection and values to the resulting collection."
  ([col v] (with-meta (conj col v) (merge-meta-matrix col v "|")) )
  ([col v & values]
    (loop [c col
           nxt v
           rst values
           mtext (str (:base_msg (meta nxt)) "-pivot_")]

      (if-not rst
        (with-meta (conj c nxt) {:name (meta-name-text-matrix mtext nxt)})
        (recur (conj c nxt) (first rst) (next rst) (meta-name-text-matrix mtext nxt "|"))
        ) )
    ) )

(defn- combine-functions-matrix
  "Carry the metadata :name forward from the pivot functions"
  [f preds pivots]
  ; copy meta data from pivot functions when appending them to predicates
  (let [mt (:name (meta pivots))
        params (into [] (concat preds pivots))]
    (with-meta (apply f params) {:name mt}) ) )

(defn build-pivot-groups-matrix
  "Build a list of pivot predicates for multiple pivots. In this case, each
  param is a sequence. The corresponding index of each sequence are (map)
  together to form a list."
  [pivotfs pivotsd base_msg]
  (loop [result []
         fs pivotfs
         data pivotsd]
    (if (empty? fs)
      result
      (recur
        (conj result (single-pivot-group-matrix (first fs) (first data) base_msg))
        (rest fs)
        (rest data)) )
    ) )

;; todo: replace with combinatorics (cartesian-product)
(defn build-matrix
  "Build a single list of predicate groups that comprise a flattened
  matrix for each collection of pivots within pivot_groups. This supports
  up to 4 different predicate groups. Perhaps this should be a macro?"
  [f base pgs]
  (let [cnt (count pgs)]
    (cond
      (= 1 cnt) (map #(with-meta (apply f (conj base %)) {:name (:name (meta %))}) (first pgs))
      (= 2 cnt) (for [a (nth pgs 0) b (nth pgs 1)]
                  (combine-functions-matrix f base (conj-meta-matrix [] a b) ) )
      (= 3 cnt) (for [a (nth pgs 0) b (nth pgs 1) c (nth pgs 2)]
                  (combine-functions-matrix f base (conj-meta-matrix [] a b c)) )
      (= 4 cnt) (for [a (nth pgs 0) b (nth pgs 1) c (nth pgs 2) d (nth pgs 3)]
                  (combine-functions-matrix f base (conj-meta-matrix [] a b c d)) )
      (= 5 cnt) (for [a (nth pgs 0) b (nth pgs 1) c (nth pgs 2) d (nth pgs 3) e (nth pgs 4)]
                  (combine-functions-matrix f base (conj-meta-matrix [] a b c d e)) )
      ) ) )

(defn- s-pivot-matrix
  "Evaluate a multi-dimensional array of predicates with their base predicates over
  a collection. The predicate evaluation against the collection is single threaded."
  ([col base_preds pivotfs pivotds] (s-pivot-matrix col base_preds pivotfs pivotds ""))
  ([col base_preds pivotfs pivotds msg]
    (let [message (if (empty? msg) "pivot-test" msg)
          pivot_groups (build-pivot-groups-matrix pivotfs pivotds message)
          flat_matrix (build-matrix c/all? base_preds pivot_groups)]

      (t/sort-map-by-value
        (reduce
          (fn [result fx]
            (assoc-in result [(:name (meta fx))] (c/count-with col fx)) )
           {} flat_matrix)
        ) )
    ) )

(defn- p-pivot-matrix
  "Evaluate a multi-dimensional array of predicates with their base predicates over
  a collection. The predicate evaluation against the collection is in parallel (reducers/fold)."
  ([col base_preds pivotfs pivotds] (p-pivot-matrix col base_preds pivotfs pivotds ""))
  ([col base_preds pivotfs pivotds msg]
    (let [message (if (empty? msg) "pivot-test" msg)
          pivot_groups (build-pivot-groups-matrix pivotfs pivotds message)
          flat_matrix (build-matrix c/all? base_preds pivot_groups)]

      (t/sort-map-by-value
        (reduce
          (fn [result fx]
            (assoc-in result [(:name (meta fx))] (c/p-count-with col fx)) )
          {} flat_matrix)
        ) )
    ) )

(defn- fold-merge-with-plus
  "Acts like (merge-with), but satisfies zero arity for recuers/fold"
  ([] {})
  ([& maps] (merge-with + maps)) )

(defn- pp-pivot-matrix
  "Evaluate a multi-dimensional array of predicates with their base predicates over
  a collection. The predicate evaluation against the collection is in parallel (reducers/fold).
  This might be beneficial when the flattened cartesian product has a large count
  (maybe > 50 predicate groups) and the workstation has a large number of cores."
  ([col base_preds pivotfs pivotds] (pp-pivot-matrix col base_preds pivotfs pivotds ""))
  ([col base_preds pivotfs pivotds msg]
    (let [message (if (empty? msg) "pivot-test" msg)
          pivot_groups (build-pivot-groups-matrix pivotfs pivotds message)
          flat_matrix (into [] (build-matrix c/all? base_preds pivot_groups))]

      (t/sort-map-by-value
        (r/fold fold-merge-with-plus
          (fn [result fx]
            (assoc-in result [(:name (meta fx))] (c/p-count-with col fx)) )
          flat_matrix)
        ) )
    ) )

(defn pivot-matrix
  "Evaluate a multi-dimensional array of predicates with their base predicates over
  a collection. The predicate evaluation against the collection is single/multithreaded.
  Ex:
  (pivot-matrix-x col [number?] 'foo' :plevel 2 :pivots [divisible-by?] :values [(range 2 5)]

  (def even-numbers [number? even?])
  (def pivot-functions [divisible-by? divisible-by?])
  (def pivot-values [(range 3 6) (range 5 7)])
  (def number-col (range 1 - 1000001))

  ; predicate group [number? even? (divisible-by? 3) (divisible-by? 5)] will get applied to
  ; 'numbers-col' in parallel with (reducers/fold)
  (pivot-matrix-x number-col even-numbers 'fizz-buzz' :p pivot-functions :v pivot-values :plevel 2)

  Notes:
  'plevel 1' is single threaded for everything
  'plevel 2' is mutli-threaded when applying predicates to a collection
  'plevel 3' is multi-threaded for list of predicate groups & applying predicates to a collection
             (note: more beneficial for a large cartesian structure or a good multi-cpu workstation)
  )"
  [col msg & {:keys [b p v plevel] :or {b [] p [] v [] plevel 1}}]
  (cond
    (= 1 plevel) (s-pivot-matrix col b p v msg)
    (= 2 plevel) (p-pivot-matrix col b p v msg)
    (= 3 plevel) (pp-pivot-matrix col b p v msg)
    ) )


;(defn pivot-matrix-compare
;  "Compare the results (maps) of two pivots with a specific function. For
;  example, perhaps it is helpful to compare the ratio of values from col1/col2.
;  The output is sorted in descending order."
;  [col1 col2 preds pivotf pivotd msg compf]
;  (let [a (pivot-matrix col1 preds pivotf pivotd msg)
;        b (pivot-matrix col2 preds pivotf pivotd msg)]
;    (t/sort-map-by-value (t/compare-map-with a b compf))
;    ) )
;
;(defn p-pivot-matrix-compare
;  "Compare the results (maps) of two pivots with a specific function. For
;  example, perhaps it is helpful to compare the ratio of values from col1/col2.
;  The output is sorted in descending order."
;  [col1 col2 preds pivotf pivotd msg compf]
;  (let [a (p-pivot-matrix col1 preds pivotf pivotd msg)
;        b (p-pivot-matrix col2 preds pivotf pivotd msg)]
;    (t/sort-map-by-value (t/compare-map-with a b compf))
;    ) )