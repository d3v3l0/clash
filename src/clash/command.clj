;   Copyright (c) David Millett. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns
    ^{:author "David Millett"
      :doc "Use performant shell commands like 'grep', 'cut', etc piped together on larger files."}
    clash.command
  (:require [clojure.string :as s])
  (:use [clojure.java.io :only (reader writer)]
        [clash.text_tools :refer :all]))

;; Linux/Unix "/bin/sh", "-c"
;; Mac
;; Windows: throw exception

(defn pipe
  "Build a command array for linux, prefixing with the following
  system commands: \"/bin/sh\", \"-c\", 'command'. This will
  enable multiple commands to execute via 'pipe'. "
  [command]
  (if (s/includes? command "|")
    ; linux, solaris, pretty much non microsoft
    (into-array (list "/bin/sh" "-c" command))
     command) )

(defn jproc
  "Get a Java Process for a Runtime system execution."
  [command]
  (let [updated (pipe command)]
    (.exec (Runtime/getRuntime) updated)) )


(defn jproc-instream
  "Get the input stream for a Java Process."
  [command]
  (.getInputStream (jproc command)))

 
(defn jproc-reader
  "Get a clojure reader from a java InputStream."
  [command]
  (if-not (nil? command)
    (reader (jproc-instream command))) )


;; todo: make this into one method with writer or console dump??

; It's pretty slow dumping to the console, but useful for testing.
(defn jproc-dump
  "Execution a shell system command, via java process, and
  dump result to the console (slow). This is handled via
  clojure reader to a line-seq. "
  [command, delim]
  (with-open [rdr (jproc-reader command)]
    (doseq [line (line-seq rdr)]
      (println (str line delim)))) )
  
(defn jproc-write
  "Execute a System command, via java Process, and capture
  the InputStream via clojure reader into a sequenc.e
  Write the resulting output to a file (useful for grep)."
  [command, output, delim]
  (with-open [rdr (jproc-reader command)
              wrt (writer output :append true)]
    (doseq [line (line-seq rdr)]
      (.write wrt (str line delim)))) )


;; Explore using map and functions here??
;; Explore using multiple functions with @
(defmacro with-jproc
  "A macro to combine clojure functions with a result
  from a shell command.

  Usage:
   (with-jproc command \":\" output last)
"
  [command delim output function]
  `(with-open [rdr# (jproc-reader ~command)
               wrt# (writer ~output)]
     (doseq [line# (line-seq rdr#)]
       (let [result# (~function line#)]
         (.write wrt# (str result# ~delim))))) )


(defmacro with-jproc-dump
  "Execute a sh command like grep and pass the result to
  a function to work with. For example, pass the result
  of a grep to 'last': (last (grep bar \"foobar\")) --> 'r'

  Usage:
   (with-jproc-dump command2 \":\" last)
"
  [command delim function]
  `(with-open [rdr# (jproc-reader ~command)]
     (doseq [line# (line-seq rdr#)]
       (let [result# (~function line#)]
         (println (str "macro result? " result# ~delim))))) )
