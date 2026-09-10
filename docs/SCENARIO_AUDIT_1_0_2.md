# Wardbound 1.0.2 — Player-journey scenario audit

This pass follows progression as a player rather than treating subsystems in isolation.

## Scenario A — fresh world -> first village

- Structure loot is still lazily valued on first interaction if an eager structure scan missed a village chunk.
- Player-placed/already-opened containers remain vanilla because they no longer have a loot table to value.
- Ward rolls remain the configured 45% / 55% low/high-value chance multiplied by the global ward-roll multiplier.
- Vanilla double chests now choose a deterministic ward owner before either half becomes active; clicking the lower-value half first can no longer alter which half defines the encounter.
- Introductory ordinary minigames suppress advanced narrative modifiers until the player has actually progressed into them.
- Failed games are recorded by the anti-repetition director too, so a new player who struggles with one discipline is not treated as though it was never seen.

## Scenario B — fail, ESC, crash/disconnect, retry

- ESC and unresolved GUI removal resolve as failure.
- The server-issued attempt flag prevents reopening a vanished screen as a free reroll.
- Mouse/key hold-release pairs survive corruption, Possession, hush and hybrid interruptions, preventing stuck controls in Pressure, Keyway, Balance and Vessel.
- Multiplayer ward leases can be inherited if the previous witness is offline; an online witness still owns the live attempt.

## Scenario C — early cards and field cards

- Normal post-ward card hands unlock at 15 resolved wards. The first otherwise-eligible ordinary ward at/after that threshold now guarantees the onboarding hand; subsequent hands return to the configured probability curve.
- Field-card drops begin after the field-card unlock and retain the 10-kill soft pity / 30-kill hard guarantee.
- The first field-card drop no longer speaks through an unidentified `???` voice. It remains a neutral card-system discovery until the late Master layer begins.
- Unclaimed field hands still advance Card Evolution, Ecology and hidden Lineage without secretly advancing a Master relationship.

## Scenario D — 90+ resolved wards but fewer than 120 opened field cards

This was the main cross-system pacing leak found in the audit.

- `MASTER`-kind private cards previously became legal from the resolved-ward shelf even while Curator, Notary and Gambler were still sealed behind field-card progression.
- The Master-card advancement and Ledger rules could likewise imply that the Master layer was already live.
- Attention could be introduced by a card whose own wording referenced it even if the player had not reached the Master phase.

Fix: the late dealer/Master layer now has one authoritative **dual gate**: at least 120 opened field cards **and** 120 resolved wards. A player can therefore farm Sealed Cards after their field unlock without using a mob farm as a shortcut into Curator/Notary/Gambler progression. Master-kind cards, Attention behavior, Master rules, refusal judgement and the Master advancement cannot activate before both keys are satisfied. Gauntlet completion was also hardened: it can no longer display the Attention discovery/message when the setter correctly rejects Attention before the Master phase.

## Scenario E — Master phase

- Ashen Curator, Mourning Notary and Pale Gambler remain peers: all enter only after the common 120-opened-field-card + 120-resolved-ward phase gate.
- Their identities still require the common 160-opened-field-card reveal shelf plus three real audiences with the specific dealer.
- Contracts and Curses no longer use the hidden Master advancement as their parent, because those card-system shelves can be reached independently of field-card Master pacing.
- Config schema v34 migrates the historical default `master_cards_after_beaten = 90` to 120 while leaving non-default custom values intact.

## Scenario F — Cthulhu's Game passage transitions

A shared lifecycle bug was found across the final exam. A 900 ms passage-title banner was displayed after the next mechanic had already started ticking. That could consume most of a memory preview, drain Rootway's first decision window, or let Pulse beats pass before the player had usable control.

Fix: each passage now has a real presentation grace. During that grace:

- the main ward clock is paused;
- corruption cadence is paused;
- the authored mechanic is frozen;
- HURRIED, hybrid-interruption and deception gameplay timers are frozen as well;
- the gameplay-elapsed clock is frozen too, so repeated passage titles cannot make a hybrid round trigger early or inflate completion telemetry;
- action input is swallowed rather than judged;
- the first phase gets a shorter opening grace and later passage titles get a 950 ms grace matching the title presentation.

This preserves difficulty while removing title-screen time theft.


## Scenario G — first unseen discipline after global progression has advanced

A player can reach 35+ total ward wins while still having one enabled minigame at mastery tier 0. Global progression must not turn that first encounter into an unreadable advanced lesson.

- Generic afflictions and hard narrative mutations require at least one real win in the selected discipline. Ordinary quirk/anomaly handwriting is only globally suppressed through the first 15 resolved wards; after that it can appear at reduced intensity even before Mastery I.
- Living Ward rolls use the same rule and are deferred until after encounter classification. They cannot roll onto a first lesson or accidentally stack with Possessed, Eldritch, Cthulhu, chain, gauntlet or depth encounters.
- Existing old-save affliction/living tags are suppressed at issue time when the attempt is a first clean lesson or authored special encounter.

## Scenario H — field-card farm / refusal abuse / legacy punishment

- 120 opened field cards without 120 resolved wards leaves the Master phase sealed.
- Refusing ordinary card hands before the Master phase increments only a neutral lifetime statistic; it cannot produce the 10/25/40 ominous hand messages or the 50-refusal Pale Gambler judgement.
- The old punishment state that refreshed Strength XX indefinitely is migrated away. The post-judgement Strength XX effect is now a single short aftershock and the punishment state clears immediately.
- Black Anomaly cannot choose an Attention-only outcome while Attention itself is progression-sealed, avoiding a hostile card result that says something happened while the central setter correctly rejects it.

## Scenario I — double chest, telemetry and late UI leakage

- A double chest with both halves valued binds to the richer value (stable coordinate tie-break on equality), preventing the low-value half from defining difficulty for a linked 54-slot reward.
- Every issued attempt stores the exact final difficulty after mastery, memory, mutation, special-ward, affliction, Attention, imprint and other modifiers. Success/failure telemetry consumes that snapshot instead of reconstructing an incomplete approximation later.
- Attention and Master-thread analysis/catalogue sections stay hidden while the dual Master phase is sealed, including on saves carrying stale pre-hardening history.

## Existing minigame hardening retained

- Cipher, Lattice, Shardsong, Orrery and Procession validate real solution depth rather than scramble count.
- Constellation preserves successful route rendering and does not replay the complete answer after a miss.
- Pressure has leak, pump, vent, seal and redline/burst states.
- Keyway uses hidden shear feedback with separated false-set plateaus.
- Augury rewrites after exhausting an omen's attempt budget.
- Rootway uses truthful route rules and timed decisions.
- Mirror uses fully asymmetric glyphs so reflected answers cannot collapse into identical visuals.
- Black Measure excludes the two-pour tutorial target from the final exam.

## Validation performed in this source pass

- All resource JSON files parse successfully.
- All Java sources pass a comment/string-aware delimiter scan.
- Curator / Notary / Gambler thresholds remain 120 pool / 160 identity.
- Current project metadata reports Wardbound 1.0.2.
- Full Gradle compilation could not run in the audit environment because the wrapper distribution was not locally cached and `services.gradle.org` was unavailable.
