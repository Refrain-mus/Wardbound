package dev.marrowseal.wardbound.champion;

import dev.marrowseal.wardbound.CardMaster;
import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.MasterStory;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.boss.WardBosses;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class ChampionEncounters {
    private ChampionEncounters() {}

    public static boolean eligible(ServerPlayer p) {
        if (p == null || p.isSpectator() || !p.isAlive()) return false;
        var data = LockData.get(p.getServer());
        return SilasProgression.encounterUnlocked(p)
                && SilasProgression.maxChapter(data, p.getUUID()) >= ChampionConfig.CHAPTER.get();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void join(EntityJoinLevelEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof DefeatedChampionEntity champion)) return;
        var d = ChampionWorldData.get(level.getServer());
        if ((!level.dimension().equals(Level.OVERWORLD) && !champion.companionActive()) || !d.accepts(champion)) {
            e.setCanceled(true);
            return;
        }
        for (ServerLevel other : level.getServer().getAllLevels()) {
            Entity existing = other.getEntity(champion.getUUID());
            if (existing != null && existing != champion && !existing.isRemoved()) {
                e.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void targetChange(LivingChangeTargetEvent e) {
        if (e.getNewTarget() instanceof DefeatedChampionEntity silas && !silas.companionActive()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent e) {
        if (e.getLevel() instanceof ServerLevel level && e.getEntity() instanceof DefeatedChampionEntity champion && champion.getHealth() > 0) {
            ChampionWorldData.get(level.getServer()).remember(champion);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        var level = server.overworld();
        long now = level.getGameTime();
        var d = ChampionWorldData.get(server);
        DefeatedChampionEntity activeSilas = d.person == null ? null : SilasCompanion.findCurrent(server, d.person);
        if (activeSilas != null && activeSilas.companionActive()) {
            d.lastSeen = now;
            d.setDirty();
            return;
        }
        if (!ChampionConfig.ENABLED.get() || now < d.nextCheck) return;
        d.nextCheck = now + ChampionConfig.INTERVAL.get();
        d.setDirty();

        List<ServerPlayer> candidates = level.players().stream().filter(ChampionEncounters::eligible).toList();
        if (candidates.isEmpty()) return;

        if (d.person != null) {
            var old = activeSilas;
            boolean fighting = old != null && old.getTarget() != null;
            boolean observed = level.players().stream().anyMatch(p -> !p.isSpectator() && p.blockPosition().distSqr(d.position) < 96 * 96);
            if (observed) {
                d.lastSeen = now;
                d.setDirty();
                return;
            }
            if (!ChampionIdentity.travelReady(now, d.arrival, d.lastSeen, ChampionConfig.TRAVEL.get(), observed, fighting)) return;
        }

        d.eligibleChecks++;
        d.setDirty();
        if (d.eligibleChecks < ChampionConfig.PITY.get() && level.random.nextDouble() >= ChampionConfig.CHANCE.get()) return;

        ServerPlayer player = candidates.get(level.random.nextInt(candidates.size()));
        BlockPos camp = findCamp(player);
        if (camp != null && place(player, camp, false)) {
            WardHud.send(player, Component.translatable("silas.encounter_hint").getString(), WardHud.Mood.WARD);
        }
    }

    private static BlockPos findCamp(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2, radius = 28 + level.random.nextInt(17);
            BlockPos probe = p.blockPosition().offset((int) (Math.cos(angle) * radius), 0, (int) (Math.sin(angle) * radius));
            if (!level.hasChunkAt(probe)) continue;
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, probe);
            if (Math.abs(pos.getY() - p.getY()) > 12 || !clear(level, pos)) continue;
            if (level.players().stream().anyMatch(other -> other.distanceToSqr(pos.getX() + .5, pos.getY(), pos.getZ() + .5) < 24 * 24)) continue;
            if (!level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(pos).inflate(8), m -> m instanceof net.minecraft.world.entity.monster.Monster).isEmpty()) continue;
            return pos;
        }
        return null;
    }

    public static boolean clear(ServerLevel l, BlockPos p) {
        if (!l.hasChunkAt(p) || !l.getWorldBorder().isWithinBounds(p) || !l.isInWorldBounds(p.above(3))) return false;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockPos ground = p.offset(x, -1, z);
            if (!l.hasChunkAt(ground)) return false;
            var block = l.getBlockState(ground);
            if (!block.isFaceSturdy(l, ground, Direction.UP) || !l.getFluidState(ground).isEmpty() || block.is(Blocks.MAGMA_BLOCK) || block.is(Blocks.CACTUS) || block.is(Blocks.CAMPFIRE) || block.is(Blocks.SOUL_CAMPFIRE)) return false;
            for (int y = 0; y < 3; y++) {
                BlockPos q = p.offset(x, y, z);
                if (!l.getBlockState(q).getCollisionShape(l, q).isEmpty() || !l.getFluidState(q).isEmpty() || l.getBlockState(q).is(Blocks.FIRE) || l.getBlockState(q).is(Blocks.SOUL_FIRE)) return false;
            }
        }
        return l.noCollision(new AABB(p.getX() - .1, p.getY(), p.getZ() - .1, p.getX() + 1.1, p.getY() + 2.2, p.getZ() + 1.1));
    }

    /** All creation and relocation uses one serialized, world-owned identity. */
    public static boolean place(ServerPlayer p, BlockPos camp, boolean debug) {
        var level = p.serverLevel();
        if (!level.dimension().equals(Level.OVERWORLD) || !clear(level, camp)) return false;
        var d = ChampionWorldData.get(level.getServer());
        Entity previous = d.person == null ? null : findEntity(level.getServer(), d.person);
        if (previous != null && !(previous instanceof DefeatedChampionEntity)) {
            Wardbound.LOG.error("Silas Veyr identity UUID is occupied by another entity type; refusing destructive relocation.");
            return false;
        }
        DefeatedChampionEntity old = previous instanceof DefeatedChampionEntity c ? c : null;
        ServerLevel oldLevel = old != null && old.level() instanceof ServerLevel sl ? sl : null;
        CompoundTag oldSnapshot = null;
        if (old != null) {
            if (old.getTarget() != null || (!debug && old.companionActive())) return false;
            if (!debug && oldLevel != null && oldLevel.players().stream().anyMatch(w -> w.distanceToSqr(old) < 96 * 96)) return false;
            d.remember(old);
            oldSnapshot = new CompoundTag();
            old.saveWithoutId(oldSnapshot);
        }
        var next = WardBosses.DEFEATED_CHAMPION.get().create(level);
        if (next == null) return false;
        if (!d.snapshot.isEmpty()) next.load(d.snapshot.copy());
        UUID person = d.person == null ? UUID.randomUUID() : d.person;
        long nextIncarnation = d.incarnation + 1;
        next.setUUID(person);
        next.arrive(nextIncarnation, camp);
        next.moveTo(camp.getX() + .5, camp.getY(), camp.getZ() + .5, p.getYRot() + 180, 0);
        next.setYBodyRot(p.getYRot() + 180);
        next.setYHeadRot(p.getYRot() + 180);

        // Commit the world-owned identity only after the replacement entity actually enters the
        // level. A failed insertion must not advance the incarnation token or move the remembered
        // camp, otherwise the still-valid snapshot becomes permanently stale.
        if (old != null) old.discard();
        boolean added = level.addFreshEntity(next);
        if (!added) {
            if (oldSnapshot != null && restoreSnapshot(oldLevel, d, oldSnapshot) == null)
                Wardbound.LOG.error("Silas Veyr relocation failed and the previous physical incarnation could not be restored.");
            d.arrival = level.getGameTime() - ChampionConfig.TRAVEL.get();
            d.lastSeen = d.arrival;
            d.setDirty();
            Wardbound.LOG.warn("Silas Veyr relocation insertion failed; identity token was not advanced.");
            return false;
        }
        d.person = person;
        d.incarnation = nextIncarnation;
        d.position = camp;
        d.roadPosition = camp;
        d.arrival = level.getGameTime();
        d.lastSeen = d.arrival;
        d.eligibleChecks = 0;
        d.setDirty();
        d.remember(next);
        return true;
    }


    private static Entity findEntity(net.minecraft.server.MinecraftServer server, UUID id) {
        if (server == null || id == null) return null;
        for (ServerLevel world : server.getAllLevels()) {
            Entity entity = world.getEntity(id);
            if (entity != null && !entity.isRemoved()) return entity;
        }
        return null;
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
}
