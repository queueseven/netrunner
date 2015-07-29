(in-ns 'game.core)

(def cards-identities
  {"Andromeda: Dispossessed Ristie"
   {:effect (effect (gain :link 1) (draw 4)) :mulligan (effect (draw 4))}

   "Argus Security: Protection Guaranteed"
   {:events {:agenda-stolen
             {:prompt "Take 1 tag or suffer 2 meat damage?"
              :choices ["1 tag" "2 meat damage"] :player :runner
              :msg "make the Runner take 1 tag or suffer 2 meat damage"
              :effect (req (if (= target "1 tag")
                             (do (gain state :runner :tag 1) (system-msg state side "takes 1 tag"))
                             (do (damage state :runner :meat 2 {:unboostable true :card card})
                                 (system-msg state side "suffers 2 meat damage"))))}}}

   "Armand \"Geist\" Walker"
   {:effect (effect (gain :link 1))
    :events {:trash {:optional {:req (req (and (= side :runner) (= (second targets) :ability-cost)))
                                :prompt "Draw a card?" :msg (msg "draw a card") :effect (effect (draw 1))}}}}

   "Blue Sun: Powering the Future"
   {:abilities [{:msg (msg "add " (:title target) " to HQ and gain " (:cost target) " [Credits]")
                 :choices {:req #(:rezzed %)}
                 :effect (effect (gain :credit (:cost target)) (move target :hand))}]}

   "Cerebral Imaging: Infinite Frontiers"
   {:effect (req (add-watch state :cerebral-imaging
                            (fn [k ref old new]
                              (let [credit (get-in new [:corp :credit])]
                                (when (not= (get-in old [:corp :credit]) credit)
                                  (swap! ref assoc-in [:corp :max-hand-size] credit))))))}

   "Chaos Theory: Wünderkind"
   {:effect (effect (gain :memory 1))}

   "Cybernetics Division: Humanity Upgraded"
   {:effect (effect (lose :max-hand-size 1) (lose :runner :max-hand-size 1))}

   "Edward Kim: Humanitys Hammer"
   {:effect (effect (gain :link 1))
    :events {:access {:once :per-turn
                      :req (req (= (:type target) "Operation"))
                      :effect (effect (trash target))
                      :msg (msg "trash " (:title target) (if (some #{:discard} (:zone target)) ", but it is already trashed."))}}}

   "Exile: Streethawk"
   {:effect (effect (gain :link 1))
    :events {:runner-install {:req (req (and (has? target :type "Program")
                                             (= (:active-player @state) :runner)
                                             ;; only trigger when played a programm from heap
                                             (some #{:discard} (:previous-zone target))))
                              :msg (msg "draw a card") :effect (effect (draw 1))}}}


   "Gabriel Santiago: Consummate Professional"
   {:events {:successful-run {:msg "gain 2 [Credits]" :once :per-turn
                              :effect (effect (gain :credit 2)) :req (req (= target :hq))}}}

   "GRNDL: Power Unleashed"
   {:effect (effect (gain :credit 5 :bad-publicity 1))}

   "Haas-Bioroid: Engineering the Future"
   {:events {:corp-install {:once :per-turn :msg "gain 1 [Credits]"
                            :effect (effect (gain :credit 1))}}}

   "Haas-Bioroid: Stronger Together"
   {:events {:pre-ice-strength {:req (req (and (= (:type target) "ICE") (has? target :subtype "Bioroid")))
                                :effect (effect (ice-strength-bonus 1))}}}

   "Harmony Medtech: Biomedical Pioneer"
   {:effect (effect (lose :agenda-point-req 1) (lose :runner :agenda-point-req 1))}

   "Hayley Kaplan: Universal Scholar"
   {:events {:runner-install
             {:optional {:prompt (msg "Install another " (:type target) " from Grip?") :once :per-turn
                         :effect (req (let [type (:type target)]
                                        (resolve-ability
                                          state side
                                          {:prompt (msg "Choose a " type "to install")
                                           :choices (req (filter #(has? % :type type) (:hand runner)))
                                           :msg (msg "install " (:title target))
                                           :effect (effect (runner-install target))} card nil)))}}}}

   "Iain Stirling: Retired Spook"
   {:effect (effect (gain :link 1))
    :events {:runner-turn-begins {:req (req (> (:agenda-point corp) (:agenda-point runner)))
                                  :msg "to gain 2 [Credits]" :effect (effect (gain :credit 2))}}}

   "Industrial Genomics: Growing Solutions"
   {:events {:pre-trash {:effect (effect (trash-cost-bonus
                                           (count (filter #(not (:seen %)) (:discard corp)))))}}}

   "Jinteki: Personal Evolution"
   {:events {:agenda-scored {:msg "do 1 net damage" :effect (effect (damage :net 1 {:card card}))}
             :agenda-stolen {:msg "do 1 net damage" :effect (effect (damage :net 1 {:card card}))}}}

   "Jinteki Biotech: Life Imagined"
   {:events {:pre-first-turn {:req (req (= side :corp))
                              :prompt "Choose a copy of Jinteki Biotech to use this game"
                              :choices ["The Brewery" "The Tank" "The Greenhouse"]
                              :effect (effect (update! (assoc card :biotech-target target))
                                              (system-msg (str "has chosen a copy of Jinteki Biotech for this game ")))}}
    :abilities [{:cost [:click 3]
                 :req (req (not (:biotech-used card)))
                 :effect (req (let [flip (:biotech-target card)]
                                (update! state side (assoc card :biotech-used true))
                                (case flip
                                  "The Brewery"
                                  (do (system-msg state side "uses The Brewery to do 2 net damage")
                                      (damage state side :net 2 {:card card}))
                                  "The Tank"
                                  (do (system-msg state side "uses The Tank to shuffle Archives into R&D")
                                      (shuffle-into-deck state side :discard))
                                  "The Greenhouse"
                                  (do (system-msg state side (str "uses The Greenhouse to place 4 advancement tokens "
                                                                  "on a card that can be advanced"))
                                      (resolve-ability
                                        state side
                                        {:prompt "Choose a card that can be advanced"
                                         :choices {:req #(or (= (:advanceable %) "always")
                                                             (and (= (:advanceable %) "while-rezzed") (:rezzed %))
                                                             (= (:type %) "Agenda"))}
                                         :effect (effect (add-prop target :advance-counter 4))} card nil)))))}]}

   "Kate \"Mac\" McCaffrey: Digital Tinker"
   {:effect (effect (gain :link 1))
    :events {:pre-install {:req (req (and (#{"Hardware" "Program"} (:type target))
                                          (not (get-in @state [:per-turn (:cid card)]))))
                           :effect (effect (install-cost-bonus -1))}  
             :runner-install {:req (req (and (#{"Hardware" "Program"} (:type target))
                                             (not (get-in @state [:per-turn (:cid card)]))))
                              :msg (msg "reduce the install cost of " (:title target) " by 1 [Credits]")
                              :effect (req (swap! state assoc-in [:per-turn (:cid card)] true))}}}

   "Ken \"Express\" Tenma: Disappeared Clone"
   {:events {:play-event {:req (req (has? target :subtype "Run")) :once :per-turn
                          :msg "gain 1 [Credits]" :effect (effect (gain :credit 1))}}}

   "Leela Patel: Trained Pragmatist"
   {:events {:agenda-scored {:choices {:req #(not (:rezzed %))} :msg "add 1 unrezzed card to HQ"
                             :player :runner :effect (effect (move :corp target :hand))}
             :agenda-stolen {:choices {:req #(not (:rezzed %))} :msg "add 1 unrezzed card to HQ"
                             :effect (req (move state :corp target :hand)
                                          (swap! state update-in [:runner :prompt] rest)
                                          (handle-end-run state side))}}}

   "MaxX: Maximum Punk Rock"
   {:events {:runner-turn-begins {:msg "trash the top 2 cards from Stack and draw 1 card"
                                  :effect (effect (mill 2) (draw))}}}

   "Nasir Meidan: Cyber Explorer"
   {:effect (effect (gain :link 1))}

   "NBN: Making News"
   {:recurring 2}

   "NBN: The World is Yours*"
   {:effect (effect (gain :max-hand-size 1))}

   "Near-Earth Hub: Broadcast Center"
   {:events {:server-created {:msg "draw 1 card" :once :per-turn :effect (effect (draw 1))}}}

   "Nisei Division: The Next Generation"
   {:events {:psi-game {:msg "gain 1 [Credits]" :effect (effect (gain :corp :credit 1))}}}

   "Noise: Hacker Extraordinaire"
   {:events {:runner-install {:msg "force the Corp to trash the top card of R&D" :effect (effect (mill :corp))
                              :req (req (has? target :subtype "Virus"))}}}

   "Quetzal: Free Spirit"
   {:abilities [{:once :per-turn :msg "break 1 barrier subroutine"}]}

   "Reina Roja: Freedom Fighter"
   {:effect (effect (gain :link 1))
    :events {:pre-rez
             {:req (req (= (:type target) "ICE")) :once :per-turn
              :effect (effect (rez-cost-bonus 1))}}}

   "Silhouette: Stealth Operative"
   {:events {:successful-run
             {:req (req (= target :hq)) :once :per-turn
              :effect (effect (resolve-ability {:choices {:req #(= (first (:zone %)) :servers)}
                                                :effect (effect (expose target)) :msg "expose 1 card"}
                                               card nil))}}}

   "Tennin Institute: The Secrets Within"
   {:abilities [{:msg "add 1 advancement counter on a card" :choices {:req #(= (first (:zone %)) :servers)}
                 :req (req (not (:successful-run runner-reg))) :once :per-turn
                 :effect (effect (add-prop target :advance-counter 1))}]}

   "The Foundry: Refining the Process"
   {:events
    {:rez {:req (req (and (= (:type target) "ICE") (some #(= (:title %) (:title target)) (:deck corp))))
           :optional
                {:prompt "Add another copy to HQ?" :once :per-turn
                 :effect (effect (move (some #(when (= (:title %) (:title target)) %) (:deck corp)) :hand)
                                 (shuffle! :deck))
                 :msg (msg "add a copy of " (:title target) " from R&D to HQ")}}}}

   "Titan Transnational: Investing In Your Future"
   {:events {:agenda-scored {:msg (msg "add 1 agenda counter to " (:title target))
                             :effect (effect (add-prop target :counter 1))}}}

   "Valencia Estevez: The Angel of Cayambe"
   {:effect (effect (gain :corp :bad-publicity 1))}

   "Weyland Consortium: Because We Built It"
   {:recurring 1}

   "Weyland Consortium: Building a Better World"
   {:events {:play-operation {:msg "gain 1 [Credits]" :effect (effect (gain :credit 1))
                              :req (req (has? target :subtype "Transaction"))}}}

   "Whizzard: Master Gamer"
   {:recurring 3}})