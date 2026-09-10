package dev.marrowseal.wardbound.ancientsmith;

import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.WardHistory;
import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.AshenCuratorEntity;
import dev.marrowseal.wardbound.boss.MourningNotaryEntity;
import dev.marrowseal.wardbound.boss.MasterArenaManager;
import dev.marrowseal.wardbound.boss.PaleGamblerEntity;
import dev.marrowseal.wardbound.champion.DefeatedChampionEntity;
import dev.marrowseal.wardbound.champion.SilasProgression;
import dev.marrowseal.wardbound.nhalsul.NhalSulModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Wardbound's only survival acquisition route for Nhal-Sûl. */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class AncientSmithProgression {
    public static final ResourceLocation REALM_ID = new ResourceLocation(Wardbound.MODID, "ancient_forge");
    public static final ResourceKey<Level> REALM = ResourceKey.create(Registries.DIMENSION, REALM_ID);
    private static final ResourceLocation PLATFORM = new ResourceLocation(Wardbound.MODID, "ancient_smith/forge_platform");
    private static final String COMPLETE = "ancient_smith_nhal_received";
    private static final String PENDING = "ancient_smith_pending_since";
    private static final String RETURN_ROOT = "WardboundAncientSmithReturn";
    private static final String RETURN_DIM = "dim", RETURN_X = "x", RETURN_Y = "y", RETURN_Z = "z", RETURN_YAW = "yaw", RETURN_PITCH = "pitch";
    private static final int SAFE_DELAY = 20 * 10;

    private AncientSmithProgression() {}

    public static boolean eligible(ServerPlayer player) {
        if (player == null || player.getServer() == null) return false;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        return SilasProgression.defeatedMasters(data, id) >= 2
                && data.totalBeaten(id) >= WardConfig.eldritchAfterBeaten
                && data.uniqueInt(id, COMPLETE) == 0;
    }

    public static boolean complete(ServerPlayer player) {
        return player != null && player.getServer() != null
                && LockData.get(player.getServer()).uniqueInt(player.getUUID(), COMPLETE) != 0;
    }

    public static boolean active(ServerPlayer player) {
        return player != null && player.getServer() != null
                && SmithSceneData.get(player.getServer()).sessions.containsKey(player.getUUID());
    }

    public static void debugReset(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        if (active(player)) AncientSmithHooks.cancel(player);
        LockData data = LockData.get(player.getServer());
        data.setUniqueInt(player.getUUID(), COMPLETE, 0);
        data.setUniqueLong(player.getUUID(), PENDING, Long.MIN_VALUE);
        data.setUniqueInt(player.getUUID(), "ancient_smith_scene_seen", 0);
    }

    public static String status(ServerPlayer player) {
        if (player == null || player.getServer() == null) return "Ancient Smith // unavailable";
        LockData data = LockData.get(player.getServer());
        int defeated = SilasProgression.defeatedMasters(data, player.getUUID());
        long pending = data.uniqueLong(player.getUUID(), PENDING);
        int wards = data.totalBeaten(player.getUUID());
        return "Ancient Smith // Master defeats toward unlock " + Math.min(defeated, 2) + "/2 (" + defeated + "/3 tracked defeated) // wards " + wards + "/" + WardConfig.eldritchAfterBeaten
                + " // pending " + (pending == Long.MIN_VALUE ? "no" : "yes")
                + " // scene " + (active(player) ? "active" : "idle") + " // Nhal-Sûl delivered " + complete(player);
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0 || player.getServer() == null) return;
        if (player.level().dimension().equals(REALM) && !active(player)) {
            recoverStranded(player);
            return;
        }
        if (player.level().dimension().equals(REALM) || player.level().dimension().equals(MasterArenaManager.REALM) || active(player) || complete(player)) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        if (SilasProgression.defeatedMasters(data, id) < 2 || data.totalBeaten(id) < WardConfig.eldritchAfterBeaten) {
            if (data.uniqueLong(id, PENDING) != Long.MIN_VALUE) data.setUniqueLong(id, PENDING, Long.MIN_VALUE);
            return;
        }
        if (carriesNhalSul(player) || !basicSafety(player)) {
            data.setUniqueLong(id, PENDING, Long.MIN_VALUE);
            return;
        }
        long now = player.getServer().overworld().getGameTime();
        long since = data.uniqueLong(id, PENDING);
        if (since == Long.MIN_VALUE) {
            data.setUniqueLong(id, PENDING, now);
            WardHud.send(player, "AN OLD FORGE HAS NOTICED THE SECOND FALLEN MASTER", WardHud.Mood.GLITCH);
            return;
        }
        if (now - since < SAFE_DELAY || !encounterSafe(player)) return;
        if (start(player, false)) data.setUniqueLong(id, PENDING, Long.MIN_VALUE);
    }



    @SubscribeEvent
    public static void protectPlayer(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.level().dimension().equals(REALM) && active(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void protectToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !player.level().dimension().equals(REALM) || !active(player)) return;
        ItemStack remainder = event.getEntity().getItem().copy();
        if (remainder.isEmpty()) { event.setCanceled(true); return; }
        // Forge removes the tossed stack before this event. Inventory#add may insert only part of a
        // stack, so never leave the original full ItemEntity alive after a partial restoration.
        player.getInventory().add(remainder);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        if (remainder.isEmpty()) event.setCanceled(true);
        else event.getEntity().setItem(remainder.copy());
    }

    @SubscribeEvent
    public static void protectBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && player.level().dimension().equals(REALM)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void protectPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().dimension().equals(REALM)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void protectFluid(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(REALM)) event.setCanceled(true);
    }

    private static boolean basicSafety(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && !player.isPassenger() && !player.isSleeping()
                && player.onGround() && !player.isInWaterOrBubble() && player.containerMenu == player.inventoryMenu;
    }

    private static boolean encounterSafe(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        UUID id = player.getUUID();
        AABB area = player.getBoundingBox().inflate(32, 16, 32);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, Mob::isAlive)) if (mob.getTarget() == player) return false;
        if (!level.getEntitiesOfClass(PaleGamblerEntity.class, area.inflate(96), e -> !e.isRemoved() && e.includes(id)).isEmpty()) return false;
        if (!level.getEntitiesOfClass(AshenCuratorEntity.class, area.inflate(96), e -> !e.isRemoved() && e.includes(id)).isEmpty()) return false;
        if (!level.getEntitiesOfClass(MourningNotaryEntity.class, area.inflate(96), e -> !e.isRemoved() && e.includes(id)).isEmpty()) return false;
        return level.getEntitiesOfClass(DefeatedChampionEntity.class, area.inflate(96), e -> e.companionOwnerIs(id)).isEmpty();
    }

    public static boolean start(ServerPlayer player, boolean debug) {
        if (player == null || player.getServer() == null || !basicSafety(player) && !debug) return false;
        // A forge scene may only be entered from an ordinary world. Starting a second acquisition
        // from a stale/manual teleport inside the forge would create a return anchor that points
        // back into the private infrastructure dimension.
        if (player.level().dimension().equals(REALM)) return false;
        if (MasterArenaManager.active(player) || player.level().dimension().equals(MasterArenaManager.REALM)) return false;
        if (active(player) || (!debug && (!eligible(player) || carriesNhalSul(player)))) return false;
        ServerLevel realm = player.getServer().getLevel(REALM);
        if (realm == null) {
            Wardbound.LOG.error("Ancient Forge dimension {} is not loaded; Nhal-Sûl scene cannot start.", REALM_ID);
            if (debug) WardHud.send(player, "ANCIENT FORGE // DIMENSION MISSING", WardHud.Mood.DANGER);
            return false;
        }

        ResourceKey<Level> returnDimension = player.level().dimension();
        Vec3 returnPosition = player.position();
        float returnYaw = player.getYRot(), returnPitch = player.getXRot();
        BlockPos origin = cellOrigin(player.getUUID());
        realm.getChunkAt(origin.offset(20, 0, 20));
        if (cellOccupiedByOther(player, origin)) {
            WardHud.send(player, "ANCIENT FORGE // THIS PRIVATE CELL IS ALREADY IN USE", WardHud.Mood.WARD);
            return false;
        }
        if (!prepareCell(realm, origin)) return false;

        cleanupCell(realm, origin);
        AncientSmithEntity smith = AncientSmithModule.SMITH.get().create(realm);
        AncientAnvilEntity anvil = AncientSmithModule.ANVIL.get().create(realm);
        if (smith == null || anvil == null) return false;
        smith.moveTo(origin.getX() + 20.5, origin.getY() + 3.0, origin.getZ() + 25.5, 180f, 0f);
        anvil.moveTo(origin.getX() + 20.5, origin.getY() + 3.0, origin.getZ() + 20.375, 180f, 0f);
        if (!realm.addFreshEntity(smith) || !realm.addFreshEntity(anvil)) {
            smith.discard();
            anvil.discard();
            return false;
        }

        rememberReturnAnchor(player, returnDimension, returnPosition, returnYaw, returnPitch);
        player.teleportTo(realm, origin.getX() + 20.5, origin.getY() + 3.05, origin.getZ() + 12.5, 0f, 0f);
        if (!player.level().dimension().equals(REALM)) {
            cleanupCell(realm, origin);
            clearReturnAnchor(player);
            Wardbound.LOG.warn("Ancient Forge teleport was vetoed for {}.", player.getGameProfile().getName());
            return false;
        }
        ItemStack reward = new ItemStack(NhalSulModule.NHAL_SUL.get());
        AncientSmithHooks.Result result = AncientSmithHooks.beginNhalSulAcquisition(player, reward);
        if (result != AncientSmithHooks.Result.STARTED) {
            cleanupCell(realm, origin);
            ServerLevel back = player.getServer().getLevel(returnDimension);
            if (back == null) back = player.getServer().overworld();
            player.teleportTo(back, returnPosition.x, returnPosition.y, returnPosition.z, returnYaw, returnPitch);
            clearReturnAnchor(player);
            Wardbound.LOG.warn("Ancient Smith acquisition failed to start for {}: {}", player.getGameProfile().getName(), result);
            return false;
        }
        AncientSmithHooks.setReturnAnchor(player, returnDimension, returnPosition, returnYaw, returnPitch);
        LockData data = LockData.get(player.getServer());
        data.setUniqueInt(player.getUUID(), "ancient_smith_scene_seen", 1);
        WardHistory.recordSpecial(player, "GLITCH", "ANCIENT FORGE // After the second Master fell, an older craft pulled the witness outside the ordinary road.");
        WardHud.send(player, "ANCIENT FORGE // THE HAMMER WAS ALREADY FALLING", WardHud.Mood.GLITCH);
        return true;
    }

    @SubscribeEvent
    public static void onReturn(AncientSmithReturnEvent event) {
        ServerPlayer player = event.player;
        if (player == null || player.getServer() == null) return;
        if (player.level() instanceof ServerLevel current && player.level().dimension().equals(REALM)) cleanupCell(current, cellOrigin(player.getUUID()));
        if (event.acquisition && event.completed) {
            LockData data = LockData.get(player.getServer());
            data.setUniqueInt(player.getUUID(), COMPLETE, 1);
            data.setUniqueLong(player.getUUID(), PENDING, Long.MIN_VALUE);
            WardHistory.recordSpecial(player, "WARD", "NHAL-SÛL // The Ancient Smith completed the blade and threw it across the forge without asking for payment.");
            WardHud.send(player, "NHAL-SÛL // USE IT WELL", WardHud.Mood.REWARD);
        }
        teleportBack(player, event);
        clearReturnAnchor(player);
    }

    private static void rememberReturnAnchor(ServerPlayer player, ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch) {
        CompoundTag t = new CompoundTag();
        t.putString(RETURN_DIM, dimension.location().toString());
        t.putDouble(RETURN_X, position.x);t.putDouble(RETURN_Y, position.y);t.putDouble(RETURN_Z, position.z);
        t.putFloat(RETURN_YAW, yaw);t.putFloat(RETURN_PITCH, pitch);
        player.getPersistentData().put(RETURN_ROOT, t);
    }

    private static void clearReturnAnchor(ServerPlayer player) {
        if (player != null) player.getPersistentData().remove(RETURN_ROOT);
    }

    private static void recoverStranded(ServerPlayer player) {
        CompoundTag t = player.getPersistentData().getCompound(RETURN_ROOT);
        boolean anchored = t.contains(RETURN_DIM) && t.contains(RETURN_X) && t.contains(RETURN_Y) && t.contains(RETURN_Z);
        ResourceLocation id = anchored ? ResourceLocation.tryParse(t.getString(RETURN_DIM)) : null;
        ServerLevel target = id == null ? null : player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (target == null || target.dimension().equals(REALM)) target = player.getServer().overworld();
        Vec3 fallback = Vec3.atBottomCenterOf(target.getSharedSpawnPos()).add(0, 1, 0);
        Vec3 preferred = anchored ? new Vec3(t.getDouble(RETURN_X), t.getDouble(RETURN_Y), t.getDouble(RETURN_Z)) : fallback;
        if (!Double.isFinite(preferred.x) || !Double.isFinite(preferred.y) || !Double.isFinite(preferred.z)) preferred = fallback;
        Vec3 destination = safeReturn(target, player, preferred, fallback);
        float yaw = anchored && Float.isFinite(t.getFloat(RETURN_YAW)) ? t.getFloat(RETURN_YAW) : player.getYRot();
        float pitch = anchored && Float.isFinite(t.getFloat(RETURN_PITCH)) ? t.getFloat(RETURN_PITCH) : player.getXRot();
        player.teleportTo(target, destination.x, destination.y, destination.z, yaw, pitch);
        player.fallDistance = 0;
        clearReturnAnchor(player);
        WardHud.send(player, "ANCIENT FORGE // AN INTERRUPTED ROAD HAS BEEN CLOSED SAFELY", WardHud.Mood.WARD);
    }

    private static boolean cellOccupiedByOther(ServerPlayer player, BlockPos origin) {
        for (SmithSceneData.Session session : SmithSceneData.get(player.getServer()).sessions.values()) {
            if (session.owner != null && !session.owner.equals(player.getUUID()) && cellOrigin(session.owner).equals(origin)) return true;
        }
        Vec3 center = new Vec3(origin.getX() + 20.5, origin.getY() + 8, origin.getZ() + 20.5);
        for (ServerPlayer other : player.getServer().getLevel(REALM).players()) {
            if (!other.getUUID().equals(player.getUUID()) && other.distanceToSqr(center) < 72 * 72) return true;
        }
        return false;
    }

    private static void teleportBack(ServerPlayer player, AncientSmithReturnEvent event) {
        ResourceLocation id = ResourceLocation.tryParse(event.entryDimension);
        ServerLevel target = id == null ? null : player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (target == null) target = player.getServer().overworld();
        Vec3 fallback = Vec3.atBottomCenterOf(target.getSharedSpawnPos()).add(0, 1, 0);
        Vec3 destination = safeReturn(target, player, event.entryPosition, fallback);
        player.teleportTo(target, destination.x, destination.y, destination.z, event.entryYaw, event.entryPitch);
    }

    private static Vec3 safeReturn(ServerLevel level, ServerPlayer player, Vec3 preferred, Vec3 fallback) {
        Vec3 found = findSafeNear(level, player, preferred, 4, -2, 4);
        if (found != null) return found;
        found = findSafeNear(level, player, fallback, 8, -3, 5);
        if (found != null) return found;
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level.getSharedSpawnPos()).above();
        Vec3 spawnTop = Vec3.atBottomCenterOf(top);
        found = findSafeNear(level, player, spawnTop, 10, -2, 4);
        return found != null ? found : fallback;
    }

    private static Vec3 findSafeNear(ServerLevel level, ServerPlayer player, Vec3 center, int radius, int minY, int maxY) {
        if (center == null || !Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) return null;
        if (safeAt(level, player, center)) return center;
        BlockPos base = BlockPos.containing(center);
        for (int r = 0; r <= radius; r++) {
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                if (r > 0 && Math.abs(x) != r && Math.abs(z) != r) continue;
                for (int y = minY; y <= maxY; y++) {
                    BlockPos q = base.offset(x, y, z);
                    if (!level.isInWorldBounds(q) || !level.getWorldBorder().isWithinBounds(q)) continue;
                    if (!level.hasChunkAt(q)) level.getChunkAt(q);
                    Vec3 candidate = Vec3.atBottomCenterOf(q);
                    if (safeAt(level, player, candidate)) return candidate;
                }
            }
        }
        return null;
    }

    private static boolean safeAt(ServerLevel level, ServerPlayer player, Vec3 pos) {
        if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) return false;
        BlockPos feet = BlockPos.containing(pos);
        BlockPos head = feet.above();
        BlockPos ground = feet.below();
        if (!level.isInWorldBounds(head) || !level.getWorldBorder().isWithinBounds(feet) || !level.hasChunkAt(ground)) return false;
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(head).isEmpty()) return false;
        var floor = level.getBlockState(ground);
        if (!floor.isFaceSturdy(level, ground, Direction.UP) || !level.getFluidState(ground).isEmpty()) return false;
        if (floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS) || floor.is(Blocks.CAMPFIRE)
                || floor.is(Blocks.SOUL_CAMPFIRE) || floor.is(Blocks.FIRE) || floor.is(Blocks.SOUL_FIRE)
                || floor.is(Blocks.POWDER_SNOW) || floor.is(Blocks.SWEET_BERRY_BUSH)) return false;
        AABB moved = player.getBoundingBox().move(pos.subtract(player.position()));
        return level.noCollision(player, moved);
    }

    private static boolean carriesNhalSul(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(NhalSulModule.NHAL_SUL.get())) return true;
        }
        return false;
    }

    private static BlockPos cellOrigin(UUID id) {
        long hash = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17);
        int gx = (int) (hash & 0xffffL) - 32768;
        int gz = (int) ((hash >>> 16) & 0xffffL) - 32768;
        return new BlockPos(gx * 256, 64, gz * 256);
    }

    private static boolean prepareCell(ServerLevel level, BlockPos origin) {
        // Load every chunk touched by the 41x41 template before placement.
        for (int x = 0; x <= 40; x += 16) for (int z = 0; z <= 40; z += 16) level.getChunkAt(origin.offset(x, 0, z));
        var template = level.getStructureManager().get(PLATFORM).orElse(null);
        if (template == null) {
            Wardbound.LOG.error("Missing Ancient Smith structure template {}", PLATFORM);
            return false;
        }
        return template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.random, 2);
    }

    private static void cleanupCell(ServerLevel level, BlockPos origin) {
        Vec3 center = new Vec3(origin.getX() + 20.5, origin.getY() + 8, origin.getZ() + 20.5);
        AABB area = new AABB(center, center).inflate(36, 20, 36);
        for (AncientSmithEntity entity : level.getEntitiesOfClass(AncientSmithEntity.class, area)) entity.discard();
        for (AncientAnvilEntity entity : level.getEntitiesOfClass(AncientAnvilEntity.class, area)) entity.discard();
        // The forge cell is cinematic-only. No dropped item is allowed to survive between sessions.
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) entity.discard();
    }
}
