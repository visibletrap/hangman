(ns hangman.repl-test
  (:require [cognitect.transcriptor :as xr]))

(doseq [repl-test-file (xr/repl-files ".")] (xr/run repl-test-file))
