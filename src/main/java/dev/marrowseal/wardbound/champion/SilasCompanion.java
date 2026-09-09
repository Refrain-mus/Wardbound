package dev.marrowseal.wardbound.champion;

import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.WardHistory;
import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.boss.AshenCuratorEntity;
import dev.marrowseal.wardbound.boss.MourningNotaryEntity;
import dev.marrowseal.wardbound.boss.PaleGamblerEntity;
import dev.marrowseal.wardbound.boss.WardBosses;
import dev.marrowseal.wardbound.ancientsmith.AncientSmithProgression;
import dev.marrowseal.wardbound.item.WornScabbardRingItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/** Moves the one world-owned Silas identity into and out of a temporary companion call. */
public final class SilasCompanion {

    private SilasCompanion() {}

    public static boolean call(ServerPlayer player, ItemStack ring) {
        if (player == null || player.getServer() == null || ring == null || ring.isEmpty()) return false;
        if (!WornScabbardRingItem.belongsTo(ring, player)) {
            WardHud.send(player, "THE RING DOES NOT KNOW YOUR HAND", WardHud.Mood.WARD);
            return false;
        }
        if (AncientSmithProgression.active(player) || player.level().dimension().equals(AncientSmithProgression.REALM)) {
            WardHud.send(player, "THE RING STAYS COLD // THIS FORGE ADMITS NO SECOND WITNESS", WardHud.Mood.WARD);
            return false;
        }
        if (SilasMasterEncounter.finalPhaseNearby(player)) {
            WardHud.send(player, "THE RING JERKS COLD // THE MASTER WILL NOT ADMIT HIM TO THE FINAL PHASE", WardHud.Mood.DANGER);
            return false;
        }
        LockData data = LockData.get(player.getServer());
        long now = player.getServer().overworld().getGameTime();
        long until = data.uniqueLong(player.getUUID(), "silas_ring_cooldown_until");
        if (until != Long.MIN_VALUE && until > now) {
            long seconds = Math.max(1, (until - now + 19) / 20);
            WardHud.send(player, "THE IRON IS COLD // " + seconds + "s", WardHud.Mood.WARD);
            return false;
        }

        MinecraftServer server = player.getServer();
        ChampionWorldData world = ChampionWorldData.get(server);
        if (world.person == null) {
            WardHud.send(player, "THE RING HAS NO ROAD TO FOLLOW", WardHud.Mood.WARD);
            return false;
        }
        DefeatedChampionEntity current = findCurrent(server, world.person);
        if (current != null && current.companionActive()) {
            WardHud.send(player, current.companionOwnerIs(player.getUUID())
                    ? "SILAS VEYR // PRESENT"
                    : "THE RING IS ANSWERED ELSEWHERE", WardHud.Mood.WARD);
            return false;
        }

        ServerLevel target = player.serverLevel();
        BlockPos spot = companionSpot(target, player.blockPosition());
        if (spot == null) {
            WardHud.send(player, "NO SAFE GROUND ANSWERS THE CALL", WardHud.Mood.WARD);
            return false;
        }

        BlockPos returnCamp = current != null ? current.campPosition() : (world.roadPosition.equals(BlockPos.ZERO) ? world.position : world.roadPosition);
        ServerLevel originalLevel = current != null && current.level() instanceof ServerLevel sl ? sl : null;
        CompoundTag snapshot = world.snapshot.copy();
        if (current != null) {
            snapshot = new CompoundTag();
            current.saveWithoutId(snapshot);
        }

        // Build the replacement before touching the world-owned Silas. A failed factory call must
        // never delete the only physical incarnation.
        DefeatedChampionEntity next = WardBosses.DEFEATED_CHAMPION.get().create(target);
        if (next == null) return false;
        if (!snapshot.isEmpty()) next.load(snapshot.copy());
        long nextIncarnation = world.incarnation + 1;
        next.setUUID(world.person);
        next.arrive(nextIncarnation, spot);
        next.startCompanion(player.getUUID(), returnCamp, ChampionConfig.COMPANION_SECONDS.get() * 20);
        next.moveTo(spot.getX() + .5, spot.getY(), spot.getZ() + .5, player.getYRot() + 180f, 0f);
        next.setYBodyRot(player.getYRot() + 180f);
        next.setYHeadRot(player.getYRot() + 180f);

        if (current != null) current.discard();
        if (!target.addFreshEntity(next)) {
            // Roll back the old incarnation if the cross-dimension insertion fails (duplicate UUID,
            // mod cancellation, chunk race, etc.). Do not advance the world token on failure.
            if (current != null && restoreSnapshot(originalLevel, world, snapshot) == null)
                dev.marrowseal.wardbound.Wardbound.LOG.error("Failed to roll Silas Veyr back after a companion transfer failure.");
            return false;
        }
        world.incarnation = nextIncarnation;
        world.position = spot;
        world.arrival = now;
        world.lastSeen = now;
        world.setDirty();
        world.remember(next);
        int cooldownTicks = ChampionConfig.CALL_COOLDOWN_SECONDS.get() * 20;
        data.setUniqueLong(player.getUUID(), "silas_ring_cooldown_until", now + (long) cooldownTicks);
        player.getCooldowns().addCooldown(ring.getItem(), cooldownTicks);
        WardHistory.recordSpecial(player, "WARD", "SILAS VEYR // PRESENT // The worn scabbard ring was answered for " + ChampionConfig.COMPANION_SECONDS.get() + " seconds.");
        WardHud.send(player, "SILAS VEYR // PRESENT", WardHud.Mood.REWARD);
        SilasMasterEncounter.onCall(player);
        target.playSound(null, spot, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, .45f, .62f);
        target.playSound(null, spot, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .34f, .58f);
        target.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                spot.getX() + .5, spot.getY() + 1.0, spot.getZ() + .5, 18, .35, .7, .35, .02);
        return true;
    }

    public static void returnToRoad(DefeatedChampionEntity current) {
        if (current == null || current.level().isClientSide || !(current.level() instanceof ServerLevel from)) return;
        MinecraftServer server = from.getServer();
        ChampionWorldData world = ChampionWorldData.get(server);
        if (!world.accepts(current)) {
            current.discard();
            return;
        }
        BlockPos returnCamp = current.companionReturnCamp();
        CompoundTag snapshot = new CompoundTag();
        current.saveWithoutId(snapshot);
        from.playSound(null, current.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.NEUTRAL, .34f, .72f);
        from.playSound(null, current.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, .26f, .52f);
        from.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                current.getX(), current.getY() + 1.0, current.getZ(), 16, .3, .65, .3, .015);
        ServerLevel home = server.overworld();
        home.getChunkAt(returnCamp);
        BlockPos safe = returnSpot(home, returnCamp);
        DefeatedChampionEntity next = WardBosses.DEFEATED_CHAMPION.get().create(home);
        if (next == null) return; // Keep the current incarnation alive; retry on the next companion tick.
        next.load(snapshot.copy());
        long nextIncarnation = world.incarnation + 1;
        next.setUUID(world.person);
        next.arrive(nextIncarnation, safe);
        next.moveTo(safe.getX() + .5, safe.getY(), safe.getZ() + .5, current.getYRot(), 0f);

        current.discard();
        if (!home.addFreshEntity(next)) {
            if (restoreSnapshot(from, world, snapshot) == null)
                dev.marrowseal.wardbound.Wardbound.LOG.error("Failed to roll Silas Veyr back after his road return failed.");
            return;
        }
        world.incarnation = nextIncarnation;
        world.position = safe;
        world.roadPosition = safe;
        world.arrival = home.getGameTime();
        world.lastSeen = world.arrival;
        world.setDirty();
        world.remember(next);
    }

    private static DefeatedChampionEntity restoreSnapshot(ServerLevel level, ChampionWorldData world, CompoundTag snapshot) {
        if (level == null || world == null || world.person == null || snapshot == null || snapshot.isEmpty()) return null;
        DefeatedChampionEntity restored = WardBosses.DEFEATED_CHAMPION.get().create(level);
        if (restored == null) return null;
        restored.load(snapshot.copy());
        restored.setUUID(world.person);
        if (!level.addFreshEntity(restored)) return null;
        world.remember(restored);
        return restored;
    }

    public static DefeatedChampionEntity findCurrent(MinecraftServer server, UUID id) {
        if (server == null || id == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof DefeatedChampionEntity silas && !silas.isRemoved()) return silas;
        }
        return null;
    }

    public static boolean masterEncounterNearby(ServerPlayer player) {
        if (player == null) return false;
        AABB area = player.getBoundingBox().inflate(128);
        ServerLevel level = player.serverLevel();
        UUID id = player.getUUID();
        return !level.getEntitiesOfClass(PaleGamblerEntity.class, area, e -> !e.isRemoved() && e.includes(id)).isEmpty()
                || !level.getEntitiesOfClass(AshenCuratorEntity.class, area, e -> !e.isRemoved() && e.includes(id)).isEmpty()
                || !level.getEntitiesOfClass(MourningNotaryEntity.class, area, e -> !e.isRemoved() && e.includes(id)).isEmpty();
    }

    private static BlockPos companionSpot(ServerLevel level, BlockPos center) {
        int[][] offsets = {{2,0},{-2,0},{0,2},{0,-2},{3,1},{-3,-1},{1,-3},{-1,3},{0,0}};
        for (int[] o : offsets) {
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos p = center.offset(o[0], dy, o[1]);
                if (simpleClear(level, p)) return p;
            }
        }
        return null;
    }

    private static BlockPos returnSpot(ServerLevel level, BlockPos preferred) {
        if (simpleClear(level, preferred)) return preferred;
        for (int r = 1; r <= 6; r++) {
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                if (Math.abs(x) != r && Math.abs(z) != r) continue;
                BlockPos p = preferred.offset(x, 0, z);
                for (int y = -2; y <= 3; y++) if (simpleClear(level, p.offset(0, y, 0))) return p.offset(0, y, 0);
            }
        }
        BlockPos spawn = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level.getSharedSpawnPos()).above();
        level.getChunkAt(spawn);
        if (simpleClear(level, spawn)) return spawn;
        for (int r = 1; r <= 8; r++) {
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                if (Math.abs(x) != r && Math.abs(z) != r) continue;
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.offset(x, 0, z)).above();
                if (simpleClear(level, top)) return top;
            }
        }
        return spawn;
    }

    private static boolean simpleClear(ServerLevel level, BlockPos p) {
        if (!level.isInWorldBounds(p.above(2)) || !level.getWorldBorder().isWithinBounds(p)) return false;
        BlockPos ground = p.below();
        if (!level.hasChunkAt(ground)) return false;
        var state = level.getBlockState(ground);
        if (!state.isFaceSturdy(level, ground, Direction.UP) || !level.getFluidState(ground).isEmpty()
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)) return false;
        for (int y = 0; y < 3; y++) {
            BlockPos q = p.above(y);
            if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty() || !level.getFluidState(q).isEmpty()) return false;
        }
        return true;
    }
}
