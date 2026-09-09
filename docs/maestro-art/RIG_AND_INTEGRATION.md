# Maestro v2 — rig, animation and VFX contract

Source overlay for Wardbound, Forge 1.20.1 and GeckoLib 4.8.4. Entity ID remains
`wardbound:maestro_art_preview`. Resting geometry is exactly 32 model units tall.
All V1 public bone and clip names remain available. Some children now inherit
additional articulated controls, so integrations must resolve hierarchy rather than
assuming the previous direct parent. Merge all `src` content over the previous pack.

## Rig

226 bones, 246 cubes, 44 empty sockets. This includes actual geometry subdivisions:

| Branch | Controls |
| --- | --- |
| Spine | root → pelvis → lower_torso → upper_torso → neck → head |
| Arms | shoulder → upper_arm → forearm → forearm_twist → hand |
| Digits | finger_<side>_<digit>_01 → 02 → 03, including thumbs |
| Legs | thigh → lower_leg → foot → toe |
| Mask | jaw, eye/glow, mask_cheek, mask_brow, eyelid on each side |
| Back tails | four named segments, each followed by an articulated `_hem`: eight geometry sections per side |
| Side panels | three named segments plus three `_hem` controls per side |
| Accessories | segmented lapels, cravat, chain, sleeve flaps, score ribbons, baton ribbons |
| Spectral ribbons | four segments per side with separate `_secondary` parents |

Original pivots and native unit conventions are retained. Main limbs rotate only;
root translates for performance, eyelids translate for blinking, eye glow scales
for death. Small runtime cloth motion is assigned absolutely to `_secondary`
parents, so repeated rendering cannot accumulate rotation drift. Main animations
never key sockets or reserved secondary parents. Distal finger and hem controls
receive a share of their parent's rotation, keeping articulated geometry attached.

## Animation

45 clips. Curves are authored with anticipation, stroke and recovery landmarks,
then baked from shape-preserving cubic Hermite interpolation to linear samples
at approximately 40 samples/second. Constant channels remain just two keys.
This is sample density, not a game frame-rate requirement. Extrema and holds do
not overshoot; loop endpoints and explicit automatic transitions match exactly.
Animation prefix is `animation.maestro_performer.`; commands omit that prefix.

| Clip | Seconds | Loop | Follow-up |
| --- | ---: | --- | --- |
| `idle_composed` | 6 | yes | `loop` |
| `idle_observing` | 8 | yes | `loop` |
| `idle_rage` | 3.6 | yes | `loop` |
| `intro` | 7.2 | no | `idle_composed` |
| `glide_step` | 1.8 | yes | `loop` |
| `pivot_turn` | 2 | no | `idle_composed` |
| `stagger` | 1.7 | no | `idle_composed` |
| `phase_transition` | 6.4 | no | `idle_composed` |
| `conduct_start` | 2.4 | no | `conduct_loop` |
| `conduct_loop` | 3.2 | yes | `loop` |
| `conduct_strike` | 2.8 | no | `recovery_cast` |
| `left_hand_cast` | 3.1 | no | `recovery_cast` |
| `right_hand_cast` | 3.1 | no | `recovery_cast` |
| `dual_hand_cast` | 4.2 | no | `recovery_cast` |
| `command_gesture` | 3.8 | no | `recovery_cast` |
| `summoning_gesture` | 5.6 | no | `recovery_heavy` |
| `shockwave_release` | 4 | no | `recovery_heavy` |
| `note_barrage` | 5 | no | `recovery_cast` |
| `wave_attack` | 3.8 | no | `recovery_cast` |
| `slam_attack` | 4.5 | no | `recovery_heavy` |
| `dash_lunge` | 2.2 | no | `recovery_step` |
| `grand_crescendo` | 9 | no | `recovery_heavy` |
| `recovery_cast` | 1.6 | no | `idle_composed` |
| `recovery_heavy` | 2.4 | no | `idle_composed` |
| `recovery_step` | 1.3 | no | `idle_composed` |
| `piano_start` | 3 | no | `piano_loop` |
| `piano_loop` | 4 | yes | `loop` |
| `piano_end` | 2.5 | no | `idle_composed` |
| `formal_bow` | 3.8 | no | `idle_composed` |
| `silence_gesture` | 4.1 | no | `idle_composed` |
| `death_start` | 4 | no | `death_fall` |
| `death_fall` | 4.6 | no | `death_end` |
| `death_end` | 3 | no | `hold` |
| `baton_flourish` | 3.6 | no | `recovery_cast` |
| `accusation` | 3.7 | no | `recovery_cast` |
| `counter_guard` | 2.8 | no | `idle_composed` |
| `counter_riposte` | 2.6 | no | `recovery_cast` |
| `staccato_combo` | 4.2 | no | `recovery_cast` |
| `legato_sweep` | 5.2 | no | `recovery_cast` |
| `orchestra_summon` | 7 | no | `recovery_heavy` |
| `silence_field` | 5.6 | no | `recovery_heavy` |
| `levitation_start` | 3 | no | `levitation_loop` |
| `levitation_loop` | 4 | yes | `loop` |
| `levitation_end` | 3 | no | `idle_composed` |
| `curtain_call` | 5 | no | `idle_composed` |

