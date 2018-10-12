(ns hangman.web-game
  (:require [clojure.core.async
             :refer [chan go-loop timeout <! offer! close! mult tap sliding-buffer <!! >!! untap]]
            [clojure.data :refer [diff]]
            [clojure.set :refer [rename-keys]]
            [hangman.reactive-hangman :as core])
  (:import [java.time Instant]))

(defonce app-state (atom nil))

(defn update-app
  [f & args]
  (apply swap! app-state f args))

(def words
  ["adventurous"
   "courageous"
   "extramundane"
   "generous"
   "intransigent"
   "sympathetic"
   "vagarious"
   "witty"])

(defn create-game-id
  [i]
  (str "game-id-" i))

(defn format-game-data
  [game game-id]
  (-> game
      (select-keys [:status :selected-letters :life-left :secret-word-length :known-secret-word])
      (assoc :id game-id)))

(defn game-ref
  [game-id]
  (-> @app-state :games (get game-id)))

(defn game-snapshot
  [game-id]
  (-> game-id game-ref :snapshot))

(defn game-letters-chan
  [game-id]
  (-> game-id game-ref :letters-chan))

(defn game-out-mult
  [game-id]
  (-> game-id game-ref :out-mult))

(defn game-time-status-mult
  [game-id]
  (-> game-id game-ref :time-status-mult))

(defn recent-game-id
  [app-state]
  (-> app-state :current-game-seq create-game-id))

(defn game->time-status
  [game]
  {:timestamp (.getEpochSecond (Instant/now))
   :event (if (core/game-end? game) "game-over" "time-spent")
   :data (select-keys game [:time-left :life-left])})

(defn create-new-game
  [app-state]
  (let [game-seq (-> app-state :current-game-seq inc)
        timer-chan (chan (sliding-buffer 1))
        _ (tap (:timer-mult app-state) timer-chan)
        letters-chan (chan 1)
        out-chan (core/reactive-hangman (rand-nth words) letters-chan timer-chan)
        out-mult (mult out-chan)
        time-status-chan (chan 1 (map game->time-status))
        _ (tap out-mult time-status-chan)
        new-game {:timer-chan timer-chan
                  :letters-chan letters-chan
                  :out-chan out-chan
                  :out-mult out-mult
                  :time-status-chan time-status-chan
                  :time-status-mult (mult time-status-chan)}]
    (-> app-state
        (update :games assoc (create-game-id game-seq) new-game)
        (assoc :current-game-seq game-seq))))

(defn synchronous-update-game
  ([game-id] (synchronous-update-game game-id nil))
  ([game-id update-fn]
   (let [return-chan (chan 1)
         game-mult (game-out-mult game-id)
         _ (tap game-mult return-chan)]
     (when update-fn (update-fn))
     (let [o (<!! return-chan)]
       (untap game-mult return-chan)
       (close! return-chan)
       (format-game-data o game-id)))))

(defn time-status-mult
  [game-id]
  (game-time-status-mult game-id))

(defn guess
  [game-id letter]
  (synchronous-update-game game-id (fn [] (>!! (game-letters-chan game-id) letter))))

(defn update-game-snapshot
  [game-id v]
  (update-app update-in [:games game-id] assoc :snapshot v))

(defn start-game-snapshoter
  [game-id]
  (let [snapshot-chan (chan 1)
        _ (tap (game-out-mult game-id) snapshot-chan)]
    (go-loop []
      (when-let [v (<! snapshot-chan)]
        (update-game-snapshot game-id (format-game-data v game-id))
        (recur)))))

(defn create-game
  []
  (let [new-game-id (recent-game-id (update-app create-new-game))
        game (synchronous-update-game new-game-id)]
    (start-game-snapshoter new-game-id)
    game))

(defn start-timer
  []
  (let [out-chan (:timer-chan @app-state)]
    (go-loop []
      (if @app-state
        (do
          (<! (timeout 1000))
          (when @app-state
            (update-app update :current-time inc)
            (offer! out-chan (:current-time @app-state)))
          (recur))
        (close! out-chan)))))

(defn start
  []
  (let [timer-chan (chan 1)]
    (reset! app-state {:games {}
                       :current-game-seq 0
                       :timer-chan timer-chan
                       :timer-mult (mult timer-chan)
                       :current-time 0}))
  (start-timer)
  :started)

(defn game-started?
  []
  (boolean @app-state))

(defn stop
  []
  (reset! app-state nil)
  :stopped)

(comment
  (start)
  (stop)
  (create-game)
  (game-snapshot "game-id-1")
  @app-state
  )
