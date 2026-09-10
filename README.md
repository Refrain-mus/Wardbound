Wardbound
Wardbound is a Minecraft 1.20.1 Forge mod built around minigames, card bargains, relics and unusual encounters.

Current source version: 1.0.2.

Wardbound turns structure loot into ward encounters, adding minigames, persistent card systems, relics and other mechanics around exploration and loot.

Requirements
Wardbound currently requires:

Minecraft 1.20.1
Forge 47.4.20
Java 17
GeckoLib 4.8+
Lodestone
AttributeFix 21.x
Placebo 8.6.x
Apothic Attributes / attributeslib 1.3.7+
Dependency coordinates are pinned in build.gradle, while runtime requirements are declared in src/main/resources/META-INF/mods.toml.

Current systems
A large card bargain system with hundreds of cards.
Persistent card effects, laws, scars, remedies, Death cards, build synergies and rare challenge cards.
Ward chest minigames with multiple variants, mastery and failure handling.
Corrupted and altered minigame variants.
Witness Ledger rules, statistics, discoveries and lore tracking.
Relics and other items connected to ward encounters and card systems.
Persistent effects and choices that can continue beyond a single encounter.
Larger authored encounters with custom mechanics, animations and VFX.
GeckoLib-authored entity and item animations.
Lodestone-driven VFX.
AttributeFix and Apothic Attributes integration for expanded combat and build statistics.
Ward encounters
Loot containers found inside structures can be protected by wards.

Interacting with a warded container can start a minigame or another ward-related encounter instead of immediately opening the container.

Minigames vary in rules and difficulty, and some systems can change how later encounters behave.

Successful encounters can provide access to the protected loot and interact with other Wardbound systems.

Card bargains
Wardbound includes a persistent card system built around choices, trade-offs and unexpected effects.

Cards can affect combat, exploration, attributes, future encounters and other parts of the mod.

Some effects are temporary, while others can remain with the player for much longer.

The system includes beneficial cards, dangerous bargains, unusual combinations and rarer card types with their own rules.

Witness Ledger
The Witness Ledger tracks information discovered while playing Wardbound.

It can record things such as:

Minigame statistics
Mastery
Card-related information
Active or discovered mechanics
Relics
Rules
Lore entries
Other progression information
The Ledger is intended to explain systems as they become relevant rather than reveal everything from the beginning.

Relics and items
Wardbound includes relics and other items that interact with its encounter systems.

Some items alter ward interactions, some provide utility, and others are tied to mechanics that are discovered later.

Not every item or mechanic is documented here to avoid spoiling discovery.

Integrations
Wardbound includes support for several external libraries and mods.

GeckoLib
Used for authored entity and item animations.

Lodestone
Used for custom visual effects and particles.

AttributeFix
Allows Wardbound to use attribute values beyond Minecraft's normal limits where required.

Apothic Attributes
Used by parts of the card and combat systems to support additional attributes and build interactions.

Curios
Curios integration is optional where used and is handled defensively rather than being required as a hard dependency.

Building
The project uses the included Gradle wrapper.

The source targets:

Java 17
Minecraft 1.20.1
Forge 47.4.20
Official mappings
Repository notes
Entity and item animation is handled primarily through GeckoLib.
Authored VFX uses Lodestone.
Curios integration is optional where used.
Wardbound does not require an external web service, account login, API key, telemetry system or remote backend at runtime.
docs/ contains selected technical, design and asset-provenance notes relevant to the public source tree.
Some gameplay content is intentionally not documented in this README.

## Development & AI Disclosure

Wardbound is made with extensive AI assistance, especially for coding, debugging and iteration.

The mod's design, gameplay direction, systems, feature decisions, testing and final integration are handled by me.

## Music

I’m a musician, and Wardbound’s original music will be composed and produced by me.

Bug reports
If you encounter a bug, crash or compatibility issue, please use the repository's Issues page.

When reporting a problem, include:

