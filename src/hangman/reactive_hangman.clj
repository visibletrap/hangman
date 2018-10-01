; Required dependency: 'org.clojure/core.async'

(ns hangman.reactive-hangman
  (:require [clojure.string :as string]
            [clojure.core.async :refer [go chan go-loop <! >! close! to-chan]]))

(defn create-game [secret-word life]
  (let [word-length (count secret-word)]
    {:secret-word secret-word
     :status :in-progress
     :selected-letters []
     :life-left life
     :secret-word-length word-length
     :known-secret-word (apply str (take word-length (repeat "_")))}))

(defn update-known-secret-word
  [{:keys [secret-word selected-letters] :as game}]
  (let [selected-letters (set selected-letters)
        new-known-secret-word (->> (map str secret-word)
                                   (map #(get selected-letters % "_"))
                                   (apply str))]
    (assoc game :known-secret-word new-known-secret-word)))

(defn update-life-left
  [{:keys [life-left secret-word selected-letters] :as game}]
  (let [new-life-left (if (string/includes? secret-word (last selected-letters))
                        life-left
                        (dec life-left))]
    (assoc game :life-left new-life-left)))

(defn update-new-status
  [game]
  (let [new-status (cond
                     (zero? (:life-left game)) :lose
                     (string/includes? (:known-secret-word game) "_") :in-progress
                     :else :won)]
    (assoc game :status new-status)))

(defn receive-letter [game letter]
  (-> game
      (update :selected-letters conj letter)
      update-known-secret-word
      update-life-left
      (update-new-status)))

(defn get-result
  [game]
  (dissoc game :secret-word))

(defn reactive-hangman [secret-word letters-chan]
  (let [output-chan (chan)
        initial-game (create-game secret-word 7)]
    (go (>! output-chan (get-result initial-game)))
    (go-loop [game initial-game]
      (println game)
      (if-let [letter (<! letters-chan)]
        (let [new-game (receive-letter game letter)]
          (>! output-chan (get-result new-game))
          (recur new-game))
        (close! output-chan)))
    output-chan))

(comment
  ; main logic: won
  (-> (create-game "bigbear" 7)
      (receive-letter "b")
      (receive-letter "o")
      (receive-letter "i")
      (receive-letter "g")
      (receive-letter "a")
      (receive-letter "e")
      (receive-letter "y")
      (receive-letter "r")
      get-result)

  ; main logic: lose
  (-> (create-game "bigbear" 7)
      (receive-letter "b")
      (receive-letter "o")
      (receive-letter "a")
      (receive-letter "e")
      (receive-letter "n")
      (receive-letter "u")
      (receive-letter "t")
      (receive-letter "z")
      (receive-letter "x")
      (receive-letter "v")
      get-result)

  ; with async: win
  (let [letters-chan (to-chan ["b" "o" "i" "g" "a" "e" "y" "r"])
        out-chan (reactive-hangman "bigbears" letters-chan)]
    (go-loop []
      (when-let [output-evt (<! out-chan)]
        (println output-evt)
        (recur))))
  )
