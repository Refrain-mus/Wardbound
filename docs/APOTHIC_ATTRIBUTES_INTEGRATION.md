# Wardbound 39.6.2 — Apothic Attributes Integration

## Runtime dependencies
- AttributeFix remains required for Wardbound's high vanilla attribute caps.
- Apothic Attributes 1.20.1: 1.3.7 (runtime mod id/API namespace: `attributeslib`).
- Placebo 1.20.1: 8.6.x, required by Apothic Attributes.

## Card wave
IDs 438–497 add 60 cards:
- 438–461: 24 timed Wager build experiments.
- 462–473: 6 persistent attribute scars/curses and 6 paired Remedies.
- 474–485: 12 persistent Epic/Unique attribute laws.
- 486–497: 12 Death attribute laws with Death-heart debt.

## Apothic attributes used
Armor Pierce/Shred, Arrow Damage/Velocity, Cold Damage, Crit Chance/Damage,
Current-HP Damage, Dodge Chance, Draw Speed, Experience Gained, Fire Damage,
Healing Received, Life Steal, Mining Speed, Overheal, Protection Pierce/Shred.

Creative Flight / Elytra Flight are intentionally not cardified in this pass: they bypass too much movement/progression logic for an ordinary deck roll.

## Persistence model
Wardbound stores card state in LockData and recomputes one transient modifier per Apothic attribute every 10 server ticks. This prevents stale modifiers after death, relog, remedy use, or timer expiry and avoids stacking hundreds of UUID modifiers on the player.

## Existing system integration
- Debt Unwritten can clear one Apothic lesser burden.
- Absolution clears all registered Apothic lesser burdens, but not Epic/Unique/Death laws.
- Ashen Curator/recovery weighting sees Apothic burdens.
- Witness Ledger gets live timer/law/scar status.
- Card relief text explains the paired Remedy or persistent-law behavior.
- Signing any ID 438–627 awards `apothic_attribute_law`.


## Second 60-card expansion (39.6.1)

The Apothic pool now spans IDs **438..557** for **120 cards total**. The second wave adds 24 hybrid timed clauses, six reversible burdens with six paired remedies, twelve persistent Epic/Unique doctrines, and twelve additional Death laws. The global four-buried-heart Death cap remains authoritative.

The second wave is intentionally synergy-oriented: penetration/crit, ballistic speed, elemental+ranged, sustain, avoidance, execution damage, and mining/experience hybrids are represented. It continues to aggregate into one transient Wardbound modifier per Apothic attribute rather than stacking one modifier object per signed card.


## Third expansion: 60 reactive cards + 10 rare House jokes (39.6.2)

The Apothic-linked pool now spans IDs **438..627** for **190 cards total**. IDs 558..617 add 60 mechanically distinct cards focused on reactive gameplay rather than only static arithmetic: stillness aim, kill momentum, first-hit execution, redline health, sprint/dodge states, ore-triggered combat, wet/fire interactions, crowd/duel states, every-fourth-hit echoes, every-seven-kill surges, projectile AoE, ammunition conditions, and several persistent risk/reward laws.

IDs **618..627** add ten deliberately rare House joke/Easter-egg cards. Their written terms remain technically true while omitting the absurd part of the transaction. Each joke card is one-time per player and receives only **8% of ordinary Card Ecology offer weight**, so the set remains surprising rather than flooding normal hands.

Representative joke: `Zombie 1v1` produces exactly one zombie, but with 560 max health, 18 attack damage, 20 armor, 0.38 movement speed, strong knockback resistance, and Apothic Armor Pierce/Crit/Life Steal. An owner-only kill pays a Netherite Ingot plus three experience levels; outside assistance voids the challenge reward.
