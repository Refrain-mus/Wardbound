# Cthulhu head — Epic Rig v2

This pass refines the 20-block head-only visual entity. The complete resting silhouette, including the beard, is still exactly 20 blocks high. The pre-final narrative remains: this is the sovereign feared by the Masters; the later full-body encounter follows it. Combat AI, damage, rewards, Master dialogue/reactions, Maestro and the full-Cthulhu unlock are outside this model/animation pass.

## What changed

| Feature | Previous pass | Epic Rig v2 |
| --- | ---: | ---: |
| Tentacles | 16 | 24 |
| Major tentacle joints | 6 each | 12 each, on 16 tentacles |
| Smaller fringe joints | — | 8 each, on 8 tentacles |
| Articulated tentacle joints | 96 | 256 |
| Total bones, including secondary controls and sockets | 229 | 589 |
| Cubes | 673 | 1571 |
| Animation clips | 18 | 24 |
| Texture atlas | 256 × 128 | 256 × 256 |

The solid cranial volume now curves in all three axes. Surface-following sutures, inset sigil marks, gill plates and uneven cranial fins break the old rectangular silhouette. The cheeks have four independently articulated folds per side. The beak has two separately controllable halves, and the mouth has a throat control. Tentacles have continuous, tapered joint geometry with cross-section bevels, uneven lengths, curved tips, and subdued sucker details. Emissive light is concentrated in eyes, sigil and selected sucker centers rather than forming a regular bright grid.

## In-game inspection

Use Creative mode or permission level 2. Clear an area approximately 64 × 64 blocks, with 40 blocks of overhead clearance. The convenience summon places the head 32 blocks ahead and faces it toward you.

```mcfunction
/wardbound cthulhu_head summon
/wardbound cthulhu_head showcase
```

The showcase runs 19 performances from manifestation through observation, eye attacks, tentacle attacks, summoning, rage and death. It finishes holding the final death pose. It is a visual demonstration and causes no damage. A `play` command interrupts the showcase and restarts the chosen performance.

```mcfunction
/wardbound cthulhu_head play idle_awake
/wardbound cthulhu_head play tentacle_grasp
/wardbound cthulhu_head play eye_beam_left
/wardbound cthulhu_head play abyssal_pulse
/wardbound cthulhu_head gaze @p @e[type=minecraft:villager,sort=nearest,limit=1]
/wardbound cthulhu_head gaze_auto
/wardbound cthulhu_head remove
```

Tab completion exposes every clip. Direct summon remains `wardbound:cthulhu_head`. Ordinary hits while idle preview stagger without reducing health; `/kill` removes it. It has no natural spawn rule, reward or progression gate and remains available in Peaceful for art inspection. The coarse 18 × 20 block entity box is not a multipart combat hitbox. The animated model can extend beyond its resting height and body box; its visual bounds are enlarged accordingly.

## Rig ownership and names

`root` controls whole-model motion. `head_pivot` controls heavy head movement. `cranial_breath` is a separate runtime control above `upper_head`. The face and beard are independent branches, so opening the jaw does not carry the entire beard with it.

| Bone / pattern | Purpose |
| --- | --- |
| `upper_head` | Rounded cranial mantle, fins and embedded surface detail |
| `jaw` → `mouth` | Jaw opening and independent mouth motion |
| `beak_left`, `beak_right` | Beak spread during roar or psychic pulse |
| `throat_pulse` | Recessed throat expansion |
| `eye_left_aim`, `eye_right_aim` | Separate runtime controls rotating whole eyeballs |
| `eye_left`, `eye_right` | Keyed eye performance below the aim controls |
| `pupil_left`, `pupil_right` | Independent dilation and slit geometry |
| `eyelid_left/right`, `eyelid_lower_left/right` | Upper/lower folds that actually close across the eyes |
| `cheek_left/right_fold_00` through `_03` | Local facial folding |
| `gill_left/right_00` through `_04` | Delayed gill flare |
| `crest_left/right_00` through `_02` | Independently addressable cranial fins |
| `mouth_socket`, `eye_left/right_socket` | Future effect attachment references |

The 16 major chains use groups `center`, `inner`, `side`, `outer`. Each group has `left` and `right` sides, with `_01` and `_02` on each side. Example chain:

`tentacle_outer_left_02_root`, then `tentacle_outer_left_02_mid_01` through `tentacle_outer_left_02_mid_10`, then `tentacle_outer_left_02_tip`, then `tentacle_outer_left_02_socket`.

The eight smaller chains use `fringe` and `temporal`. They have `root`, `mid_01` through `mid_06`, `tip`, and `socket`.

Every articulated segment has a dedicated `<segment>_sway` parent. Animation clips own the segment; runtime secondary motion owns only its sway parent. The new joint count is accounted for in the rotation amplitudes, avoiding the excessive total curl that would result from copying the six-joint angles onto twelve joints. The geometry axis ends precisely at the next joint; no tentacle clip translates or scales the joints.

