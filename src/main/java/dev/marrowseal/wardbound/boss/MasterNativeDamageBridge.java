package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.Wardbound;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps Wardbound Masters on Minecraft's real health pipeline while reserving the
 * final point of health for their authored death timelines.
 *
 * LivingDamageEvent is the correct interception point: armor, magic reduction and
 * absorption have already been applied, so the captured value is the real final
 * health damage seen by Forge integrations. Only an explicitly armed Wardbound hurt
 * call is touched, leaving /kill and bypass-invulnerability damage completely vanilla.
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class MasterNativeDamageBridge {
    private static final Map<LivingEntity, Float> ARMED = new WeakHashMap<>();
    private MasterNativeDamageBridge() {}

    public static void arm(LivingEntity entity) {
        if (entity != null) ARMED.put(entity, Float.NaN);
    }

    public static float consume(LivingEntity entity, float fallback) {
        Float captured = entity == null ? null : ARMED.remove(entity);
        return captured == null || Float.isNaN(captured) ? Math.max(0F, fallback) : Math.max(0F, captured);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFinalDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !ARMED.containsKey(entity)) return;
        float amount = event.getAmount();
        if (!Float.isFinite(amount) || amount <= 0F) {
            ARMED.put(entity, 0F);
            return;
        }
        ARMED.put(entity, amount);

        // Never let vanilla reach zero during an authored encounter. The boss hurt
        // method consumes the unclamped FINAL damage and decides whether to start its
        // cinematic death sequence.
        float safeNativeDamage = Math.max(0F, entity.getHealth() - 1F);
        if (amount > safeNativeDamage) event.setAmount(safeNativeDamage);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof CinematicVitalityHost host) || event.getEntity().level().isClientSide || !host.wardboundVitalityActive()) return;
        MasterVitality vitality = host.wardboundVitality();
        if (vitality == null) return;
        if (host.wardboundVitalityLocked()) {
            event.setAmount(0F);
            return;
        }
        vitality.reconcileFromNative(event.getEntity());
        float allowed = (float)Math.max(0D, Math.min(event.getAmount(), vitality.maximum() - vitality.current()));
        event.setAmount(allowed);
        vitality.heal(allowed);
        host.wardboundVitalityChanged();
    }
}
