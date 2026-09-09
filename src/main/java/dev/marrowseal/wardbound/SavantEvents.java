package dev.marrowseal.wardbound;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import dev.marrowseal.wardbound.item.CthulhuEyeItem;

/** Lets the Savant react to dimension changes caused by portals or other mods. */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class SavantEvents {

    private SavantEvents() {}

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getTarget() instanceof Mob mob)) return;
        if (CthulhuEyeItem.onWeaponAttack(sp, mob)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) Guardians.recordDimensionVisit(sp);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        // Progression tracking is independent of whether the Savant chooses to speak.
        Guardians.recordDimensionVisit(sp);

        long ownTravelTick = sp.getPersistentData().getLong("WardboundSavantOwnTravelTick");
        if (ownTravelTick > 0L) {
            sp.getPersistentData().remove("WardboundSavantOwnTravelTick");
            // travel() provides its own richer line. A stale marker is ignored so
            // it can never silence some unrelated portal trip much later.
            if (Math.abs(sp.level().getGameTime() - ownTravelTick) <= 10L) return;
        }

        CthulhuEyeItem.speakDimensionChange(sp, event.getTo());
    }
}