`rig_manifest.json` contains every chain, its transformed resting joint coordinates, animation durations and reserved controls. `CthulhuHeadRig.java` is generated from the same definitions and supplies runtime names, timings and resting eye socket positions. If regenerating assets, include this generated Java file too.

## Animation library

Animation identifiers have the prefix `animation.cthulhu_head.`. Commands use the short names below.

| Clip | Seconds |
| --- | ---: |
| `idle_awake` | 9 |
| `idle_observing` | 12 |
| `idle_rage` | 5 |
| `idle_dormant` | 14 |
| `manifestation` | 11 |
| `phase_transition` | 7 |
| `stagger` | 2.1 |
| `eye_beam_charge` | 4 |
| `eye_beam_fire` | 3 |
| `dual_eye_burst` | 4.2 |
| `tracking_gaze` | 8 |
| `psychic_roar` | 6 |
| `tentacle_lash` | 4.6 |
| `tentacle_fan_attack` | 5.6 |
| `eldritch_summon` | 8 |
| `head_slam` | 6 |
| `death_start` | 5 |
| `death_collapse` | 7 |
| `death_end` | 5 |
| `eye_beam_left` | 4.5 |
| `eye_beam_right` | 4.5 |
| `tentacle_grasp` | 6 |
| `tentacle_recoil` | 3.5 |
| `abyssal_pulse` | 7 |

Idle chains use periodic, linearly interpolated samples with matching first/last values, plus small independent runtime currents. This avoids repeatedly easing the tentacle to a halt at every idle key. Main attacks use distinct preparation/impact/rebound/recovery landmarks; motion propagates along the chain and differs between regions. The fringe moves at a different rate from the heavy main tentacles.

Upper and lower eyelids slide and deform to close, instead of merely rotating a short strip that leaves the pupil exposed. Eye aim rotates the entire eye hierarchy; pupil dilation remains separately keyed. Gaze interpolation belongs to each entity, preventing two heads from sharing interpolation history.

Charge retains its prepared pose through the fire hand-off. The three death clips share matching terminal/start transforms on all authored channels. Runtime motion fades through `death_start` and remains off during collapse/end. Non-looping clips hold their terminal pose until the server advances; the controller transition interval is allowed for in the performance timer.

The server selects performances through `playClip(name)`, assigns eye targets with `setEyeTargets(left, right)`, and starts the demo with `beginShowcase()`. `automaticGaze()` restores nearest-player observation. Full animated world-space beam origins and synchronized damage frames are still future combat work. The generated eye offsets are precise **resting** offsets, not fully transformed attack sockets. Loading a saved entity restarts its saved clip; joining an already running performance may start the client's visual late.

## Source assets and reproducibility

- `src/main/resources/assets/wardbound/geo/entity/cthulhu_head.geo.json`
- `src/main/resources/assets/wardbound/animations/entity/cthulhu_head.animation.json`
- `src/main/resources/assets/wardbound/textures/entity/cthulhu_head.png`
- `src/main/resources/assets/wardbound/textures/entity/cthulhu_head_glowmask.png`
- `tools/cthulhu_head/generate_assets.py`: geometry, materials, clips, manifest and Java registry generator; requires Pillow.
- `tools/cthulhu_head/inspect_assets.py`: independent asset validation and software inspection renders; requires NumPy and Pillow.

Geometry uses native GeckoLib/Bedrock 1.12 cube data with per-face UVs and 1.8 animation data. Import the exported geometry/animations using Blockbench's GeckoLib plugin. Regeneration overwrites exported assets, so make source changes in the generator or keep your hand-edited exports separately.

Materials are code-authored pixel atlases for the cube model. Their wet highlights are painted shading, not PBR. The emissive mask follows [GeckoLib's emissive convention](https://github.com/bernie-g/geckolib/wiki/Emissive-Textures-(Geckolib4)).

## Validation and limits

The independent inspection checks bone hierarchy and references, UV bounds, finite keys, all clip durations, 12/8-joint chain structure, physical joint-axis endpoints, closed idle loops, charge/fire and death boundary continuity, and transformed 20-block resting height. Java 17 syntax parsing passes for the five integration classes.

Full Java compilation and Minecraft/Forge/GeckoLib playback are unverified: this environment cannot download the missing Gradle distribution. The software previews inspect exported geometry and approximate authored motion; they are not game captures and do not include the runtime aim/current layer. FPS, actual in-game blend behavior, culling, shaders and dedicated-server behavior still require game testing. The larger rig increases animation/rendering work; no claim of unchanged FPS is made.

This remains the visual entity/animation foundation. Beams, grabs, psychic attacks and summons here are animation performances, not implemented damage or mob-spawning systems. The eventual full-Cthulhu unlock must be awarded by a server-side encounter victory handler, never by the preview death animation.
