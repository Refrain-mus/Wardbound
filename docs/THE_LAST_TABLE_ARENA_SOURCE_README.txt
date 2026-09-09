THE LAST TABLE — USER-AUTHORED GAMBLER ARENA IMPORT
Minecraft Java 1.20.1 / Forge 47.4.20 / Sponge schematic v2

Source retained in this project:
  docs/arena-sources/gamblerarena.schem

SOURCE GEOMETRY
  Schematic selection: 190 X × 116 Y × 173 Z
  Authored non-air blocks: 32,805
  Unique center marker: minecraft:crying_obsidian at local (89,21,79)
  WorldEdit copy offset: (-89,-22,-79)
  Therefore the //copy origin / player feet were local (89,22,79).

WARDBOUND ANCHOR
MasterArenaManager.center is the Pale Gambler's feet position.
The structure origin is placed at:
  center + (-89,-22,-79)
This maps the crying-obsidian marker to center.below() and Pale Gambler to center,
so the boss is standing exactly ON TOP OF the crying obsidian rather than inside it.

PLAYER START
The existing authored encounter sightline is retained at center + (0,0,+17).
In the imported schematic this resolves to local (89,22,96). The block below is solid
and the player-height column is clear.

PERFORMANCE / CLEANUP
The Sponge selection contains ~3.78M air positions. They are intentionally NOT exported
into the runtime structure. the_last_table.nbt contains only the 32,805 authored non-air
positions. the_last_table_clear.nbt contains the same positions as AIR and is used only
when a cell that hosted Gambler is repurposed for Curator/Notary.

This avoids placing millions of air blocks on every Gambler invocation while still preventing
large Gambler architecture from leaking into another Master encounter. Old 62x36x62/procedural
center geometry is cleared before first placement of the new sparse arena.

MULTIPLAYER CELL SAFETY
The imported arena spans roughly 188 × 173 blocks horizontally around its authored geometry.
Private Master cell spacing was increased from 128 to 256 blocks so adjacent UUID-derived cells
cannot overlap this arena.

RUNTIME VALIDATION
After placement MasterArenaManager verifies that center.below() is crying obsidian. If the
anchor does not materialize, arena preparation fails closed instead of starting a misaligned fight.
