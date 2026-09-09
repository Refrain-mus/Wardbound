package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.ancientsmith.AncientSmithProgression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Stable, prebuilt Master arena routing. Invocation happens in the ordinary world, but the actual
 * fight is moved into a private End-sky recess so terrain, fluids, villages and modded structures
 * cannot invalidate the encounter choreography.
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class MasterArenaManager {
    public static final ResourceLocation REALM_ID = new ResourceLocation(Wardbound.MODID, "master_recess");
    public static final ResourceKey<Level> REALM = ResourceKey.create(Registries.DIMENSION, REALM_ID);
    private static final ResourceLocation LAST_TABLE_TEMPLATE = new ResourceLocation(Wardbound.MODID, "the_last_table");
    private static final ResourceLocation LAST_TABLE_CLEAR_TEMPLATE = new ResourceLocation(Wardbound.MODID, "the_last_table_clear");
    private static final int MASTER_CELL_SPACING = 256;
    private static final int LAST_TABLE_SIZE_X = 190;
    private static final int LAST_TABLE_SIZE_Z = 173;
    // WorldEdit copy origin was the player's feet; the unique crying-obsidian floor marker is
    // one block below it at local (89,21,79), so boss feet are local (89,22,79).
    private static final BlockPos LAST_TABLE_BOSS_FOOT = new BlockPos(89, 22, 79);

    public enum Kind { GAMBLER, CURATOR, NOTARY, MAESTRO }
    public record Arena(ServerLevel level, BlockPos center) {}

    private static final String ROOT = "WardboundMasterArena";
    private static final String ACTIVE = "active";
    private static final String KIND = "kind";
    private static final String CENTER = "center";
    private static final String RETURN_DIM = "return_dim";
    private static final String RX = "return_x", RY = "return_y", RZ = "return_z";
    private static final String RYAW = "return_yaw", RPITCH = "return_pitch";
    private static final String EMPTY_TICKS = "empty_ticks";
    private static final String DEATH_EXIT = "death_exit";
    private static final String VICTORY_UNTIL = "victory_until";
    private static final int VICTORY_LINGER_TICKS = 30 * 20;
    private static final int FLOOR_RADIUS = 25;
    private static final int CLEAR_HEIGHT = 14;
    // Entity-only sweep radius. This covers the entire authored Last Table footprint while still
    // leaving a healthy gap before the next 256-block private cell.
    private static final int STALE_ENTITY_RADIUS = 112;

    private MasterArenaManager() {}

    public static boolean available(ServerPlayer player) {
        return player != null && player.getServer() != null && player.getServer().getLevel(REALM) != null;
    }

    public static boolean active(ServerPlayer player) {
        return player != null && player.getPersistentData().getCompound(ROOT).getBoolean(ACTIVE);
    }

    public static Arena enter(ServerPlayer player, Kind kind) {
        if (player == null || player.getServer() == null || kind == null || active(player)) return null;
        if (AncientSmithProgression.active(player) || player.level().dimension().equals(AncientSmithProgression.REALM)) {
            WardHud.send(player, "MASTER RECESS // THE ANCIENT FORGE HAS PRIOR CLAIM ON THIS WITNESS", WardHud.Mood.WARD);
            return null;
        }
        if (player.level().dimension().equals(REALM)) {
            WardHud.send(player, "MASTER RECESS // A NEW INVOCATION CANNOT USE THE RECESS ITSELF AS A RETURN ROAD", WardHud.Mood.WARD);
            return null;
        }
        ServerLevel realm = player.getServer().getLevel(REALM);
        if (realm == null) {
            Wardbound.LOG.error("Master arena dimension {} is unavailable.", REALM_ID);
            WardHud.send(player, "MASTER RECESS // THE PREPARED ARENA COULD NOT BE FOUND", WardHud.Mood.DANGER);
            return null;
        }

        BlockPos center = cellCenter(player.getUUID());
        realm.getChunkAt(center);
        // A death/forced exit can leave the previous boss alive for its own collapse/empty timeout.
        // This is the entrant's deterministic private cell, so remove only stale Masters that still
        // claim this same player before deciding whether another encounter owns the cell.
        cleanupOwnedStaleMasters(realm, center, player.getUUID());
        if (cellOccupiedByOther(realm, center, player)) {
            WardHud.send(player, "MASTER RECESS // THIS PRIVATE CELL IS ALREADY OCCUPIED; TRY AGAIN AFTER THE OTHER ENCOUNTER CLOSES", WardHud.Mood.DANGER);
            return null;
        }

        // Do not mutate an existing nested ROOT tag until every admission check has passed.
        // CompoundTag#getCompound may return the live nested object; mutating it before the
        // collision check could therefore leave ACTIVE=true even when enter() returned null.
        CompoundTag root = new CompoundTag();
        root.putBoolean(ACTIVE, true);
        root.putString(KIND, kind.name());
        root.putString(RETURN_DIM, player.level().dimension().location().toString());
        root.putDouble(RX, player.getX());
        root.putDouble(RY, player.getY());
        root.putDouble(RZ, player.getZ());
        root.putFloat(RYAW, player.getYRot());
        root.putFloat(RPITCH, player.getXRot());
        root.putInt(EMPTY_TICKS, 0);
        root.putLong(CENTER, center.asLong());
        player.getPersistentData().put(ROOT, root);

        if (!prepareArena(realm, center, kind)) {
            clear(player);
            WardHud.send(player, "MASTER RECESS // THE PREPARED ARENA FAILED TO MATERIALIZE", WardHud.Mood.DANGER);
            return null;
        }
        Vec3 spawn = playerSpawn(center, kind);
        player.teleportTo(realm, spawn.x, spawn.y, spawn.z, 180f, 0f);
        if (!player.level().dimension().equals(REALM)) {
            // Another mod/event may veto dimension travel. Never return a valid Arena in that case,
            // otherwise the caller would spawn a boss in an empty cell while the player stayed home.
            clear(player);
            Wardbound.LOG.warn("Master recess teleport was vetoed for {}.", player.getGameProfile().getName());
            return null;
        }
        player.fallDistance = 0;
        WardHud.send(player, switch (kind) {
            case GAMBLER -> "THE LAST TABLE // THE INVITATION CLOSES BEHIND YOU";
            case CURATOR -> "ASH ARCHIVE // THE WORLD HAS BEEN REPLACED BY A PREPARED INDEX";
            case NOTARY -> "FINAL WITNESS // THE COURT WAS ALREADY IN SESSION";
            case MAESTRO -> "MAESTRO STAGE // THE FIRST DOWNBEAT HAS ALREADY BEEN COUNTED";
        }, WardHud.Mood.GLITCH);
        return new Arena(realm, center);
    }

    public static void beginVictoryLinger(ServerPlayer player, Kind kind) {
        if (player == null || player.getServer() == null || kind == null || !active(player)) return;
        CompoundTag root = player.getPersistentData().getCompound(ROOT);
        if (!root.getString(KIND).equals(kind.name())) return;
        long until = player.serverLevel().getGameTime() + VICTORY_LINGER_TICKS;
        root.putLong(VICTORY_UNTIL, until);
        root.putInt(EMPTY_TICKS, 0);
        player.getPersistentData().put(ROOT, root);
        MasterInvocationRelics.consumeOnVictory(player, kind);
    }

    public static void abort(ServerPlayer player) {
        if (player == null || player.getServer() == null || !active(player)) return;
        CompoundTag root = player.getPersistentData().getCompound(ROOT).copy();
        ServerLevel realm = player.getServer().getLevel(REALM);
        BlockPos center = root.contains(CENTER) ? BlockPos.of(root.getLong(CENTER)) : null;
        teleportBack(player);
        clear(player);
        // A private cell is disposable encounter infrastructure. Remove the old boss body,
        // combat residue and death drops now instead of waiting for a later Master to discover it.
        if (realm != null && center != null) cleanupExitResidue(realm, center);
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        MasterInvocationRelics.deliverPending(player);
        MasterRewardDelivery.tick(player);
        if (!active(player)) {
            // The recess is private infrastructure, not a normal travel destination. If stale/corrupt
            // player NBT or an interrupted teleport leaves somebody here without an arena claim, eject
            // them rather than leaving them permanently stranded in an empty cell. Preserve a valid
            // stale return anchor when one exists; otherwise use the Overworld spawn.
            if (player.level().dimension().equals(REALM) && player.getServer() != null) {
                CompoundTag stale = player.getPersistentData().getCompound(ROOT);
                if (stale.contains(RETURN_DIM) && stale.contains(RX) && stale.contains(RY) && stale.contains(RZ)) {
                    teleportBack(player);
                    clear(player);
                } else {
                    ServerLevel target = player.getServer().overworld();
                    Vec3 fallback = Vec3.atBottomCenterOf(target.getSharedSpawnPos()).add(0, 1, 0);
                    Vec3 safe = safeReturn(target, player, fallback);
                    player.teleportTo(target, safe.x, safe.y, safe.z, player.getYRot(), player.getXRot());
                    player.fallDistance = 0;
                }
            }
            return;
        }
        CompoundTag root = player.getPersistentData().getCompound(ROOT);
        if (!player.level().dimension().equals(REALM)) {
            ServerLevel realm = player.getServer().getLevel(REALM);
            BlockPos staleCenter = root.contains(CENTER) ? BlockPos.of(root.getLong(CENTER)) : null;
            // Death is the legitimate hard exit. LivingDropsEvent still needs the claim first, so
            // LivingDeathEvent marks it and the first post-respawn tick performs the cleanup here.
            if (root.getBoolean(DEATH_EXIT) || root.contains(VICTORY_UNTIL)) {
                clear(player);
                if (realm != null && staleCenter != null) cleanupExitResidue(realm, staleCenter);
                return;
            }
            // Commands, portals and other mod teleports must not become a free escape from a Master.
            // A valid live claim is returned to its private cell instead of being silently forgiven.
            if (realm != null && staleCenter != null && validClaim(root)) {
                Vec3 spawn = playerSpawn(staleCenter, kindFrom(root));
                player.teleportTo(realm, spawn.x, spawn.y, spawn.z, 180f, 0f);
                player.fallDistance = 0;
                WardHud.send(player, "MASTER RECESS // THE CLAIM HAS NOT RELEASED YOU", WardHud.Mood.DANGER, 1600);
                return;
            }
            clear(player);
            if (realm != null && staleCenter != null) cleanupExitResidue(realm, staleCenter);
            return;
        }
        if (!validClaim(root)) {
            ejectToSpawn(player);
            clear(player);
            Wardbound.LOG.warn("Recovered {} from a malformed Master recess claim.", player.getGameProfile().getName());
            return;
        }
        BlockPos center = BlockPos.of(root.getLong(CENTER));
        if (player.getY() < center.getY() - 8 || player.distanceToSqr(Vec3.atCenterOf(center)) > 70 * 70) {
            Vec3 spawn = playerSpawn(center, kindFrom(root));
            player.teleportTo(player.serverLevel(), spawn.x, spawn.y, spawn.z, 180f, 0f);
            player.fallDistance = 0;
        }
        Kind kind;
        try { kind = Kind.valueOf(root.getString(KIND)); }
        catch (Exception ignored) { abort(player); return; }
        if (bossPresent(player, kind, center)) {
            root.putInt(EMPTY_TICKS, 0);
            player.getPersistentData().put(ROOT, root);
            return;
        }
        if (root.contains(VICTORY_UNTIL)) {
            long until = root.getLong(VICTORY_UNTIL);
            if (player.serverLevel().getGameTime() < until) {
                root.putInt(EMPTY_TICKS, 0);
                player.getPersistentData().put(ROOT, root);
                return;
            }
            abort(player);
            return;
        }
        int empty = root.getInt(EMPTY_TICKS) + 1;
        root.putInt(EMPTY_TICKS, empty);
        player.getPersistentData().put(ROOT, root);
        // Non-victory disappearance remains a short fail-safe recovery path.
        if (empty >= 40) abort(player);
    }

    @SubscribeEvent
    public static void protectBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p && p.level().dimension().equals(REALM)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void protectPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.level().dimension().equals(REALM)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void protectFluid(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel l && l.dimension().equals(REALM)) event.setCanceled(true);
    }

    /**
     * ServerPlayer is replaced on respawn. Raw persistent data outside Forge's PlayerPersisted
     * subtree is not guaranteed to survive that replacement, so explicitly carry a live Master
     * claim to the clone. The first post-respawn tick can then see DEATH_EXIT and perform the
     * intended boss/residue cleanup instead of waiting for a future stale-cell sweep.
     */
    @SubscribeEvent
    public static void cloneMasterArenaClaim(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag original = event.getOriginal().getPersistentData().getCompound(ROOT);
        if (!original.getBoolean(ACTIVE)) return;
        player.getPersistentData().put(ROOT, original.copy());
    }

    @SubscribeEvent
    public static void masterPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.level().dimension().equals(REALM)) return;
        CompoundTag root = player.getPersistentData().getCompound(ROOT);
        if (!root.getBoolean(ACTIVE) || root.contains(VICTORY_UNTIL)) return;
        MasterInvocationRelics.noteDeath(player, kindFrom(root));
        root.putBoolean(DEATH_EXIT, true);player.getPersistentData().put(ROOT, root);
        if (root.contains(CENTER)) MasterInvocationRelics.rescueGroundRelics(player, player.serverLevel(), BlockPos.of(root.getLong(CENTER)));
    }

    @SubscribeEvent
    public static void markMasterDeathDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.level().dimension().equals(REALM)) return;
        CompoundTag root = player.getPersistentData().getCompound(ROOT);
        if (!root.getBoolean(ACTIVE)) return;
        // Invocation relics are not part of the death price while their Master still lives. Pull
        // them out of the drop collection and restore them after respawn; every other death drop
        // remains disposable private-encounter residue.
        MasterInvocationRelics.rescueDeathDrops(player, event.getDrops());
        for (ItemEntity drop : event.getDrops())
            if (drop != null) drop.getPersistentData().putBoolean("WardboundMasterTransient", true);
    }

    private static boolean validClaim(CompoundTag root) {
        if (root == null || !root.getBoolean(ACTIVE) || !root.contains(CENTER) || !root.contains(KIND)
                || !root.contains(RX) || !root.contains(RY) || !root.contains(RZ)) return false;
        try { Kind.valueOf(root.getString(KIND)); } catch (Exception ignored) { return false; }
        return Double.isFinite(root.getDouble(RX)) && Double.isFinite(root.getDouble(RY)) && Double.isFinite(root.getDouble(RZ));
    }

    private static void ejectToSpawn(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        ServerLevel target = player.getServer().overworld();
        Vec3 fallback = Vec3.atBottomCenterOf(target.getSharedSpawnPos()).add(0, 1, 0);
        Vec3 safe = safeReturn(target, player, fallback);
        player.teleportTo(target, safe.x, safe.y, safe.z, player.getYRot(), player.getXRot());
        player.fallDistance = 0;
    }

    private static boolean cellOccupiedByOther(ServerLevel level, BlockPos center, ServerPlayer entrant) {
        Vec3 c = Vec3.atCenterOf(center);
        for (ServerPlayer other : level.players()) {
            if (other != entrant && other.distanceToSqr(c) <= 96 * 96) return true;
        }
        AABB box = new AABB(center).inflate(96, 48, 96);
        if (!level.getEntitiesOfClass(PaleGamblerEntity.class, box, e -> !e.isRemoved()).isEmpty()) return true;
        if (!level.getEntitiesOfClass(AshenCuratorEntity.class, box, e -> !e.isRemoved()).isEmpty()) return true;
        if (!level.getEntitiesOfClass(MourningNotaryEntity.class, box, e -> !e.isRemoved()).isEmpty()) return true;
        return !level.getEntitiesOfClass(MaestroEntity.class, box, e -> !e.isRemoved()).isEmpty();
    }

    private static boolean bossPresent(ServerPlayer player, Kind kind, BlockPos center) {
        ServerLevel level = player.serverLevel();
        UUID id = player.getUUID();
        AABB box = new AABB(center).inflate(96, 32, 96);
        return switch (kind) {
            case GAMBLER -> !level.getEntitiesOfClass(PaleGamblerEntity.class, box, e -> !e.isRemoved() && e.includes(id)).isEmpty();
            case CURATOR -> !level.getEntitiesOfClass(AshenCuratorEntity.class, box, e -> !e.isRemoved() && e.includes(id)).isEmpty();
            case NOTARY -> !level.getEntitiesOfClass(MourningNotaryEntity.class, box, e -> !e.isRemoved() && e.includes(id)).isEmpty();
            case MAESTRO -> !level.getEntitiesOfClass(MaestroEntity.class, box, e -> !e.isRemoved() && e.includes(id)).isEmpty();
        };
    }

    private static void teleportBack(ServerPlayer player) {
        CompoundTag root = player.getPersistentData().getCompound(ROOT);
        ResourceLocation dim = ResourceLocation.tryParse(root.getString(RETURN_DIM));
        ServerLevel target = dim == null ? null : player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (target == null) target = player.getServer().overworld();
        Vec3 preferred = new Vec3(root.getDouble(RX), root.getDouble(RY), root.getDouble(RZ));
        Vec3 safe = safeReturn(target, player, preferred);
        float yaw = Float.isFinite(root.getFloat(RYAW)) ? root.getFloat(RYAW) : player.getYRot();
        float pitch = Float.isFinite(root.getFloat(RPITCH)) ? root.getFloat(RPITCH) : player.getXRot();
        player.teleportTo(target, safe.x, safe.y, safe.z, yaw, pitch);
        player.fallDistance = 0;
    }

    private static Vec3 safeReturn(ServerLevel level, ServerPlayer player, Vec3 preferred) {
        Vec3 fallback = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 1, 0);
        if (preferred == null || !Double.isFinite(preferred.x) || !Double.isFinite(preferred.y) || !Double.isFinite(preferred.z)) preferred = fallback;
        if (safeAt(level, player, preferred)) return preferred;
        BlockPos base = BlockPos.containing(preferred);
        for (int r = 1; r <= 6; r++) for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            if (Math.abs(x) != r && Math.abs(z) != r) continue;
            for (int y = -2; y <= 4; y++) {
                Vec3 q = Vec3.atBottomCenterOf(base.offset(x, y, z));
                if (safeAt(level, player, q)) return q;
            }
        }
        return Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 1, 0);
    }

    private static boolean safeAt(ServerLevel level, ServerPlayer player, Vec3 pos) {
        if (pos == null || !Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) return false;
        BlockPos feet = BlockPos.containing(pos), head = feet.above(), floor = feet.below();
        if (!level.isInWorldBounds(head) || !level.getWorldBorder().isWithinBounds(feet)) return false;
        level.getChunkAt(floor);
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(head).isEmpty() || !level.getFluidState(floor).isEmpty()) return false;
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        AABB moved = player.getBoundingBox().move(pos.subtract(player.position()));
        return level.noCollision(player, moved);
    }

    private static void clear(ServerPlayer player) {
        player.getPersistentData().remove(ROOT);
    }

    private static BlockPos cellCenter(UUID id) {
        long h = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 23);
        int gx = Math.floorMod((int) h, 4096) - 2048;
        int gz = Math.floorMod((int) (h >>> 32), 4096) - 2048;
        return new BlockPos(gx * MASTER_CELL_SPACING, 72, gz * MASTER_CELL_SPACING);
    }

    private static Kind kindFrom(CompoundTag root) {
        try { return Kind.valueOf(root.getString(KIND)); }
        catch (Exception ignored) { return Kind.GAMBLER; }
    }

    private static Vec3 playerSpawn(BlockPos center, Kind kind) {
        // The Last Table source build was authored for a player start seventeen blocks south of
        // the boss. Preserve that authored sightline. The two provisional arenas keep their old
        // fifteen-block offset until their own authored structures replace them.
        return kind == Kind.GAMBLER
                ? new Vec3(center.getX() + .5, center.getY(), center.getZ() + 17.5)
                : new Vec3(center.getX() + .5, center.getY(), center.getZ() + 15.5);
    }

    private static boolean prepareArena(ServerLevel level, BlockPos center, Kind kind) {
        // A private cell is canonical, not cumulative. Sweep stale entities before rebuilding so a
        // crashed/aborted previous encounter cannot donate arrows, fangs, clouds, drops or physics
        // entities to the next Master.
        cleanupStaleCell(level, center);
        if (kind == Kind.GAMBLER) {
            boolean prepared = prepareLastTable(level, center);
            // prepareLastTable preloads every authored chunk. Sweep once more afterwards so death
            // drops that lived in an unloaded outer chunk cannot reappear inside the next fight.
            if (prepared) cleanupStaleCell(level, center);
            return prepared;
        }
        // The authored Gambler arena is substantially larger than the provisional combat floor.
        // If this cell previously hosted the Last Table, erase exactly its authored non-air blocks
        // before building Curator/Notary. The crying-obsidian marker makes the check effectively free
        // on cells that have never hosted Gambler.
        if (level.getBlockState(center.below()).is(Blocks.CRYING_OBSIDIAN) && !clearLastTable(level, center)) return false;
        prepareProceduralArena(level, center, kind);
        // clearLastTable/prepareProceduralArena have now loaded the chunks relevant to this cell.
        // A post-build sweep closes the same stale-drop hole when switching from Gambler to another
        // Master arena.
        cleanupStaleCell(level, center);
        return true;
    }

    private static void cleanupOwnedStaleMasters(ServerLevel level, BlockPos center, UUID owner) {
        if (level == null || center == null || owner == null) return;
        AABB box = new AABB(center).inflate(STALE_ENTITY_RADIUS, 112, STALE_ENTITY_RADIUS);
        for (PaleGamblerEntity e : level.getEntitiesOfClass(PaleGamblerEntity.class, box, e -> !e.isRemoved() && e.includes(owner))) e.discard();
        for (AshenCuratorEntity e : level.getEntitiesOfClass(AshenCuratorEntity.class, box, e -> !e.isRemoved() && e.includes(owner))) e.discard();
        for (MourningNotaryEntity e : level.getEntitiesOfClass(MourningNotaryEntity.class, box, e -> !e.isRemoved() && e.includes(owner))) e.discard();
        for (MaestroEntity e : level.getEntitiesOfClass(MaestroEntity.class, box, e -> !e.isRemoved() && e.includes(owner))) e.discard();
        // Preview entities are debug-only and must never survive into a real private encounter.
        for (MourningNotaryPreviewEntity e : level.getEntitiesOfClass(MourningNotaryPreviewEntity.class, box, e -> !e.isRemoved())) e.discard();
    }

    private static void cleanupExitResidue(ServerLevel level, BlockPos center) {
        if (level == null || center == null) return;
        AABB box = new AABB(center).inflate(STALE_ENTITY_RADIUS, 112, STALE_ENTITY_RADIUS);
        for (Entity e : level.getEntities((Entity) null, box, e -> e != null && !e.isRemoved())) {
            // Marked player-death drops count as combat transients; ordinary reward drops are left
            // alone for the short post-victory pickup window.
            if (isCombatTransient(e) || isMasterBody(e) || e instanceof ExperienceOrb) e.discard();
        }
    }

    /** Immediate end-of-encounter cleanup. This intentionally leaves ordinary item drops alone. */
    public static void cleanupCombatTransients(ServerLevel level, BlockPos center) {
        if (level == null || center == null) return;
        AABB box = new AABB(center).inflate(42, 26, 42);
        for (Entity e : level.getEntities((Entity) null, box, e -> e != null && !e.isRemoved())) {
            if (isCombatTransient(e)) e.discard();
        }
    }

    /** Stronger pre-entry sweep; safe because admission already proved that no encounter owns the cell. */
    private static void cleanupStaleCell(ServerLevel level, BlockPos center) {
        if (level == null || center == null) return;
        AABB box = new AABB(center).inflate(STALE_ENTITY_RADIUS, 112, STALE_ENTITY_RADIUS);
        for (Entity e : level.getEntities((Entity) null, box, e -> e != null && !e.isRemoved())) {
            if (isCombatTransient(e) || isMasterBody(e) || e instanceof ItemEntity || e instanceof ExperienceOrb
                    || e instanceof FallingBlockEntity || e instanceof PrimedTnt) e.discard();
        }
    }

    private static boolean isMasterBody(Entity e) {
        return e instanceof PaleGamblerEntity || e instanceof AshenCuratorEntity
                || e instanceof MourningNotaryEntity || e instanceof MaestroEntity || e instanceof MourningNotaryPreviewEntity;
    }

    private static boolean isCombatTransient(Entity e) {
        return e instanceof Projectile || e instanceof EvokerFangs || e instanceof AreaEffectCloud
                || e.getPersistentData().getBoolean("WardboundMasterTransient");
    }

    /**
     * Places the user-authored WorldEdit Last Table arena. The unique crying-obsidian marker is
     * local (89,21,79) and the //copy origin/player feet are local (89,22,79), so mapping that foot
     * anchor onto {@code center} guarantees the boss spawns exactly one block above the marker.
     *
     * The exported template is sparse (authored non-air blocks only) to avoid processing ~3.8M air
     * positions. A small legacy/procedural center volume is cleared only when the marker is absent;
     * repeated Gambler entries can therefore reuse the already-canonical outer architecture.
     */
    private static boolean prepareLastTable(ServerLevel level, BlockPos center) {
        final BlockPos origin = center.offset(-LAST_TABLE_BOSS_FOOT.getX(), -LAST_TABLE_BOSS_FOOT.getY(), -LAST_TABLE_BOSS_FOOT.getZ());

        // Old 62x36x62 Last Table and provisional Curator/Notary geometry live inside this volume.
        // Clear it before the sparse authored template is laid down, but skip the sweep when the
        // crying-obsidian marker proves this exact authored arena is already resident.
        if (!level.getBlockState(center.below()).is(Blocks.CRYING_OBSIDIAN)) clearLegacyCenterVolume(level, center);

        preloadLastTableChunks(level, origin);
        var template = level.getStructureManager().get(LAST_TABLE_TEMPLATE).orElse(null);
        if (template == null) {
            Wardbound.LOG.error("Missing Pale Gambler arena structure template {}", LAST_TABLE_TEMPLATE);
            return false;
        }
        boolean placed = template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.random, 2);
        if (!placed) {
            Wardbound.LOG.error("Failed to place Pale Gambler arena structure {} at {}", LAST_TABLE_TEMPLATE, origin);
            return false;
        }
        BlockPos marker = center.below();
        if (!level.getBlockState(marker).is(Blocks.CRYING_OBSIDIAN)) {
            Wardbound.LOG.error("Pale Gambler arena anchor verification failed: expected crying obsidian at {}", marker);
            return false;
        }
        return true;
    }

    /** Erases only blocks authored by the sparse Gambler structure. */
    private static boolean clearLastTable(ServerLevel level, BlockPos center) {
        final BlockPos origin = center.offset(-LAST_TABLE_BOSS_FOOT.getX(), -LAST_TABLE_BOSS_FOOT.getY(), -LAST_TABLE_BOSS_FOOT.getZ());
        preloadLastTableChunks(level, origin);
        var clear = level.getStructureManager().get(LAST_TABLE_CLEAR_TEMPLATE).orElse(null);
        if (clear == null) {
            Wardbound.LOG.error("Missing Pale Gambler arena clear template {}", LAST_TABLE_CLEAR_TEMPLATE);
            return false;
        }
        boolean placed = clear.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.random, 2);
        if (!placed) Wardbound.LOG.error("Failed to clear Pale Gambler arena structure {} at {}", LAST_TABLE_CLEAR_TEMPLATE, origin);
        return placed;
    }

    private static void preloadLastTableChunks(ServerLevel level, BlockPos origin) {
        // Iterate actual chunk coordinates, not origin+16 samples. The structure origin is usually
        // not chunk-aligned, so sampled points can miss a far-edge chunk row/column that still owns
        // authored blocks or stale ItemEntity sections.
        int minCx = origin.getX() >> 4;
        int maxCx = (origin.getX() + LAST_TABLE_SIZE_X - 1) >> 4;
        int minCz = origin.getZ() >> 4;
        int maxCz = (origin.getZ() + LAST_TABLE_SIZE_Z - 1) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++)
            for (int cz = minCz; cz <= maxCz; cz++)
                level.getChunk(cx, cz);
    }

    private static void clearLegacyCenterVolume(ServerLevel level, BlockPos center) {
        final int cleanupRadius = 34;
        for (int x = -cleanupRadius; x <= cleanupRadius; x += 12)
            for (int z = -cleanupRadius; z <= cleanupRadius; z += 12)
                level.getChunkAt(center.offset(x, 0, z));
        for (int x = -cleanupRadius; x <= cleanupRadius; x++) {
            for (int z = -cleanupRadius; z <= cleanupRadius; z++) {
                for (int y = -6; y <= CLEAR_HEIGHT + 16; y++) {
                    BlockPos q = center.offset(x, y, z);
                    if (!level.getBlockState(q).isAir()) level.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void prepareProceduralArena(ServerLevel level, BlockPos center, Kind kind) {
        // Temporary Curator/Notary arenas. These remain deterministic and flat until their authored
        // structures are supplied, while Gambler now uses the actual Last Table build.
        final int cleanupRadius = 34; // also erases the previous 62x62 Last Table footprint
        for (int x = -cleanupRadius; x <= cleanupRadius; x += 12)
            for (int z = -cleanupRadius; z <= cleanupRadius; z += 12)
                level.getChunkAt(center.offset(x, 0, z));

        for (int x = -cleanupRadius; x <= cleanupRadius; x++) {
            for (int z = -cleanupRadius; z <= cleanupRadius; z++) {
                for (int y = -6; y <= CLEAR_HEIGHT + 16; y++) {
                    BlockPos q = center.offset(x, y, z);
                    if (!level.getBlockState(q).isAir()) level.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        int floorY = -1;
        for (int x = -FLOOR_RADIUS; x <= FLOOR_RADIUS; x++) for (int z = -FLOOR_RADIUS; z <= FLOOR_RADIUS; z++) {
            BlockPos q = center.offset(x, floorY, z);
            level.setBlock(q, floorState(kind, x, z), 2);
        }

        // The outer architecture is deliberately outside every boss's authoritative attack floor.
        for (int i = -24; i <= 24; i += 8) {
            pillar(level, center.offset(-25, 0, i), kind);
            pillar(level, center.offset(25, 0, i), kind);
            pillar(level, center.offset(i, 0, -25), kind);
            pillar(level, center.offset(i, 0, 25), kind);
        }
    }

    private static BlockState floorState(Kind kind, int x, int z) {
        int edge = Math.max(Math.abs(x), Math.abs(z));
        boolean axis = x == 0 || z == 0;
        boolean light = Math.floorMod(x, 12) == 0 && Math.floorMod(z, 12) == 0;
        if (light) return Blocks.SEA_LANTERN.defaultBlockState();
        return switch (kind) {
            case GAMBLER -> edge >= 24 ? Blocks.GILDED_BLACKSTONE.defaultBlockState()
                    : axis && (Math.abs(x) + Math.abs(z)) % 4 == 0 ? Blocks.GOLD_BLOCK.defaultBlockState()
                    : ((x + z) & 1) == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
            case CURATOR -> edge >= 24 ? Blocks.CHISELED_DEEPSLATE.defaultBlockState()
                    : axis ? Blocks.POLISHED_BASALT.defaultBlockState()
                    : ((x ^ z) & 3) == 0 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
            case NOTARY -> edge >= 24 ? Blocks.PURPUR_BLOCK.defaultBlockState()
                    : axis ? Blocks.SMOOTH_QUARTZ.defaultBlockState()
                    : ((x + z) & 3) == 0 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            case MAESTRO -> edge >= 24 ? Blocks.OBSIDIAN.defaultBlockState()
                    : axis ? Blocks.AMETHYST_BLOCK.defaultBlockState()
                    : ((x + z) & 3) == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
        };
    }

    private static void pillar(ServerLevel level, BlockPos base, Kind kind) {
        BlockState body = switch (kind) {
            case GAMBLER -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            case CURATOR -> Blocks.CHISELED_DEEPSLATE.defaultBlockState();
            case NOTARY -> Blocks.PURPUR_PILLAR.defaultBlockState();
            case MAESTRO -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        };
        BlockState cap = switch (kind) {
            case GAMBLER -> Blocks.GILDED_BLACKSTONE.defaultBlockState();
            case CURATOR -> Blocks.SOUL_LANTERN.defaultBlockState();
            case NOTARY -> Blocks.SEA_LANTERN.defaultBlockState();
            case MAESTRO -> Blocks.AMETHYST_BLOCK.defaultBlockState();
        };
        for (int y = 0; y < 5; y++) level.setBlock(base.above(y), body, 2);
        level.setBlock(base.above(5), cap, 2);
    }
}
