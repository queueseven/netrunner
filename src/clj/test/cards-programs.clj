(in-ns 'test.core)

(deftest datasucker
  "Datasucker - Reduce strength of encountered ICE"
  (do-game
    (new-game (default-corp [(qty "Fire Wall" 1)])
              (default-runner [(qty "Datasucker" 1)]))
    (play-from-hand state :corp "Fire Wall" "New remote")
    (take-credits state :corp)
    (core/gain state :runner :click 3)
    (play-from-hand state :runner "Datasucker")
    (let [ds (get-in @state [:runner :rig :program 0])
          fw (get-ice state :remote1 0)]
      (run-empty-server state "Archives")
      (is (= 1 (:counter (refresh ds))))
      (run-empty-server state "Archives")
      (is (= 2 (:counter (refresh ds))))
      (run-on state "Server 1")
      (run-continue state)
      (run-successful state)
      (is (= 2 (:counter (refresh ds))) "No counter gained, not a central server")
      (run-on state "Server 1")
      (core/rez state :corp fw)
      (is (= 5 (:current-strength (refresh fw))))
      (card-ability state :runner ds 0)
      (is (= 1 (:counter (refresh ds))) "1 counter spent from Datasucker")
      (is (= 4 (:current-strength (refresh fw))) "Fire Wall strength lowered by 1"))))

(deftest diwan
  "Diwan - Full test"
  (do-game
    (new-game (default-corp [(qty "Ice Wall" 3) (qty "Fire Wall" 3) (qty "Crisium Grid" 2)])
              (default-runner [(qty "Diwan" 1)]))
    (take-credits state :corp)
    (play-from-hand state :runner "Diwan")
    (prompt-choice :runner "HQ")
    (take-credits state :runner)
    (is (= 8 (:credit (get-corp))) "8 credits for corp at start of second turn")
    (play-from-hand state :corp "Ice Wall" "R&D")
    (is (= 8 (:credit (get-corp))) "Diwan did not charge extra for install on another server")
    (play-from-hand state :corp "Ice Wall" "HQ")
    (is (= 7 (:credit (get-corp))) "Diwan charged 1cr to install ice protecting the named server")
    (play-from-hand state :corp "Crisium Grid" "HQ")
    (is (= 6 (:credit (get-corp))) "Diwan charged 1cr to install in the named server")
    (take-credits state :corp)
    (take-credits state :runner)
    (play-from-hand state :corp "Ice Wall" "HQ")
    (is (= 4 (:credit (get-corp))) "Diwan charged 1cr + 1cr to install a second ice protecting the named server")
    (core/gain state :corp :click 1)
    (core/purge state :corp)
    (play-from-hand state :corp "Fire Wall" "HQ") ; 2cr cost from normal install cost
    (is (= "Diwan" (-> (get-runner) :discard first :title)) "Diwan was trashed from purge")
    (is (= 2 (:credit (get-corp))) "No charge for installs after Diwan purged")))

(deftest djinn-host-chakana
  "Djinn - Hosted Chakana does not disable advancing agendas. Issue #750"
  (do-game
    (new-game (default-corp [(qty "Priority Requisition" 1)])
              (default-runner [(qty "Djinn" 1) (qty "Chakana" 1)]))
    (play-from-hand state :corp "Priority Requisition" "New remote")
    (take-credits state :corp 2)
    (play-from-hand state :runner "Djinn")
    (let [djinn (get-in @state [:runner :rig :program 0])
          agenda (get-content state :remote1 0)]
      (is agenda "Agenda was installed")
      (card-ability state :runner djinn 1)
      (prompt-select :runner (find-card "Chakana" (:hand (get-runner))))
      (let [chak (first (:hosted (refresh djinn)))]
        (is (= "Chakana" (:title chak)) "Djinn has a hosted Chakana")
        ;; manually add 3 counters
        (core/add-prop state :runner (first (:hosted (refresh djinn))) :counter 3)
        (take-credits state :runner 2)
        (core/advance state :corp {:card agenda})
        (is (= 1 (:advance-counter (refresh agenda))) "Agenda was advanced")))))

(deftest djinn-host-program
  "Djinn - Host a non-icebreaker program"
  (do-game
    (new-game (default-corp)
              (default-runner [(qty "Djinn" 1) (qty "Chakana" 1)]))
    (take-credits state :corp)
    (play-from-hand state :runner "Djinn")
    (is (= 3 (:memory (get-runner))))
    (let [djinn (get-in @state [:runner :rig :program 0])]
      (card-ability state :runner djinn 1)
      (prompt-select :runner (find-card "Chakana" (:hand (get-runner))))
      (is (= 3 (:memory (get-runner))) "No memory used to host on Djinn")
      (is (= "Chakana" (:title (first (:hosted (refresh djinn))))) "Djinn has a hosted Chakana")
      (is (= 1 (:credit (get-runner))) "Full cost to host on Djinn"))))

(deftest djinn-tutor-virus
  "Djinn - Tutor a virus program"
  (do-game
    (new-game (default-corp)
              (default-runner [(qty "Djinn" 1) (qty "Parasite" 1)]))
    (take-credits state :corp)
    (play-from-hand state :runner "Djinn")
    (core/move state :runner (find-card "Parasite" (:hand (get-runner))) :deck)
    (is (zero? (count (:hand (get-runner)))) "No cards in hand after moving Parasite to deck")
    (let [djinn (get-in @state [:runner :rig :program 0])]
      (card-ability state :runner djinn 0)
      (prompt-card :runner (find-card "Parasite" (:deck (get-runner))))
      (is (= "Parasite" (:title (first (:hand (get-runner))))) "Djinn moved Parasite to hand")
      (is (= 2 (:credit (get-runner))) "1cr to use Djinn ability")
      (is (= 2 (:click (get-runner))) "1click to use Djinn ability"))))

(deftest harbinger-blacklist
  "Harbinger - install facedown when Blacklist installed"
  (do-game
    (new-game (default-corp [(qty "Blacklist" 1)])
              (default-runner [(qty "Harbinger" 1)]))
    (play-from-hand state :corp "Blacklist" "New remote")
    (core/rez state :corp (get-content state :remote1 0) )
    (take-credits state :corp)
    (play-from-hand state :runner "Harbinger")
    (core/trash state :runner (-> (get-runner) :rig :program first))
    (is (= 0 (count (:discard (get-runner)))) "Harbinger not in heap")
    (is (-> (get-runner) :rig :facedown first :facedown) "Harbinger installed facedown")))

(deftest incubator-transfer-virus-counters
  "Incubator - Gain 1 virus counter per turn; trash to move them to an installed virus program"
  (do-game
    (new-game (default-corp)
              (default-runner [(qty "Incubator" 1) (qty "Datasucker" 1)]))
    (take-credits state :corp)
    (play-from-hand state :runner "Datasucker")
    (play-from-hand state :runner "Incubator")
    (take-credits state :runner)
    (take-credits state :corp)
    (let [ds (get-in @state [:runner :rig :program 0])
          incub (get-in @state [:runner :rig :program 1])]
      (is (= 1 (:counter (refresh incub))) "Incubator gained 1 virus counter")
      (take-credits state :runner)
      (take-credits state :corp)
      (is (= 2 (:counter (refresh incub))) "Incubator has 2 virus counters")
      (card-ability state :runner incub 0)
      (prompt-select :runner ds)
      (is (= 2 (:counter (refresh ds))) "Datasucker has 2 virus counters moved from Incubator")
      (is (= 1 (count (get-in @state [:runner :rig :program]))))
      (is (= 1 (count (:discard (get-runner)))) "Incubator trashed")
      (is (= 3 (:click (get-runner)))))))

(deftest leprechaun-mu-savings
  "Leprechaun - Keep MU the same when hosting or trashing hosted programs"
  (do-game
    (new-game (default-corp)
              (default-runner [(qty "Leprechaun" 1) (qty "Hyperdriver" 1) (qty "Imp" 1)]))
    (take-credits state :corp)
    (play-from-hand state :runner "Leprechaun")
    (let [lep (get-in @state [:runner :rig :program 0])]
      (card-ability state :runner lep 0)
      (prompt-select :runner (find-card "Hyperdriver" (:hand (get-runner))))
      (is (= 2 (:click (get-runner))))
      (is (= 2 (:credit (get-runner))))
      (is (= 3 (:memory (get-runner))) "Hyperdriver 3 MU not deducted from available MU")
      (card-ability state :runner lep 0)
      (prompt-select :runner (find-card "Imp" (:hand (get-runner))))
      (is (= 1 (:click (get-runner))))
      (is (= 0 (:credit (get-runner))))
      (is (= 3 (:memory (get-runner))) "Imp 1 MU not deducted from available MU")
      ;; Trash Hyperdriver
      (core/move state :runner (find-card "Hyperdriver" (:hosted (refresh lep))) :discard)
      (is (= 3 (:memory (get-runner))) "Hyperdriver 3 MU not added to available MU")
      (core/move state :runner (find-card "Imp" (:hosted (refresh lep))) :discard) ; trash Imp
      (is (= 3 (:memory (get-runner))) "Imp 1 MU not added to available MU"))))

(deftest magnum-opus-click
  "Magnum Opus - Gain 2 cr"
  (do-game
    (new-game (default-corp)
              (default-runner [(qty "Magnum Opus" 1)]))
    (take-credits state :corp)
    (play-from-hand state :runner "Magnum Opus")
    (is (= 2 (:memory (get-runner))))
    (is (= 0 (:credit (get-runner))))
    (let [mopus (get-in @state [:runner :rig :program 0])]
      (card-ability state :runner mopus 0)
      (is (= 2 (:credit (get-runner))) "Gain 2cr"))))

(deftest origami
  "Origami - Increases Runner max hand size"
  (do-game
    (new-game (default-corp)
              (default-runner [(qty "Origami" 2)]))
    (take-credits state :corp)
    (play-from-hand state :runner "Origami")
    (is (= 6 (core/hand-size state :runner)))
    (play-from-hand state :runner "Origami")
    (is (= 9 (core/hand-size state :runner)) "Max hand size increased by 2 for each copy installed")))

(deftest paintbrush
  "Paintbrush - Give rezzed ICE a chosen subtype until the end of the next run"
  (do-game
    (new-game (default-corp [(qty "Ice Wall" 1)])
              (default-runner [(qty "Paintbrush" 1)]))
    (play-from-hand state :corp "Ice Wall" "HQ")
    (take-credits state :corp)
    (play-from-hand state :runner "Paintbrush")
    (is (= 2 (:memory (get-runner))))
    (let [iwall (get-ice state :hq 0)
          pb (get-in @state [:runner :rig :program 0])]
      (card-ability state :runner pb 0)
      (prompt-select :runner iwall)
      (is (= 3 (:click (get-runner))) "Ice Wall not rezzed, so no click charged")
      (prompt-choice :runner "Done") ; cancel out
      (core/rez state :corp iwall)
      (card-ability state :runner pb 0)
      (prompt-select :runner iwall)
      (prompt-choice :runner "Code Gate")
      (is (= 2 (:click (get-runner))) "Click charged")
      (is (= true (has? (refresh iwall) :subtype "Code Gate")) "Ice Wall gained Code Gate")
      (run-empty-server state "Archives")
      (is (= false (has? (refresh iwall) :subtype "Code Gate")) "Ice Wall lost Code Gate at the end of the run"))))

(deftest parasite-apex
  "Parasite - Installed facedown w/ Apex"
  (do-game
    (new-game (default-corp)
              (make-deck "Apex: Invasive Predator" [(qty "Parasite" 1)]))
    (take-credits state :corp)
    (prompt-select :runner (find-card "Parasite" (:hand (get-runner))))
    (is (empty? (:prompt (get-runner))) "No prompt to host Parasite")
    (is (= 1 (count (get-in @state [:runner :rig :facedown]))) "Parasite installed face down")))

(deftest parasite-architect
  "Parasite - Installed on untrashable Architect should keep gaining counters past 3 and make strength go negative"
  (do-game
    (new-game (default-corp [(qty "Architect" 3) (qty "Hedge Fund" 3)])
              (default-runner [(qty "Parasite" 3) (qty "Grimoire" 1)]))
    (play-from-hand state :corp "Architect" "HQ")
    (let [arch (get-ice state :hq 0)]
      (core/rez state :corp arch)
      (take-credits state :corp)
      (play-from-hand state :runner "Grimoire")
      (play-from-hand state :runner "Parasite")
      (prompt-select :runner arch)
      (let [psite (first (:hosted (refresh arch)))]
        (is (= 1 (:counter (refresh psite))) "Parasite has 1 counter")
        (take-credits state :runner)
        (take-credits state :corp)
        (take-credits state :runner)
        (take-credits state :corp)
        (take-credits state :runner)
        (take-credits state :corp)
        (is (= 4 (:counter (refresh psite))) "Parasite has 4 counters")
        (is (= -1 (:current-strength (refresh arch))) "Architect at -1 strength")))))

(deftest parasite-gain-counter
  "Parasite - Gain 1 counter every Runner turn"
  (do-game
    (new-game (default-corp [(qty "Wraparound" 3) (qty "Hedge Fund" 3)])
              (default-runner [(qty "Parasite" 3) (qty "Sure Gamble" 3)]))
    (play-from-hand state :corp "Wraparound" "HQ")
    (let [wrap (get-ice state :hq 0)]
      (core/rez state :corp wrap)
      (take-credits state :corp)
      (play-from-hand state :runner "Parasite")
      (prompt-select :runner wrap)
      (is (= 3 (:memory (get-runner))) "Parasite consumes 1 MU")
      (let [psite (first (:hosted (refresh wrap)))]
        (is (= 0 (:counter psite)) "Parasite has no counters yet")
        (take-credits state :runner)
        (take-credits state :corp)
        (is (= 1 (:counter (refresh psite)))
            "Parasite gained 1 virus counter at start of Runner turn")
        (is (= 6 (:current-strength (refresh wrap))) "Wraparound reduced to 6 strength")))))

(deftest parasite-hivemind-instant-ice-trash
  "Parasite - Use Hivemind counters when installed; instantly trash ICE if counters >= ICE strength"
  (do-game
    (new-game (default-corp [(qty "Enigma" 3) (qty "Hedge Fund" 3)])
              (default-runner [(qty "Parasite" 1)
                               (qty "Grimoire" 1)
                               (qty "Hivemind" 1)
                               (qty "Sure Gamble" 1)]))
    (play-from-hand state :corp "Enigma" "HQ")
    (let [enig (get-ice state :hq 0)]
      (core/rez state :corp enig)
      (take-credits state :corp)
      (play-from-hand state :runner "Sure Gamble")
      (play-from-hand state :runner "Grimoire")
      (play-from-hand state :runner "Hivemind")
      (let [hive (get-in @state [:runner :rig :program 0])]
        (is (= 2 (:counter (refresh hive))) "Hivemind has 2 counters")
        (play-from-hand state :runner "Parasite")
        (prompt-select :runner enig)
        (is (= 1 (count (:discard (get-corp)))) "Enigma trashed instantly")
        (is (= 4 (:memory (get-runner))))
        (is (= 2 (count (:discard (get-runner)))) "Parasite trashed when Enigma was trashed")))))

(deftest parasite-ice-trashed
  "Parasite - Trashed along with host ICE when its strength has been reduced to 0"
  (do-game
    (new-game (default-corp [(qty "Enigma" 3) (qty "Hedge Fund" 3)])
              (default-runner [(qty "Parasite" 3) (qty "Grimoire" 1)]))
    (play-from-hand state :corp "Enigma" "HQ")
    (let [enig (get-ice state :hq 0)]
      (core/rez state :corp enig)
      (take-credits state :corp)
      (play-from-hand state :runner "Grimoire")
      (play-from-hand state :runner "Parasite")
      (prompt-select :runner enig)
      (let [psite (first (:hosted (refresh enig)))]
        (is (= 1 (:counter (refresh psite))) "Parasite has 1 counter")
        (is (= 1 (:current-strength (refresh enig))) "Enigma reduced to 1 strength")
        (take-credits state :runner)
        (take-credits state :corp)
        (is (= 1 (count (:discard (get-corp)))) "Enigma trashed")
        (is (= 1 (count (:discard (get-runner)))) "Parasite trashed when Enigma was trashed")))))

(deftest progenitor-host-hivemind
  "Progenitor - Hosting Hivemind, using Virus Breeding Ground. Issue #738"
  (do-game
    (new-game (default-corp)
              (default-runner [(qty "Progenitor" 1) (qty "Virus Breeding Ground" 1) (qty "Hivemind" 1)]))
    (take-credits state :corp)
    (play-from-hand state :runner "Progenitor")
    (play-from-hand state :runner "Virus Breeding Ground")
    (is (= 4 (:memory (get-runner))))
    (let [prog (get-in @state [:runner :rig :program 0])
          vbg (get-in @state [:runner :rig :resource 0])]
      (card-ability state :runner prog 0)
      (prompt-select :runner (find-card "Hivemind" (:hand (get-runner))))
      (is (= 4 (:memory (get-runner))) "No memory used to host on Progenitor")
      (let [hive (first (:hosted (refresh prog)))]
        (is (= "Hivemind" (:title hive)) "Hivemind is hosted on Progenitor")
        (is (= 1 (:counter hive)) "Hivemind has 1 counter")
        (is (= 0 (:credit (get-runner))) "Full cost to host on Progenitor")
        (take-credits state :runner 1)
        (take-credits state :corp)
        (card-ability state :runner vbg 0) ; use VBG to transfer 1 token to Hivemind
        (prompt-select :runner hive)
        (is (= 2 (get (refresh hive) :counter 0)) "Hivemind gained 1 counter")
        (is (= 0 (get (refresh vbg) :counter 0)) "Virus Breeding Ground lost 1 counter")))))

(deftest sneakdoor-nerve-agent
  "Sneakdoor Beta - Allow Nerve Agent to gain counters. Issue #1158/#955"
  (do-game
    (new-game (default-corp)
              (default-runner [(qty "Sneakdoor Beta" 1) (qty "Nerve Agent" 1)]))
    (take-credits state :corp)
    (core/gain state :runner :credit 10)
    (play-from-hand state :runner "Nerve Agent")
    (play-from-hand state :runner "Sneakdoor Beta")
    (let [nerve (get-in @state [:runner :rig :program 0])
          sb (get-in @state [:runner :rig :program 1])]
      (card-ability state :runner sb 0)
      (run-successful state)
      (is (= 1 (:counter (refresh nerve))))
      (card-ability state :runner sb 0)
      (run-successful state)
      (is (= 2 (:counter (refresh nerve)))))))

(deftest sneakdoor-ash
  "Sneakdoor Beta - Gabriel Santiago, Ash on HQ should prevent Sneakdoor HQ access but still give Gabe credits.
  Issue #1138."
  (do-game
    (new-game (default-corp [(qty "Ash 2X3ZB9CY" 1)])
              (make-deck "Gabriel Santiago: Consummate Professional" [(qty "Sneakdoor Beta" 1)]))
    (play-from-hand state :corp "Ash 2X3ZB9CY" "HQ")
    (take-credits state :corp)
    (play-from-hand state :runner "Sneakdoor Beta")
    (is (= 1 (:credit (get-runner))) "Sneakdoor cost 4 credits")
    (let [sb (get-in @state [:runner :rig :program 0])
          ash (get-content state :hq 0)]
      (core/rez state :corp ash)
      (card-ability state :runner sb 0)
      (card-ability state :corp ash 0)
      (prompt-choice :corp 0)
      (prompt-choice :runner 0)
      (run-successful state)
      (is (= 3 (:credit (get-runner))) "Gained 2 credits from Gabe's ability")
      (is (= (:cid ash) (-> (get-runner) :prompt first :card :cid)) "Ash interrupted HQ access after Sneakdoor run")
      (is (= :hq (-> (get-runner) :register :successful-run first)) "Successful Run on HQ recorded"))))

(deftest sneakdoor-crisium
  "Sneakdoor Beta - do not switch to HQ if Archives has Crisium Grid. Issue #1229."
  (do-game
    (new-game (default-corp [(qty "Crisium Grid" 1) (qty "Priority Requisition" 1) (qty "Private Security Force" 1)])
              (default-runner [(qty "Sneakdoor Beta" 1)]))
    (play-from-hand state :corp "Crisium Grid" "Archives")
    (trash-from-hand state :corp "Priority Requisition")
    (take-credits state :corp)
    (play-from-hand state :runner "Sneakdoor Beta")
    (let [sb (get-in @state [:runner :rig :program 0])
          cr (get-content state :archives 0)]
      (core/rez state :corp cr)
      (card-ability state :runner sb 0)
      (run-successful state)
      (is (= :archives (get-in @state [:run :server 0])) "Crisium Grid stopped Sneakdoor Beta from switching to HQ"))))

(deftest sneakdoor-sectest
  "Sneakdoor Beta - Grant Security Testing credits on HQ."
  (do-game
    (new-game (default-corp)
              (default-runner [(qty "Security Testing" 1) (qty "Sneakdoor Beta" 1)]))
    (take-credits state :corp)
    (play-from-hand state :runner "Sneakdoor Beta")
    (play-from-hand state :runner "Security Testing")
    (take-credits state :runner)
    (is (= 3 (:credit (get-runner))))
    (take-credits state :corp)
    (let [sb (get-in @state [:runner :rig :program 0])]
      (prompt-choice :runner "HQ")
      (card-ability state :runner sb 0)
      (run-successful state)
      (is (not (:run @state)) "Switched to HQ and ended the run from Security Testing")
      (is (= 5 (:credit (get-runner))) "Sneakdoor switched to HQ and earned Security Testing credits"))))

(deftest surfer
  "Surfer - Swap position with ice before or after when encountering a barrier ice"
  (do-game
   (new-game (default-corp [(qty "Ice Wall" 1) (qty "Quandary" 1)])
             (default-runner [(qty "Surfer" 1)]))
   (play-from-hand state :corp "Quandary" "HQ")
   (play-from-hand state :corp "Ice Wall" "HQ")
   (take-credits state :corp)
   (play-from-hand state :runner "Surfer")
   (is (= 3 (:credit (get-runner))) "Paid 2 credits to install Surfer")
   (core/rez state :corp (get-ice state :hq 1))
   (run-on state "HQ")
   (is (= 2 (get-in @state [:run :position])) "Starting run at position 2")
   (let [surf (get-in @state [:runner :rig :program 0])]
     (card-ability state :runner surf 0)
     (prompt-select :runner (get-ice state :hq 0))
     (is (= 1 (:credit (get-runner))) "Paid 2 credits to use Surfer")
     (is (= 1 (get-in @state [:run :position])) "Now at next position (1)")
     (is (= "Ice Wall" (:title (get-ice state :hq 0))) "Ice Wall now at position 1"))))
