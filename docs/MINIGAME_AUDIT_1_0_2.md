# Wardbound 1.0.2 — Minigame audit and hardening

This pass focuses on gameplay integrity rather than cosmetic difficulty. The goal is for Cthulhu's Game to test learned minigame grammar without collapsing those disciplines into cheap micro-versions.

## Correctness fixes

- Host mouse/key releases are paired with delivered presses. A corruption blackout, Possession seizure, Eldritch hush or hybrid interruption that begins while a control is held can no longer swallow the release and leave a minigame in a stuck held state.
- Cipher calculates its ring-dependent clock from constructor-safe data. Four- and five-ring plates no longer inherit the three-ring time multiplier because `ringCount` was still Java-default zero during the base constructor.
- Constellation render and input radii agree. Completed route links remain visible. A wrong star now recoils progress without replaying the entire answer.
- Keyway's generated false set has a deterministic separation fallback, preventing a rare false/true tension overlap.
- Pressure button hitboxes match the painted buttons.
- Rootway's hint now describes its actual routing mechanic.
- Mirror restricts its target to fully asymmetric sigils, preventing reflection ambiguity.

## Cthulhu's Game depth pass

- **Cipher:** exact breadth-first solution distance; generated boards must meet a 4-move minimum, or 5 when testing the player's weak discipline.
- **Lattice:** actual Lights-Out solution depth is checked; cancellation-heavy random scrambles are rejected.
- **Black Measure:** the two-move target is removed from the final-exam pool; deeper targets 1, 4 and 7 are used.
- **Pressure:** continuous leak is joined by a visible redline/burst threshold. Pumping past it costs a life and deals a new verdict.
- **Constellation:** mistakes no longer purchase a free full-route replay.
- **Augury:** starting omens cannot equal the default all-zero guess too closely; reaching the attempt cap costs a life and rewrites the board instead of allowing repeated life drains on the same exhausted clue state.
- **Rootway:** each routing decision has a countdown that tightens as the root advances.
- **Shardsong:** rotation depth is measured from the actual tile orientations and shallow starts are rerolled. Scoring uses the real minimum.
- **Orrery:** exact coupled-ring distance is used for generation, scoring and move allowance.
- **Procession:** starting order is checked by true swap distance. The testimony is indirect but has exactly one valid order.
- **Mirror:** each verdict uses one fully asymmetric glyph in all four unique reflection states; option cards no longer print their axes, so the player reads the transformed glyph itself.

## Progression preserved

The late Master phase remains unchanged by this audit: Curator, Notary and Gambler stay parallel/equivalent late-game Masters behind the same progression gate. The README Development & AI Disclosure and Music sections are preserved verbatim.