Minecraft version
Forge version
Wardbound version
Relevant dependency versions
Steps to reproduce the issue
Crash report or log when applicable
License
Wardbound is source-available, not open-source.

The project is distributed under the repository's All Rights Reserved terms.

You may view and study the source and make private personal modifications, but redistribution, re-uploading, public modified builds or forks, asset reuse and commercial exploitation require explicit permission.

See LICENSE for the full terms.

### 1.0.2 minigame audit, scenario hardening and late-Master pacing

- Fixed hold/release input pairing so corruption, Possession, Eldritch hush or hybrid interruptions cannot leave Pressure, Keyway, Balance or Vessel controls logically held.
- Fixed Cipher clock initialization so three-, four- and five-ring plates receive the intended time budget.
- Cthulhu's Game now validates actual solution depth for Cipher, Lattice, Shardsong, Orrery and Procession instead of treating random scramble count as difficulty.
- Constellation keeps successful path rendering, uses matched hover/click hit areas and no longer reveals the whole numbered route after a mistake.
- Pressure now has a visible redline and burst state in addition to leakage, pump, vent and seal.
- Keyway guarantees false-set feedback remains separated from the true gate.
- Augury rewrites its omen after the attempt cap instead of repeatedly taxing lives against the same exhausted state.
- Rootway has real decision pressure and truthful instructions instead of referring to a rotation mechanic that this exam variant does not use.
- Mirror only chooses fully asymmetric witness glyphs, so different reflection states cannot become visually indistinguishable.
- Black Measure avoids its two-pour tutorial target in the final exam; Procession testimony is less explicit while remaining uniquely solvable.
- Cthulhu's Game passage titles now pause the authored mechanic, ward clock, corruption/deception/hybrid cadence and gameplay-elapsed clock, so Constellation/Choir previews, Rootway decisions, Pulse beats and hybrid triggers cannot run underneath a passage title.
- The late Master phase now requires both 120 opened field cards and 120 resolved wards. Mob-farming field cards alone cannot reveal Curator, Notary or Gambler, while all three remain equal peers at the same gate.
- Master-kind private cards, Attention, Ledger Master rules, refusal judgement and the Master-card advancement share that dual Master-phase gate; early cards cannot leak the late dealer layer.
- A first-time discipline cannot roll a generic affliction or Living Ward layer. Living/affliction rolls also wait until special encounter classification, preventing accidental stacking onto Possessed, Eldritch or Cthulhu encounters.
- Pre-Master card refusals remain a neutral statistic; the old 50-refusal judgement cannot fire early, and the legacy punishment no longer refreshes Strength XX forever.
- Double chests choose the richer valued half as their deterministic ward owner, so click side cannot lower encounter difficulty while retaining the linked 54-slot reward.
- Success/failure telemetry snapshots the exact issued difficulty, and mandatory presentation time is excluded from gameplay elapsed time.
- Contracts and Curses are no longer advancement children of the hidden Master node, avoiding a progression-tree ordering conflict when their independent card shelves unlock first.

### Hardening-10 audit

- Field-card debug progression presets now use the 1.0.2 pity scale (10 soft start / 30 hard guarantee).
- Config-screen registration is gated through a client-only bootstrap so dedicated servers do not resolve UI classes during common initialization.
- General resource audit: JSON assets parse cleanly, Wardbound model/texture references resolve, and EN/TR language key sets match.

### Hardening-12 debug clarity

- `/wardbound progression summary` now separates defeated Master bosses from known dealer-story threads.
- Field-card debug output reports locked/unlocked state, current dry-streak pity, next eligible hostile drop chance, and remaining kills to the 30-kill hard guarantee.
- `/wardbound progression field_cards` (or `... field_cards status`) gives the same focused field-card status; debug pity/opened setters remain available.
- Ancient Smith status now labels its 2-Master requirement as an unlock condition instead of implying that Wardbound only tracks two Masters.
- Witness Ledger field-card diagnostics no longer advertise a live mob-drop chance before the 20-ward field-card unlock.

