(ns hangman.reactive-hangman
  (:require [clojure.string :as string]
            [clojure.core.async :refer [chan go-loop alts! close! alt! timeout]]))

(def guess-clock 5)

(defn vectorize [text]
  (mapv str text))

(defn init-game-data [secret-word life]
  (let [word-length (count secret-word)]
    {:secret-word secret-word
     :secret-word-vec (vectorize secret-word)
     :status :in-progress
     :selected-letters []
     :life-left life
     :secret-word-length word-length
     :known-secret-word (apply str (take word-length (repeat "_")))
     :time-left guess-clock}))

(defn initial-letters-count
  [word-length]
  (-> (dec word-length)
      (quot 5)
      (inc)))

(defn rand-initial-letters
  [secret-word]
  (let [letters (map str (-> secret-word seq set))
        no-letters (initial-letters-count (count secret-word))]
    (take no-letters (shuffle letters))))

(defn known-secret-word
  [{:keys [secret-word-vec selected-letters]}]
  (let [selected-letters-set (set selected-letters)]
    (->> secret-word-vec
         (map #(get selected-letters-set % "_"))
         (apply str))))

(defn new-life-left
  [{:keys [life-left secret-word-vec selected-letters]} new-letter]
  (cond
    (some #{new-letter} selected-letters) life-left
    (not-any? #{new-letter} secret-word-vec) (dec life-left)
    :default life-left))

(defn apply-letter [game letter]
  (-> game
      (#(assoc % :life-left (new-life-left % letter)))
      (update :selected-letters (fn [lts] (if (some #{letter} lts) lts (conj lts letter))))
      (#(assoc % :known-secret-word (known-secret-word %)))
      (assoc :time-left guess-clock)))

(defn prefill-letters
  [game letters]
  (reduce apply-letter game letters))

(defn create-game [secret-word life]
  (let [game (init-game-data secret-word life)
        initial-letters (rand-initial-letters (:secret-word-vec game))]
    (prefill-letters game initial-letters)))

(defn game-state
  [game]
  (select-keys game [:status :selected-letters :life-left :secret-word-length :known-secret-word :time-left]))

(defn time-tick [game]
  (let [game-after (if (zero? (:time-left game))
                     (assoc game :time-left guess-clock)
                     (update game :time-left dec))]
    (if (zero? (:time-left game-after))
      (update game-after :life-left dec)
      game-after)))

(defn resolve-status
  [game]
  (cond
    (zero? (:life-left game)) :lose
    (string/includes? (:known-secret-word game) "_") :in-progress
    :else :won))

(defn update-status
  [game]
  (assoc game :status (resolve-status game)))

(defn handle-new-event
  [game [e & args]]
  (-> (case e
        :guess (apply-letter game (first args))
        :time-tick (time-tick game))
      update-status))

(def game-end? (comp #{:won :lose} :status))

(defn reactive-hangman [secret-word letters-chan time-chan]
  (let [output-chan (chan 1)]
    (go-loop [game (create-game secret-word 7)]
      (alts! [[output-chan (game-state game)] (timeout 5000)])
      (if (game-end? game)
        (close! output-chan)
        (when-let [event (alt!
                      letters-chan ([letter] [:guess letter])
                      time-chan ([_] [:time-tick])
                      (timeout 5000) ([_] (close! output-chan)))]
          (recur (handle-new-event game event)))))
    output-chan))
