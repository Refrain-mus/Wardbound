# Wardbound 1.0.2 progression curve

Wardbound 1.0.2 uses a long-form progression because the card catalogue, minigame variants, Master encounters and cosmic path now extend far beyond the original prototype scale.

## Headline ward gates

- 15 resolved wards: ordinary post-ward card hands
- 20: physical Sealed Cards can begin dropping from eligible hostile kills
- 90: Master/private cards
- 110: Contracts
- 180: Curses
- 220: House Measure / Cut cadence
- 240: Rituals and first corrupted-minigame eligibility (with Mastery II)
- 340: Epics
- 360: minigame deception layer
- 520: hybrid minigame rounds / Revision II band
- 430: Covenants
- 600: Uniques
- 650: Eldritch ward tier
- 900: Death laws
- 1200: Cthulhu-tier ward content

Individual card emergence shelves are spread across roughly 0-1500 resolved wards. Broad rarity gates and per-card emergence both apply.

## Post-ward card-table frequency

The default post-ward bargain chance is 12%. Eligible hands are attached to the ordinary ward that was just resolved, but the chance still ramps gently with progression:

- 15-89 wards: 85% of the configured chance
- 90-219: 90%
- 220-449: 95%
- 450+: 100%

Clean/Perfect performance still modifies the final roll. This keeps early chests readable while making card play more present once the catalogue is large enough to support it.

## Field cards

The default eligible hostile-kill drop chance is 7.5%. After 10 consecutive eligible misses the chance begins rising by 1 percentage point per miss, and the 30th eligible kill in a dry streak is guaranteed to drop a Sealed Card. Field cards unlock after 20 resolved wards and are meant to remain visible during ordinary exploration without becoming farm-spam.

## Chest/ward synchronization

Vanilla double chests are treated as one Wardbound encounter. Once either half owns a ward, card hand, re-seal offer, permanent failure, or pending reward, interactions on the other half route back to that same state. Reward/penalty multipliers are mirrored across both loot-table halves, while one-shot relic/lore extras are emitted only once.

The rest of the chest ecosystem is stretched with the same curve: afflictions, possessed/unsigned wards, mutation tiers, deception/hybrid layers, Eldritch wards, Cthulhu-tier wards, global difficulty tiers, and the positive loot ceiling no longer finish their progression in the first few hundred resolved wards.

Card revisions now obey those long-form shelves as well: Revision I requires 240 wards, Revision II 520, and Palimpsest/Revision III 850 in addition to the normal repeated-signature requirement. Maestro's cosmic path cannot reveal before the Death-law band, and the Cthulhu Head Canticle cannot open before the 1200-ward Cthulhu band.

## Debug presets

- `fresh`: 0 wards
- `early`: 120
- `mid`: 450
- `late`: 900
- `endgame`: 1500
- `cosmic` / `max`: 2200 (future-facing headroom beyond the current final card shelf)
