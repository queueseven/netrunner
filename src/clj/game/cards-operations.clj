(in-ns 'game.core)

(def cards-operations
  {"Aggressive Negotiation"
   {:req (req (:scored-agenda corp-reg)) :prompt "Choose a card" :choices (req (:deck corp))
    :effect (effect (move target :hand) (shuffle! :deck))}

   "Anonymous Tip"
   {:effect (effect (draw 3))}

   "Archived Memories"
   {:prompt "Choose a card from Archives" :choices (req (:discard corp))
    :effect (effect (move target :hand)
                    (system-msg (str "adds " (if (:seen target) (:title target) "a card") " to HQ")))}

   "Back Channels"
   {:prompt "Choose an installed card in a server to trash" :choices {:req #(= (last (:zone %)) :content)}
    :effect (effect (gain :credit (* 3 (:advance-counter target))) (trash target))
    :msg (msg "trash " (if (:rezzed target) (:title target) " a card") " and gain "
              (* 3 (:advance-counter target)) " [Credits]")}

   "Bad Times"
   {:req (req tagged)}

   "Beanstalk Royalties"
   {:effect (effect (gain :credit 3))}

   "Big Brother"
   {:req (req tagged) :effect (effect (gain :runner :tag 2))}

   "Bioroid Efficiency Research"
   {:choices {:req #(and (= (:type %) "ICE") (has? % :subtype "Bioroid") (not (:rezzed %)))}
    :msg (msg "rez " (:title target) " at no cost")
    :effect (effect (rez target {:no-cost true})
                    (host (get-card state target) (assoc card :zone [:discard] :seen true)))}

   "Biotic Labor"
   {:effect (effect (gain :click 2))}

   "Blue Level Clearance"
   {:effect (effect (gain :credit 5) (draw 2))}

   "Casting Call"
   {:choices {:req #(and (:type % "Agenda") (= (:zone %) [:hand]))}
    :effect (req (let [agenda target]
                   (resolve-ability
                     state side {:prompt (str "Choose a server to install " (:title agenda))
                                 :choices (server-list state agenda)
                                 :effect (req (corp-install state side agenda target {:install-state :face-up})
                                              ; find where the agenda ended up and host on it
                                              (let [agenda (some #(when (= (:cid %) (:cid agenda)) %)
                                                                 (all-installed state :corp))]
                                                ; the operation ends up in :discard when it is played; to host it,
                                                ; we need (host) to look for it in discard.
                                                (host state side agenda (assoc card :zone [:discard]
                                                                                    :seen true :installed true))
                                                (system-msg state side
                                                            (str "hosts Casting Call on " (:title agenda)))))}
                     card nil)))
    :events {:access {:req (req (= (:cid target) (:cid (:host card))))
                      :effect (effect (gain :runner :tag 2)) :msg "give the Runner 2 tags"}}}

   "Celebrity Gift"
   {:choices {:max 5 :req #(and (:side % "Corp") (= (:zone %) [:hand]))}
    :msg (msg "reveal " (join ", " (map :title targets)) " and gain " (* 2 (count targets)) " [Credits]")
    :effect (effect (gain :credit (* 2 (count targets))))}

   "Cerebral Cast"
   {:psi {:not-equal {:player :runner :prompt "Take 1 tag or 1 brain damage?"
                      :choices ["1 tag" "1 brain damage"] :msg (msg "The Runner takes " target)
                      :effect (req (if (= target "1 tag")
                                     (gain state side :tag 1)
                                     (damage state side :brain 1 {:card card})))}}}

   "Cerebral Static"
   {:msg "disable the Runner's identity"
    :effect (req (unregister-events state side (:identity runner)))
    :leave-play (req (when-let [events (:events (card-def (:identity runner)))]
                       (register-events state side events (:identity runner))))}

   "Closed Accounts"
   {:req (req tagged) :effect (effect (lose :runner :credit :all))}

   "Commercialization"
   {:msg (msg "gain " (:advance-counter target) " [Credits]")
    :choices {:req #(has? % :type "ICE")} :effect (effect (gain :credit (:advance-counter target)))}

   "Corporate Shuffle"
   {:effect (effect (shuffle-into-deck :hand) (draw 5))}

   "Cyberdex Trial"
   {:effect (effect (purge))}

   "Defective Brainchips"
   {:events {:pre-damage {:req (req (= target :brain)) :msg "to do 1 additional brain damage"
                          :once :per-turn :effect (effect (damage-bonus :brain 1))}}}

   "Diversified Portfolio"
   {:effect (effect (gain :credit (count (filter #(not (empty? (:content %)))
                                                 (get-in corp [:servers :remote])))))}

   "Fast Track"
   {:prompt "Choose an Agenda" :choices (req (filter #(has? % :type "Agenda") (:deck corp)))
    :effect (effect (system-msg (str "adds " (:title target) " to HQ and shuffle R&D"))
                    (move target :hand) (shuffle! :deck))}

   "Foxfire"
   {:trace {:base 7 :prompt "Choose 1 card to trash" :not-distinct true
            :choices {:req #(and (:installed %)
                                 (or (has? % :subtype "Virtual") (has? % :subtype "Link")))}
            :msg (msg "trash " (:title target)) :effect (effect (trash target))}}

   "Freelancer"
   {:req (req tagged) :msg (msg "trash " (join ", " (map :title targets)))
    :choices {:max 2 :req #(and (:installed %) (= (:type %) "Resource"))}
    :effect (effect (trash-cards :runner targets))}

   "Green Level Clearance"
   {:effect (effect (gain :credit 3) (draw))}

   "Hedge Fund"
   {:effect (effect (gain :credit 9))}

   "Hellion Alpha Test"
   {:req (req (:installed-resource runner-reg))
    :trace {:base 2 :choices {:req #(and (:installed %) (= (:type %) "Resource"))}
            :msg (msg "add " (:title target) " to the top of the Stack")
            :effect (effect (move :runner target :deck true))
            :unsuccessful {:msg "take 1 bad publicity" :effect (effect (gain :corp :bad-publicity 1))}}}

   "Housekeeping"
   {:events {:runner-install {:req (req (= side :runner)) :choices (req (:hand runner))
                              :prompt "Choose a card to trash for Housekeeping" :once :per-turn
                              :msg (msg "to force the Runner to trash " (:title target) " from Grip")
                              :effect (effect (trash target))}}}

   "Interns"
   {:prompt "Install a card from Archives or HQ?" :choices ["Archives" "HQ"]
    :msg (msg "install a card from " target)
    :effect (effect (resolve-ability
                      {:prompt "Choose a card to install"
                       :choices (req (filter #(not= (:type %) "Operation")
                                             ((if (= target "HQ") :hand :discard) corp)))
                       :effect (effect (corp-install target nil {:no-install-cost true}))}
                      card targets))}

   "Invasion of Privacy"
   {:trace {:base 2 :msg (msg "reveal the Runner's Grip and trash up to " (- target (second targets)) " resources or events")
            :effect (req (doseq [c (:hand runner)]
                           (move state side c :play-area)))
            :unsuccessful {:msg "take 1 bad publicity" :effect (effect (gain :corp :bad-publicity 1))}}}

   "Lag Time"
   {:events {:pre-ice-strength {:effect (effect (ice-strength-bonus 1))}}
    :leave-play {:effect (effect (update-all-ice))}}

   "Manhunt"
   {:events {:successful-run {:req (req (first-event state side :successful-run))
                              :trace {:base 2 :msg "give the Runner 1 tag"
                                      :effect (effect (gain :runner :tag 1))}}}}

   "Medical Research Fundraiser"
   {:effect (effect (gain :credit 8) (gain :runner :credit 3))}

   "Midseason Replacements"
   {:req (req (:stole-agenda runner-reg))
    :trace {:base 6 :msg (msg "give the Runner " (- target (second targets)) " tags")
            :effect (effect (gain :runner :tag (- target (second targets))))}}

   "Mushin No Shin"
   {:prompt "Choose a card to install"
    :choices (req (filter #(#{"Asset" "Agenda" "Upgrade"} (:type %)) (:hand corp)))
    :effect (effect (corp-install (assoc target :advance-counter 3) "New remote"))}

   "Neural EMP"
   {:req (req (:made-run runner-reg)) :effect (effect (damage :net 1 {:card card}))}

   "Oversight AI"
   {:choices {:req #(and (= (:type %) "ICE") (not (:rezzed %)))}
    :msg (msg "rez " (:title target) " at no cost")
    :effect (effect (rez target {:no-cost true})
                    (host (get-card state target) (assoc card :zone [:discard] :seen true)))}

   "Patch"
   {:choices {:req #(and (= (:type %) "ICE") (:rezzed %))}
    :effect (effect (host target (assoc card :zone [:discard] :seen true :installed true))
                    (update-ice-strength (get-card state target)))
    :events {:pre-ice-strength {:req (req (= (:cid target) (:cid (:host card))))
                                :effect (effect (ice-strength-bonus 2))}}}

   "Paywall Implementation"
   {:events {:successful-run {:msg "gain 1 [Credits]" :effect (effect (gain :corp :credit 1))}}}

   "Peak Efficiency"
   {:effect (effect (gain :credit
                          (reduce (fn [c server]
                                    (+ c (count (filter (fn [ice] (:rezzed ice)) (:ices server)))))
                                  0 (flatten (seq (:servers corp))))))}

   "Precognition"
   {:effect (req (doseq [c (take 5 (:deck corp))] (move state side c :play-area)))}

   "Power Grid Overload"
   {:trace {:base 2 :msg (msg "trash 1 piece of hardware with install cost less than or equal to "
                              (- target (second targets)))
            :effect (req (let [max-cost (- target (second targets))]
                           (resolve-ability state side
                                            {:choices {:req #(and (has? % :type "Hardware")
                                                                  (<= (:cost %) max-cost))}
                                             :msg (msg "trash " (:title target))
                                             :effect (effect (trash target))}
                                            card nil)))}}

   "Power Shutdown"
   {:req (req (:made-run runner-reg)) :prompt "Trash how many cards from the top R&D?"
    :choices {:number (req (count (:deck corp)))}
    :msg (msg "trash " target " cards from the top of R&D")
    :effect (req (mill state :corp target)
                 (let [n target]
                   (resolve-ability state :runner
                                    {:prompt "Choose a Program or piece of Hardware to trash"
                                     :choices {:req #(and (#{"Hardware" "Program"} (:type %))
                                                          (<= (:cost %) n))}
                                     :msg (msg "trash " (:title target)) :effect (effect (trash target))}
                                    card nil)))}

   "Predictive Algorithm"
   {:events {:pre-steal-cost {:effect (effect (steal-cost-bonus [:credit 2]))}}}

   "Psychographics"
   {:req (req tagged) :choices :credit :prompt "How many credits?"
    :effect (req (let [c (min target (:tag runner))]
                   (resolve-ability state side
                                    {:msg (msg "place " c " advancement tokens on "
                                               (if (:rezzed target) (:title target) "a card"))
                                     :choices {:req #(or (= (:type %) "Agenda") (:advanceable %))}
                                     :effect (effect (add-prop target :advance-counter c))} card nil)))}

   "Punitive Counterstrike"
   {:trace {:base 5 :msg (msg "do " (:stole-agenda runner-reg) " meat damage")
            :effect (effect (damage :meat (get-in runner [:register :stole-agenda]) {:card card}))}}

   "Reclamation Order"
   {:prompt "Choose a card from Archives" :msg (msg "add copies of " (:title target) " to HQ")
    :choices (req (filter #(not= (:title %) "Reclamation Order") (:discard corp)))
    :effect (req (doseq [c (filter #(= (:title target) (:title %)) (:discard corp))]
                   (move state side c :hand)))}

   "Recruiting Trip"
   (let [rthelp (fn rt [total left selected] 
                  (if (> left 0) 
                    {:prompt (str "Select a sysop (" (inc (- total left)) "/" total ")")
                     :choices (req (filter #(and (has? % :subtype "Sysop")
                                                 (not (some #{(:title %)} selected))) (:deck corp)))
                     :msg (msg "put " (:title target) " into HQ") 
                     :effect (req (move state side target :hand)
                                  (resolve-ability 
                                    state side 
                                    (rt total (dec left) (cons (:title target) selected)) 
                                    card nil))}
                    {:effect (req (shuffle! state :corp :deck))
                     :msg (msg "shuffle R&D")}))]
   {:prompt "How many sysops?" :choices :credit :msg (msg "search for " target " sysops")
    :effect (effect (resolve-ability (rthelp target target []) card nil))})
   
   "Restoring Face"
   {:prompt "Choose a Sysop, Executive or Clone to trash"
    :msg (msg "trash " (:title target) " to remove 2 bad publicity")
    :choices (req (filter #(and (:rezzed %)
                                (or (has? % :subtype "Clone") (has? % :subtype "Executive")
                                    (has? % :subtype "Sysop")))
                          (mapcat :content (flatten (seq (:servers corp))))))
    :effect (effect (lose :bad-publicity 2) (trash target))}

   "Restructure"
   {:effect (effect (gain :credit 15))}

   "Reuse"
   {:choices {:max 100 :req #(and (:side % "Corp") (= (:zone %) [:hand]))}
    :msg (msg "trash " (count targets) " card" (if (not= 1 (count targets)) "s") " and gain " (* 2 (count targets)) " [Credits]")
    :effect (effect (trash-cards targets) (gain :credit (* 2 (count targets))))}

   "Rework"
   {:prompt "Choose a card to shuffle into R&D" :choices (req (:hand corp))
    :effect (effect (move target :deck) (shuffle! :deck))}

   "Scorched Earth"
   {:req (req tagged) :effect (effect (damage :meat 4 {:card card}))}

   "SEA Source"
   {:req (req (:successful-run runner-reg))
    :trace {:base 3 :msg "give the Runner 1 tag" :effect (effect (gain :runner :tag 1))}}

   "Shipment from Kaguya"
   {:choices {:max 2 :req #(or (= (:advanceable %) "always")
                               (and (= (:advanceable %) "while-rezzed") (:rezzed %))
                               (= (:type %) "Agenda"))}
    :msg (msg "1 advancement tokens on " (count targets) " cards")
    :effect (req (doseq [t targets] (add-prop state :corp t :advance-counter 1)))}

   "Shipment from SanSan"
   {:choices ["0", "1", "2"] :prompt "How many advancement tokens?"
    :effect (req (let [c (Integer/parseInt target)]
                   (resolve-ability
                     state side
                     {:choices {:req #(or (= (:advanceable %) "always")
                                          (and (= (:advanceable %) "while-rezzed") (:rezzed %))
                                          (= (:type %) "Agenda"))}
                      :msg (msg "add " c " advancement tokens on a card")
                      :effect (effect (add-prop :corp target :advance-counter c))} card nil)))}

   "Shoot the Moon"
   {:choices {:req #(and (= (:type %) "ICE") (not (:rezzed %)))
              :max (req (min (:tag runner)
                             (reduce (fn [c server]
                                       (+ c (count (filter #(not (:rezzed %)) (:ices server)))))
                                     0 (flatten (seq (:servers corp))))))}
    :req (req tagged)
    :effect (req (doseq [t targets] (rez state side t {:no-cost true})))}

   "Snatch and Grab"
   {:trace {:base 3 :choices {:req #(has? % :subtype "Connection")}
            :msg (msg "attempt to trash " (:title target))
            :effect (req (let [c target]
                           (resolve-ability
                             state side
                             {:prompt (msg "Take 1 tag to prevent " (:title c) " from being trashed?")
                              :choices ["Yes" "No"] :player :runner
                              :effect (effect (resolve-ability
                                                (if (= target "Yes")
                                                  {:msg (msg "take 1 tag to prevent " (:title c)
                                                             " from being trashed")
                                                   :effect (effect (gain :runner :tag 1))}
                                                  {:effect (trash state side c) :msg (msg "trash " (:title c))})
                                                card nil))}
                             card nil)))}}

   "Sub Boost"
   {:choices {:req #(and (= (:type %) "ICE") (:rezzed %))}
    :effect (effect (host target (assoc card :zone [:discard] :seen true)))}

   "Subliminal Messaging"
   {:effect (effect (gain :credit 1)
                    (resolve-ability {:once :per-turn :once-key :subliminal-messaging
                                      :effect (effect (gain :corp :click 1))} card nil))}

   "Successful Demonstration"
   {:req (req (:unsuccessful-run runner-reg)) :effect (effect (gain :credit 7))}

   "Sweeps Week"
   {:effect (effect (gain :credit (count (:hand runner))))}

   "Traffic Accident"
   {:req (req (>= (:tag runner) 2)) :effect (effect (damage :meat 2 {:card card}))}

   "Trick of Light"
   {:choices {:req #(and (contains? % :advance-counter) (> (:advance-counter %) 0))}
    :effect
             (req (let [fr target tol card]
                    (resolve-ability
                      state side
                      {:prompt "Move how many advancement tokens?"
                       :choices (take (inc (:advance-counter fr)) ["0" "1" "2"])
                       :effect (req (let [c (Integer/parseInt target)]
                                      (resolve-ability
                                        state side
                                        {:prompt  "Move to where?"
                                         :choices {:req #(and (not= (:cid fr) (:cid %))
                                                              (or (= (:advanceable %) "always")
                                                                  (and (= (:advanceable %) "while-rezzed") (:rezzed %))
                                                                  (= (:type %) "Agenda")))}
                                         :effect  (effect (add-prop :corp target :advance-counter c)
                                                          (add-prop :corp fr :advance-counter (- c))
                                                          (system-msg (str "moves " c " advancement tokens from "
                                                                           (if (:rezzed fr) (:title fr) "a card") " to "
                                                                           (if (:rezzed target) (:title target) "a card"))))}
                                        tol nil)))}
                      card nil)))}

   "Witness Tampering"
   {:effect (effect (lose :bad-publicity 2))}})
