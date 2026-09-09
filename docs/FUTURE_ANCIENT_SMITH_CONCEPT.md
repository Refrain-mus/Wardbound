# Ancient Smith // integrated

This document used to describe a future concept. The Ancient Smith is now integrated into Wardbound as the sole survival acquisition route for Nhal-Sûl.

Current scene:
- Two distinct Master defeats unlock eligibility.
- A safe-state delay prevents the player from being pulled out of combat, menus, sleep, riding or an active Master encounter.
- The player is moved to a dedicated `wardbound:ancient_forge` realm cell reserved by UUID.
- The Smith is already at the forge and does not behave as a boss, hostile mob or wandering NPC.
- He examines the blade, remarks that he did not ask for a second one, tests the material with two strikes, lands the final heavy strike, then throws Nhal-Sûl to the player.
- The player returns only after claiming the offered sword.

Core lines:
- `I did not ask for a second one.`
- `This is not steel.`
- `Nor this.`
- `Use it well.`

Safety and ownership:
- Forge cells are deterministic and separated per player.
- Block breaking, placement and fluid placement are blocked in the forge realm.
- Entry dimension, exact position, yaw and pitch are persisted as the return anchor.
- Logout, death, dimension escape, missing Smith and timeout abort safely without materializing an acquisition reward.
- Server-reloaded sessions recover instead of replaying the cinematic transaction.
