# Hand Control, Family Echo and Fourth-Wave Integration

This pass extends Wardbound to 408 card IDs without replacing the existing deck-law, progression, mining, Death Resonance or Third Wave systems.

## Hand rules
`The Red Pen` is an offer-local operation. The authoritative server receives the Red Pen ID plus a target ID, verifies both IDs belong to the currently stored offer, then rewrites only that offer array. The enum/card pool is never mutated.

At 120 resolved wards the save begins counting eligible hands. Every third hand receives one extra legal card. A refresh does not advance or trigger this counter, so Fourth/Fifth Card remain meaningful deck laws rather than a refresh exploit.

## Echo rules
Each non-REFRESH `ForbiddenBargain.Kind` has its own primer. Pending state is keyed by family, so signing another family does not consume it. Primer cards never masquerade as objectives or hostile laws merely because they carry the target family Kind.

When a matching card is signed, the echo is consumed in the authoritative signature path. Revision-aware cards advance one effective revision (e.g. an effect at I can resolve at II); persistent Fourth-Wave laws read the same x2 marker to expose a stronger law variant. A new repeatable signature clears a stale per-signature marker before checking a newly armed echo.

## Cross-system synergies
- **Mirror Writ + Return to Sender:** Mirror provides the deliberate facing/reflection skill check; Return to Sender accelerates the returned shot and lowers the mirror recovery time.
- **Borrowed Anatomy + Stolen Countenance:** the older card can still copy a beneficial status effect; Anatomy separately records mob archetype, so both can fire from one kill without competing for the same state.
- **Borrowed Anatomy + Ninth Margin:** Anatomy is passive adaptation; Ninth Margin is the active expression of the same remembered archetype. Owning both increases active spell power and lowers recovery.
- **Corpse Ledger + Death Resonance:** unpaid corpse locations create escalating weakness; personally reclaiming the location pays resonance back into the existing death metagame.
- **Chain of Custody + Ore Whisper / Pilgrim's Luck:** chained blocks are destroyed through the normal server block-break path, so normal loot/tool wear and mining-card effects still happen. A synthetic-chain guard skips only Ritual objective increments.

## Authority boundaries
Red Pen targets and Ninth Margin casts are server revalidated. Chain recursion is server-owned and guarded per player. No card is removed from `ForbiddenBargain.values()` at runtime.
