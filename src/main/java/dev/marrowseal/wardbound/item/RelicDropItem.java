package dev.marrowseal.wardbound.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import dev.marrowseal.wardbound.WardEffects;
import dev.marrowseal.wardbound.WardSounds;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Functional fragments left behind by stronger wards.
 *
 * <p>Unlike keys and the older relics, these are primed before a ward is touched.
 * Right-clicking consumes the shard and leaves a long-lived status effect on the
 * player. The next ward the player actually commits to absorbs that attunement,
 * so a normal chest aid and a shard can both influence the same ward.</p>
 */
public class RelicDropItem extends Item {

    private static final int ATTUNEMENT_DURATION = 20 * 60 * 8; // 8 minutes
    private static final int ELDRITCH_STORM_DURATION = 20 * (60 * 3 + 30); // 3:30
    private static final int BLOODGLASS_DURATION = 20 * 60 * 4; // 4 minutes

    public enum Kind {
        SPLINTER("ward_splinter"),
        ELDRITCH_SHARD("eldritch_shard"),
        ECHO_SHARD("echo_shard"),
        BLOODGLASS_SHARD("bloodglass_shard");

        public final String id;
        Kind(String id) { this.id = id; }
    }

    private final Kind kind;

    public RelicDropItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        return activate(serverPlayer, stack)
                ? InteractionResultHolder.consume(stack)
                : InteractionResultHolder.fail(stack);
    }

    /**
     * Used both by air-use and by the chest interaction hook. Holding a shard and
     * right-clicking a chest primes the shard instead of accidentally spending it
     * directly on that chest.
     */
    public boolean activate(ServerPlayer player, ItemStack stack) {
        if (player.hasEffect(WardEffects.WARD_SPLINTER_ATTUNEMENT.get())
                || player.hasEffect(WardEffects.ELDRITCH_SHARD_ATTUNEMENT.get())
                || player.hasEffect(WardEffects.ELDRITCH_STORM.get())
                || player.hasEffect(WardEffects.ECHO_SHARD_ATTUNEMENT.get())
                || player.hasEffect(WardEffects.BLOODGLASS_FERVOR.get())) {
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "Another shard is already active. Let its effect end before using another.")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }

        ItemStack activationStack = stack.copy();
        activationStack.setCount(1);

        if (kind == Kind.SPLINTER) {
            player.addEffect(new MobEffectInstance(WardEffects.WARD_SPLINTER_ATTUNEMENT.get(),
                    ATTUNEMENT_DURATION, 0, false, false, true));
        } else if (kind == Kind.ELDRITCH_SHARD) {
            player.addEffect(new MobEffectInstance(WardEffects.ELDRITCH_SHARD_ATTUNEMENT.get(),
                    ATTUNEMENT_DURATION, 0, false, false, true));
            player.addEffect(new MobEffectInstance(WardEffects.ELDRITCH_STORM.get(),
                    ELDRITCH_STORM_DURATION, 0, false, true, true));
        } else if (kind == Kind.ECHO_SHARD) {
            player.addEffect(new MobEffectInstance(WardEffects.ECHO_SHARD_ATTUNEMENT.get(),
                    ATTUNEMENT_DURATION, 0, false, false, true));
        } else {
            player.getPersistentData().remove("WardboundBloodglassKills");
            player.addEffect(new MobEffectInstance(WardEffects.BLOODGLASS_FERVOR.get(),
                    BLOODGLASS_DURATION, 0, false, true, true));
        }

        if (!player.getAbilities().instabuild) stack.shrink(1);
        // Exact vanilla Totem-style presentation, but with the shard that was actually consumed.
        dev.marrowseal.wardbound.Wardbound.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new dev.marrowseal.wardbound.net.ShardActivationPacket(activationStack));

        ServerLevel level = (ServerLevel) player.level();
        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();
        if (kind == Kind.SPLINTER) {
            level.playSound(null, player.blockPosition(), WardSounds.PIN_SET.get(),
                    SoundSource.PLAYERS, 0.92f, 0.72f);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.65f, 1.48f);
            dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.RELIC_SPLINTER, 0.95f);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "The splinter dissolves into a pale ward-sign. Your next ward will yield half again as much.")
                    .withStyle(ChatFormatting.AQUA), false);
        } else if (kind == Kind.ELDRITCH_SHARD) {
            level.playSound(null, player.blockPosition(), WardSounds.EYE_CHOOSE.get(), SoundSource.PLAYERS, 1.0f, 0.54f);
            level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.72f, 0.68f);
            dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.RELIC_ELDRITCH, 1.05f);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "The shard folds into your shadow. The next warded chest will repeat what it contains.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        } else if (kind == Kind.ECHO_SHARD) {
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.PLAYERS, 0.8f, 0.74f);
            level.playSound(null, player.blockPosition(), WardSounds.MARK.get(), SoundSource.PLAYERS, 0.46f, 1.26f);
            dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y + 0.1, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.RELIC_ECHO, 1.0f);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "An echo settles behind your eyes. The next ward must show you something less familiar.")
                    .withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC), false);
        } else {
            level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.35f, 0.55f);
            level.playSound(null, player.blockPosition(), WardSounds.HEART_USE.get(), SoundSource.PLAYERS, 0.62f, 0.72f);
            dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.RELIC_BLOODGLASS, 0.9f);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "Bloodglass warms in your hand. It will count hostile deaths for four minutes.")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), false);
        }
        return true;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return kind == Kind.ELDRITCH_SHARD || super.isFoil(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide && entity instanceof Player player) {
            if (isSelected || player.getOffhandItem() == stack) {
                if (level.getGameTime() % (kind == Kind.ELDRITCH_SHARD ? 4 : 7) == 0) {
                    double x = player.getX() + (level.random.nextDouble() - 0.5) * 0.55;
                    double y = player.getY() + 1.0 + level.random.nextDouble() * 0.35;
                    double z = player.getZ() + (level.random.nextDouble() - 0.5) * 0.55;
                    spawnHeldParticle(level, x, y, z);
                }
            }
        }
        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        Level level = entity.level();
        if (!level.isClientSide) return false;
        if (level.getGameTime() % (kind == Kind.ELDRITCH_SHARD ? 3 : 6) == 0) {
            double x = entity.getX();
            double y = entity.getY() + 0.06;
            double z = entity.getZ();
            spawnGroundAura(level, x, y, z, entity.onGround());
        }
        return false;
    }

    private void spawnHeldParticle(Level level, double x, double y, double z) {
        if (kind == Kind.ELDRITCH_SHARD) {
            level.addParticle(ParticleTypes.PORTAL, x, y, z, (level.random.nextDouble() - 0.5) * 0.05, 0.01, (level.random.nextDouble() - 0.5) * 0.05);
            level.addParticle(new DustParticleOptions(new Vector3f(0.68f, 0.35f, 0.95f), 1.0f), x, y + 0.02, z, 0.0, 0.008, 0.0);
        } else if (kind == Kind.ECHO_SHARD) {
            level.addParticle(new DustParticleOptions(new Vector3f(0.36f, 0.62f, 0.95f), 0.9f), x, y, z, 0.0, 0.01, 0.0);
        } else if (kind == Kind.BLOODGLASS_SHARD) {
            level.addParticle(new DustParticleOptions(new Vector3f(0.72f, 0.12f, 0.20f), 0.9f), x, y, z, 0.0, 0.008, 0.0);
        } else {
            level.addParticle(new DustParticleOptions(new Vector3f(0.46f, 0.96f, 0.86f), 0.9f), x, y, z, 0.0, 0.01, 0.0);
        }
    }

    private void spawnGroundAura(Level level, double x, double y, double z, boolean grounded) {
        float spin = (level.getGameTime() % 40) / 40f * Mth.TWO_PI;
        int count = kind == Kind.ELDRITCH_SHARD ? 3 : 2;
        double radius = grounded ? (kind == Kind.ELDRITCH_SHARD ? 0.34 : 0.26) : 0.12;
        Vector3f color = kind == Kind.ELDRITCH_SHARD ? new Vector3f(0.65f, 0.28f, 0.96f)
                : kind == Kind.ECHO_SHARD ? new Vector3f(0.34f, 0.58f, 0.92f)
                : kind == Kind.BLOODGLASS_SHARD ? new Vector3f(0.70f, 0.12f, 0.20f)
                : new Vector3f(0.49f, 0.95f, 0.86f);
        for (int i = 0; i < count; i++) {
            double ang = spin + i * (Mth.TWO_PI / count);
            double px = x + Math.cos(ang) * radius;
            double pz = z + Math.sin(ang) * radius;
            level.addParticle(new DustParticleOptions(color, kind == Kind.ELDRITCH_SHARD ? 1.15f : 0.9f), px, y + 0.03, pz, 0.0, 0.01, 0.0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wardbound." + kind.id + ".desc")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("wardbound.tooltip." + kind.id)
                .withStyle(ChatFormatting.GRAY));
        ChatFormatting fxColor = kind == Kind.ELDRITCH_SHARD ? ChatFormatting.LIGHT_PURPLE
                : kind == Kind.ECHO_SHARD ? ChatFormatting.AQUA
                : kind == Kind.BLOODGLASS_SHARD ? ChatFormatting.DARK_RED : ChatFormatting.YELLOW;
        tooltip.add(Component.translatable("wardbound.tooltip." + kind.id + "_fx")
                .withStyle(fxColor, ChatFormatting.ITALIC));
        if (kind == Kind.ELDRITCH_SHARD) {
            tooltip.add(Component.translatable("wardbound.tooltip.eldritch_storm")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        }
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
