Wardbound
Wardbound is a Minecraft 1.20.1 Forge mod built around minigames, card bargains, relics and unusual encounters.

Current source version: 1.0.

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