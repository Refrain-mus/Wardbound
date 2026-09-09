# Wardbound command root

All Wardbound operator/test commands share one Brigadier root: `/wardbound`.

Primary branches:
- `/wardbound smith ...`
- `/wardbound gambler ...`
- `/wardbound curator ...`
- `/wardbound notary ...`
- `/wardbound silas ...`
- `/wardbound cthulhu_head ...`

Chest/minigame/card/progression utilities already registered by Wardbound remain under the same `/wardbound` root.
Legacy standalone roots such as `/ancientsmith`, `/wardbound_gambler`, `/wardbound_curator`, `/wardbound_notary`, and `/wardbound_silas` are intentionally not registered.

Cthulhu Head combat testing lives under `/wardbound cthulhu_head fight`; its showcase/rig commands share the same branch. The future full-body Cthulhu encounter will remain a separate progression layer.
