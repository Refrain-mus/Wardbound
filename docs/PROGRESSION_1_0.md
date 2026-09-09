# Wardbound 1.0 progression curve

Wardbound 1.0 uses a long-form progression because the card catalogue, minigame variants, Master encounters and cosmic path now extend far beyond the original prototype scale.

## Headline ward gates

- 15 resolved wards: ordinary post-chest card hands
- 40: physical Sealed Cards can begin dropping from eligible hostile kills
- 90: Master/private cards
- 110: Contracts
- 180: Curses
- 240: Rituals
- 340: Epics
- 430: Covenants
- 600: Uniques
- 650: Eldritch ward tier
- 900: Death laws
- 1200: Cthulhu-tier ward content

Individual card emergence shelves are spread across roughly 0-1500 resolved wards. Broad rarity gates and per-card emergence both apply.

## Chest card-table frequency

The default post-chest bargain chance is 10.5%, but it does not reach full frequency immediately:

- 15-89 wards: 35% of the configured chance
- 90-219: 55%
- 220-449: 78%
- 450+: 100%

Clean/Perfect performance still modifies the final roll. This keeps early chests readable while making card play more present once the catalogue is large enough to support it.

## Field cards

The default eligible hostile-kill drop chance is 0.40%. Pity begins after 110 misses and guarantees a card at 550 eligible kills. With the default curve, the long-run expected interval is roughly 190 eligible hostile kills per Sealed Card.

## Chest/ward synchronization

The rest of the chest ecosystem is stretched with the same curve: afflictions, possessed/unsigned wards, mutation tiers, deception/hybrid layers, Eldritch wards, Cthulhu-tier wards, global difficulty tiers, and the positive loot ceiling no longer finish their progression in the first few hundred resolved wards.

## Debug presets

- `fresh`: 0 wards
- `early`: 120
- `mid`: 450
- `late`: 900
- `endgame`: 1500
- `cosmic` / `max`: 2200 (future-facing headroom beyond the current final card shelf)
