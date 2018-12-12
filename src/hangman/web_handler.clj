(ns hangman.web-handler
  (:require [bidi.bidi :refer [tag]]
            [yada.yada :as yada :refer [listener resource as-resource]]
            [yada.resources.webjar-resource :refer [new-webjar-resource]]
            [hangman.web-game :as game]))

(defonce server (atom nil))

(def routes
  [""
   (-> [""
        [["/hangman/"
          [[""
            (resource
              {:methods
               {:post
                {:produces "application/json"
                 :response
                 (fn [_]
                   (game/create-game))}}})]
           [[:id]
            [[""
              (resource
                {:produces "application/json"
                 :response
                 (fn [{{{:keys [id]} :params} :request}]
                   (game/game-snapshot id))})]
             [["/timer"]
              (resource
                {:swagger/description "WARN: This enpoint can't be tested with this swagger UI"
                 :methods
                 {:get
                  {:produces "text/event-stream"
                   :response
                   (fn [{{{:keys [id]} :params} :request}]
                     (game/time-status-mult id))}}})]
             [["/" :letter]
              (resource
                {:methods
                 {:put
                  {:produces "application/json"
                   :response
                   (fn [{{{:keys [id letter]} :params} :request}]
                     (game/guess id letter))}}})]]]]]]]
       yada/swaggered)])

(defn start [& [port]]
  (when-not (game/game-started?) "WARN: Game hasn't started")
  (let [svr (listener #'routes {:port (or port 3000)})]
    (reset! server svr))
  (println "Server start at port 3000"))

(defn stop []
  (when @server ((:close @server)))
  (reset! server nil))

(defn restart []
  (stop)
  (start))

(defn -main [& [port]]
  (let [port (Integer. (or port (System/getenv :port)))]
    (game/start)
    (start port)))

(comment
  (game/start)
  (game/stop)
  (start)
  (stop)
  @game/app-state
  )