The preview entity does not navigate. Glide and dash remain in place; levitation
moves the rendered root only. A future server encounter must own displacement,
hitboxes and damage. Piano animations include articulated hands and hide the baton;
they do not create a piano/bench prop.

## Active VFX

`MaestroParticles` registers four dedicated particle types using Forge RegisterEvent.
`MaestroParticleProviders` is restricted to Dist.CLIENT. All four particle JSONs
resolve to included transparent textures. No new particle-library dependency is used.

`MaestroVfxLayer.preRender` enables matrix tracking for the named GeoBones. Its
post-geometry `render` reads `GeoBone.getWorldPosition()`, after the current entity's
actual bone, parent, root and renderer transforms. It does not use static manifest
pivots as world positions. These APIs were checked against GeckoLib's Forge 1.20.1
[GeoBone source](https://github.com/bernie-g/geckolib/blob/1.20.1/Forge/src/main/java/software/bernie/geckolib/cache/object/GeoBone.java) and
[GeoRenderLayer source](https://github.com/bernie-g/geckolib/blob/1.20.1/Forge/src/main/java/software/bernie/geckolib/renderer/layer/GeoRenderLayer.java).

| System | Behavior |
| --- | --- |
| Notes | Fullbright musical glyphs drift or shoot forward from authored sockets |
| Conducting trail | Short-lived arc sprites interpolate between current/previous baton and left-hand positions |
| Cutting arc | Timed sixteen-glyph fan released from the conducting socket |
| Stage pulse | Radial expanding particle pattern; central ring sprite is camera-facing |
| Halo / spectral score | Vertical circle of musical glyphs oriented with the entity |
| Impact / seal | Short radial glyph bursts |
| Orchestral summon | Eight named radial anchors emit twelve-point circles |
| Idle | One restrained chest note every twelve ticks |

The ring texture is a billboard, not a flat terrain decal. Horizontal circles are
formed by particle positions/velocities. Floor/stage/orchestra origins use entity
base Y + 0.04, suitable for the intended flat stage; arbitrary terrain is not raycast.
Particles use normal client particle settings, fullbright light and alpha fade.

Emission is limited to once per entity tick, independent of render FPS and glow
passes. Budget is 128 particles/entity/tick within 24 blocks, 48 beyond 24 blocks,
and none beyond 64 blocks. Offscreen entities emit nothing. A gap catches up at
most two ticks of events; older visual bursts are dropped. Paths longer than two
blocks between updates reset instead of drawing a streak through a teleport.
Weak per-entity state prevents one Maestro from reusing another's trail positions.

`MaestroVfxTimeline.java` is generated from the same metadata as `vfx_timeline.json`.
VFX use a local visual clock reset with the animation controller's revision and a
six-tick blend allowance. This keeps late-created visual controllers and their
VFX clocks together. Server-driven clip changes can still truncate a late client's
performance; precise network attack synchronization is not a claim of this pack.
No client particle position is used for gameplay hit checks. Cue strings map to
shared effect families, not separate combat mechanics for every string.

## Socket map

All pivots in `rig_manifest.json` are model units, not world positions. The Java
renderer reads world matrices only after the matching entity has rendered.
`Model.socketBone` still exposes local pose access for external render integration.

| Socket | Parent | Purpose |
| --- | --- | --- |
| `floor_cast_socket` | `root` | Ground-plane ritual origin, unaffected by torso articulation |
| `stage_ring_socket` | `root` | Stage circle / expanding rings |
| `foot_left_socket` | `foot_left` | Footfall / impact / step dust |
| `finger_left_index_socket` | `finger_left_index_03` | Fingertip sparks / precise musical articulation |
| `finger_left_middle_socket` | `finger_left_middle_03` | Fingertip sparks / precise musical articulation |
| `finger_left_ring_socket` | `finger_left_ring_03` | Fingertip sparks / precise musical articulation |
| `finger_left_little_socket` | `finger_left_little_03` | Fingertip sparks / precise musical articulation |
| `hand_left_socket` | `hand_left` | Palm casting / arc origin / note emission |
| `conducting_arc_left_socket` | `hand_left` | Conducting motion trail |
| `wrist_left_socket` | `hand_left` | Wrist orbit |
| `foot_right_socket` | `foot_right` | Footfall / impact / step dust |
| `finger_right_index_socket` | `finger_right_index_03` | Fingertip sparks / precise musical articulation |
| `finger_right_middle_socket` | `finger_right_middle_03` | Fingertip sparks / precise musical articulation |
| `finger_right_ring_socket` | `finger_right_ring_03` | Fingertip sparks / precise musical articulation |
| `finger_right_little_socket` | `finger_right_little_03` | Fingertip sparks / precise musical articulation |
| `hand_right_socket` | `hand_right` | Palm casting / arc origin / note emission |
| `conducting_arc_right_socket` | `hand_right` | Conducting motion trail |
| `wrist_right_socket` | `hand_right` | Wrist orbit |
| `baton_tip_socket` | `baton_tip` | Primary conductor trail / projectile origin |
| `baton_grip_socket` | `baton` | Grip aura |
| `chest_socket` | `upper_torso` | Chest seal / phase flare |
| `head_socket` | `head` | Mask aura / eye flare |
| `back_socket` | `upper_torso` | Back sigil / spectral score |
| `halo_socket` | `head` | Optional halo behind head |
| `sigil_socket` | `upper_torso` | Forward musical seal |
| `note_left_socket` | `hand_left` | Left note emitter |
| `note_right_socket` | `hand_right` | Right note emitter |
| `sound_wave_socket` | `upper_torso` | Forward sound-wave origin |
| `aura_socket` | `pelvis` | Body aura |
| `impact_socket` | `hand_right` | Strike impact |
| `piano_left_socket` | `hand_left` | Optional keyboard fingertip anchor |
| `piano_right_socket` | `hand_right` | Optional keyboard fingertip anchor |
| `eye_left_socket` | `eye_left` | Eye seal and glint |
| `ribbon_left_socket` | `spectral_ribbon_left_03` | Spectral score fragments |
| `eye_right_socket` | `eye_right` | Eye seal and glint |
| `ribbon_right_socket` | `spectral_ribbon_right_03` | Spectral score fragments |
| `orchestra_00_socket` | `root` | Radial stage notation |
| `orchestra_01_socket` | `root` | Radial stage notation |
| `orchestra_02_socket` | `root` | Radial stage notation |
| `orchestra_03_socket` | `root` | Radial stage notation |
| `orchestra_04_socket` | `root` | Radial stage notation |
| `orchestra_05_socket` | `root` | Radial stage notation |
| `orchestra_06_socket` | `root` | Radial stage notation |
| `orchestra_07_socket` | `root` | Radial stage notation |

## Reproduction and verification

Run `tools/generate_maestro.py` to regenerate assets, registry constants, manifest
and VFX cue constants. Run `tools/inspect_maestro.py` and `--sheet` for validation
and software captures. `render_motion.py` renders the conduct loop;
`render_vfx_preview.py` adds an illustrative offline VFX preview. Python tools
require Pillow and NumPy. They are authoring tools, not runtime dependencies.

Java 17 syntax was checked for all nine Java files. Full dependency type checking,
Forge compilation, dedicated-server loading and Minecraft/GeckoLib playback have
not been verified. No FPS benchmark is claimed. The offline VFX preview is not a
capture of the Java particle engine and omits runtime micro-sway.

This package supplies the model, rig, performances, summonable visual entity and
active client VFX implementation. Combat AI, damage, sounds, arena, rewards and
progression belong to the main encounter integration.
