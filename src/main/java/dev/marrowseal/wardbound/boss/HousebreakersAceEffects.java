package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.Wardbound;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Permanent world-law half of Housebreaker's Ace.
 *
 * The reward deliberately lives outside the normal card timer/revision system:
 * beating the end-endgame Master has literally changed the player's rules.
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class HousebreakersAceEffects {
    private HousebreakersAceEffects() {}

    private static final TagKey<EntityType<?>> MASTER_BOSSES = TagKey.create(
            Registries.ENTITY_TYPE, new ResourceLocation(Wardbound.MODID, "master_bosses"));

    private static final UUID SPEED = uuid("speed");
    private static final UUID HEALTH = uuid("health");
    private static final UUID BLOCK_REACH = uuid("block_reach");
    private static final UUID ENTITY_REACH = uuid("entity_reach");
    private static final UUID LUCK = uuid("luck");

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof net.minecraft.server.level.ServerPlayer player)
                || player.getServer() == null || player.tickCount % 20 != 0) return;

        LockData data = LockData.get(player.getServer());
        boolean active = MasterRewards.hasWorldLaw(data, player.getUUID());
        AttributeInstance healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        boolean justGainedHealthLaw = active && healthAttr != null && healthAttr.getModifier(HEALTH) == null;

        sync(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED, "wardbound.ace_world_speed",
                active ? 0.10D : 0D, AttributeModifier.Operation.MULTIPLY_TOTAL);
        sync(healthAttr, HEALTH, "wardbound.ace_world_health",
                active ? 8.0D : 0D, AttributeModifier.Operation.ADDITION);
        if (justGainedHealthLaw) player.heal(8.0F);
        sync(player.getAttribute(ForgeMod.BLOCK_REACH.get()), BLOCK_REACH, "wardbound.ace_world_block_reach",
                active ? 1.5D : 0D, AttributeModifier.Operation.ADDITION);
        sync(player.getAttribute(ForgeMod.ENTITY_REACH.get()), ENTITY_REACH, "wardbound.ace_world_entity_reach",
                active ? 1.0D : 0D, AttributeModifier.Operation.ADDITION);
        sync(player.getAttribute(Attributes.LUCK), LUCK, "wardbound.ace_world_luck",
                active ? 2.0D : 0D, AttributeModifier.Operation.ADDITION);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onHurt(LivingHurtEvent event) {
        // Outgoing law: the player's attacks carry the Last Table into ordinary combat.
        if (event.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer attacker
                && attacker.getServer() != null) {
            LockData data = LockData.get(attacker.getServer());
            if (MasterRewards.hasWorldLaw(data, attacker.getUUID())) {
                LivingEntity target = event.getEntity();
                if (target instanceof Enemy || target.getType().is(MASTER_BOSSES)) {
                    float amount = event.getAmount() * 1.15F;
                    if (target.getType().is(MASTER_BOSSES)) amount *= 1.25F;
                    event.setAmount(amount);
                }
            }
        }

        // Incoming law: do not blunt kill/void-style damage that explicitly bypasses invulnerability.
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer defender
                && defender.getServer() != null
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            LockData data = LockData.get(defender.getServer());
            if (MasterRewards.hasWorldLaw(data, defender.getUUID()))
                event.setAmount(event.getAmount() * 0.88F);
        }
    }

    private static UUID uuid(String key) {
        return UUID.nameUUIDFromBytes(("wardbound.housebreakers_ace." + key).getBytes(StandardCharsets.UTF_8));
    }

    private static void sync(AttributeInstance attribute, UUID id, String name, double amount, AttributeModifier.Operation op) {
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(id);
        if (Math.abs(amount) <= 0.0001D) {
            if (current != null) attribute.removeModifier(id);
            return;
        }
        if (current != null && Math.abs(current.getAmount() - amount) < 0.0001D && current.getOperation() == op) return;
        if (current != null) attribute.removeModifier(id);
        attribute.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }
}
