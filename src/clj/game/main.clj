(ns game.main
  (:import [org.zeromq ZMQ ZMQQueue])
  (:require [cheshire.core :refer [parse-string generate-string]]
            [cheshire.generate :refer [add-encoder encode-str]]
            [game.macros :refer [effect]]
            [game.core :refer [game-states system-msg pay gain draw end-run] :as core]
            [environ.core :refer [env]])
  (:gen-class :main true))

(add-encoder java.lang.Object encode-str)

(def ctx (ZMQ/context 1))

(def commands
  {"say" core/say
   "system-msg" #(system-msg %1 %2 (:msg %3))
   "change" core/change
   "move" core/move-card
   "mulligan" core/mulligan
   "keep" core/keep-hand
   "start-turn" core/start-turn
   "end-turn" core/end-turn
   "draw" core/click-draw
   "credit" core/click-credit
   "purge" core/do-purge
   "remove-tag" core/remove-tag
   "play" core/play
   "rez" #(core/rez %1 %2 (:card %3) nil)
   "derez" #(core/derez %1 %2 (:card %3))
   "run" core/click-run
   "no-action" core/no-action
   "continue" core/continue
   "access" core/successful-run
   "jack-out" core/jack-out
   "advance" core/advance
   "score" core/score
   "choice" core/resolve-prompt
   "select" core/select
   "shuffle" core/shuffle-deck
   "ability" core/play-ability
   "trash-resource" core/trash-resource
   "auto-pump" core/auto-pump})

(defn convert [args]
  (try
    (let [params (parse-string (String. args) true)]
      (if (or (get-in params [:args :card]))
        (update-in params [:args :card :zone] #(map (fn [k] (if (string? k) (keyword k) k)) %))
        params))
    (catch Exception e
      (println "Convert error " e))))

(defn send-to-socket [socket gameid action]
 (prn "sending action" action)
  (if-let [state (@game-states gameid)]
    (.send socket (generate-string (assoc (dissoc @state :events :turn-events :dispatch) :action action)))
    (.send socket (generate-string "ok")))
    (prn "sending DONE" action)
 )

(defn run [socket]
  (while true
    (let [_ (prn "call recv")
            {:keys [gameid action command side args text] :as msg} (convert (.recv socket))
          state (@game-states gameid)]
           (prn "ACTION" action)
        (try
        (future 
          (prn "future started")
          (case action
            "start" (core/init-game msg (fn [] (send-to-socket socket gameid "blocking")))
            "remove" (swap! game-states dissoc gameid)
            "do" ((commands command) state (keyword side) args)
            "notification" (when state
                             (swap! state update-in [:log] #(conj % {:user "__system__" :text text}))))
          (prn "sending")
          (send-to-socket socket gameid action)
          (prn "future done"))
        (catch Exception e
          (println "Error " action command (get-in args [:card :title]) e "\nStack trace:"
                   (java.util.Arrays/toString (.getStackTrace e)))
          (if (and state (#{"do" "start"} action))
            (.send socket (generate-string state))
            (.send socket (generate-string "error")))))
            (prn "after try"))))
      
(defn zmq-url
  []
  (str "tcp://" (or (env :zmq-host) "127.0.0.1") ":1043"))

(defn dev []
  (println "[Dev] Listening on port 1043 for incoming commands...")
  (let [socket (.socket ctx ZMQ/REP)]
    (.bind socket (zmq-url))
    (run socket)))

(defn -main []
  (println "[Prod] Listening on port 1043 for incoming commands...")
  (let [worker-url "inproc://responders"
        router (doto (.socket ctx ZMQ/ROUTER) (.bind (zmq-url)))
        dealer (doto (.socket ctx ZMQ/DEALER) (.bind worker-url))]
    (dotimes [n 2]
      (.start
       (Thread.
        (fn []
          (let [socket (.socket ctx ZMQ/REP)]
            (.connect socket worker-url)
            (run socket))))))

    (.start (Thread. #(.run (ZMQQueue. ctx router dealer))))))
