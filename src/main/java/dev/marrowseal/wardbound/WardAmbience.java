package dev.marrowseal.wardbound;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * Warded containers hum.
 *
 * <p>Until now the mod said nothing until you touched something. Nineteen pieces
 * of foley, and a base full of sealed chests was silent; you found out a
 * container was warded by walking into it. A ward that can be heard from a few
 * blocks away turns "I should check that chest" into "there is something in that
 * room", which is a different and much better sentence for a mod about locks.
 *
 * <p>Unsigned seals hum a minor third lower and less steadily, so by the time
 * you have met a few you know what you have walked into before you can see it.
 *
 * <p>Cheap on purpose. Its scan period is configurable (80 ticks by default), and it looks only at the chunk each
 * player is standing in and its immediate neighbours, and plays at most one
 * sound per player: a scan of every loaded block entity would be a far worse
 * trade than the effect is worth.
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class WardAmbience {

    private WardAmbience() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!WardConfig.ambienceEnabled) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            // Offset by dimension so every world does not sing on the same tick.
            long tick = level.getGameTime() + level.dimension().location().hashCode();
            if (Math.floorMod(tick, WardConfig.ambiencePeriodTicks) != 0) continue;

            for (ServerPlayer player : level.players()) {
                hum(level, player);
            }
        }
    }

    private static void hum(ServerLevel level, ServerPlayer player) {
        BlockPos at = player.blockPosition();
        int cx = at.getX() >> 4;
        int cz = at.getZ() >> 4;

        BlockPos best = null;
        boolean bestUnsigned = false;
        double range = WardConfig.ambienceRange;
        double bestDist = range * range;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) continue;

                for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                    if (!(e.getValue() instanceof RandomizableContainerBlockEntity)) continue;
                    if (!e.getValue().getPersistentData().getBoolean(ChestValuator.TAG_LOCKED)) continue;

                    double d = e.getKey().distSqr(at);
                    if (d >= bestDist) continue;
                    bestDist = d;
                    best = e.getKey();
                    bestUnsigned = e.getValue().getPersistentData()
                            .getBoolean(ChestValuator.TAG_UNSIGNED);
                }
            }
        }

        if (best == null) return;

        // Quiet, and quieter the further off it is. It is meant to be something
        // you notice rather than something you hear.
        float near = (float) (1.0 - Math.sqrt(bestDist) / range);
        float volume = WardConfig.ambienceVolume * 0.72f * (0.25f + near * 0.75f);
        level.playSound(null, best,
                (bestUnsigned ? WardSounds.WARD_HUM_UNSIGNED : WardSounds.WARD_HUM).get(),
                SoundSource.BLOCKS, volume, 1.08f + (best.hashCode() % 7) * 0.012f);
    }
}
