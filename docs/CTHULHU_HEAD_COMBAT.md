# Cthulhu Head — pre-final encounter combat pass

The Head uses its own dimension, `wardbound:cthulhu_abyss`; it does not reuse Master Recess. The encounter is intentionally single-witness for this pass. Maestro and the later full-body Cthulhu fight are not part of this implementation.

## Test commands

- `/wardbound cthulhu_head fight` — enter the real combat arena and start the encounter.
- `/wardbound cthulhu_head arena_abort` — leave/clean the private combat cell.
- `/wardbound cthulhu_head summon` / `showcase` / `play ...` — visual-rig inspection remains available outside the combat system.

## Arena

- Private deterministic cells in `cthulhu_abyss`, 192 blocks apart.
- 47-block combat radius over a void backdrop.
- Deepslate/sculk/crying-obsidian floor language with twelve perimeter monoliths.
- Block breaking, placement and fluid placement are rejected.
- Leaving the arena radius is not a valid escape path.
- Death drops are deliberately lost in this sovereign encounter.
- Victory records `cthulhu_head_defeated`, which is the future gate for the full Cthulhu encounter.

## Attack library

1. Eye Lance — predictive charge, position lock, one-eye beam.
2. Dual Gaze — divergent left/right eye lanes.
3. Tracking Gaze — delayed tracking beam; continuous movement beats it.
4. Tentacle Lash — three long parallel lane strikes, with a final-phase cross follow-up.
5. Tentacle Fan — five separated tentacle lanes with dodge gaps.
6. Tentacle Grasp — predicted trap point, inward pull, crush impact and recoil.
7. Psychic Roar — expanding shockwave that can be jumped.
8. Head Slam — broad frontal corridor plus secondary floor pulse.
9. Eldritch Eruption — staggered telegraphed void-rift eruptions.
10. Abyssal Pulse — four different annular kill-pressure bands.
11. Sovereign Cross — final-phase two-step cross pattern combining eye/tentacle visual language.

Every damaging geometry is server-authoritative. Lodestone visuals are telegraphs/impacts, not the source of hit detection. Phase transitions and manifestation are invulnerable spectacle/recovery windows rather than hidden damage periods.
