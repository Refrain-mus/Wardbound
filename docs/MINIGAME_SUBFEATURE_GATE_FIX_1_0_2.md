# Minigame sub-feature gate correction — 1.0.2

## Reported regression

Ordinary minigames could appear almost permanently "clean" even after raising global ward progression. The shared onboarding gate treated `masteryTier == 0` as synonymous with "first lesson". With 26 disciplines and Mastery I requiring multiple successful observations, this suppressed quirk/anomaly/maker behavior far longer than intended.

A value-60 ordinary ward, for example, already qualifies for one normal quirk by value. Under the old gate it still received a zero modifier budget whenever that specific discipline had not yet reached Mastery I.

## Corrected contract

- 0–14 resolved wards: global clean onboarding. Ordinary quirks/anomalies remain suppressed.
- 15+ resolved wards: ordinary sub-features are active. An unfamiliar tier-0 discipline is capped to one ordinary quirk and gets a reduced anomaly roll instead of having the entire system disabled.
- Named/hard mutation layers (Affliction, Living, Possessed, Eldritch selection) still require the player to have at least one real win in that individual discipline.
- "Learned" now means at least one successful solve for these hard layers; it no longer incorrectly means Mastery I.
- Deep expert/corrupted forms retain their later global/mastery gates.

`/wardbound progression summary` now reports the ordinary sub-feature gate separately from hard mutation and expert-form shelves.
