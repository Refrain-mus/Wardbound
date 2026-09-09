package dev.marrowseal.wardbound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Shared dimension-aware position storage and safe teleport helper for cards.
 * Card state used to store only XYZ, which could reinterpret an old coordinate
 * in a different dimension after portal travel. This helper keeps the dimension
 * alongside the position and always resolves a collision-safe destination.
 */
public final class CardTeleportUtil {
    private CardTeleportUtil() {}

    public record Stored(ResourceKey<Level> dimension, Vec3 position) {}

    public static void store(LockData data, UUID player, String key, ServerPlayer source) {
        if (data == null || player == null || key == null || source == null) return;
        data.setUniqueLong(player, key + "_pos", source.blockPosition().asLong());
        data.setUniqueString(player, key + "_dim", source.level().dimension().location().toString());
    }

    /** Reads legacy XYZ-only saves as belonging to the player's current dimension. */
    public static Stored load(LockData data, UUID player, String key, ServerPlayer context) {
        if (data == null || player == null || key == null || context == null) return null;
        long packed = data.uniqueLong(player, key + "_pos");
        if (packed == Long.MIN_VALUE) return null;
        String raw = data.uniqueString(player, key + "_dim");
        ResourceKey<Level> dimension = context.level().dimension();
        if (!raw.isBlank()) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            // A malformed stored dimension is corrupted state, not a legacy XYZ-only save.
            // Fail closed instead of reinterpreting those coordinates in the player's current world.
            if (id == null) return null;
            dimension = ResourceKey.create(Registries.DIMENSION, id);
        }
        BlockPos pos = BlockPos.of(packed);
        return new Stored(dimension, new Vec3(pos.getX() + .5, pos.getY(), pos.getZ() + .5));
    }

    public static void clear(LockData data, UUID player, String key) {
        if (data == null || player == null || key == null) return;
        data.setUniqueLong(player, key + "_pos", Long.MIN_VALUE);
        data.setUniqueString(player, key + "_dim", "");
    }

    public static boolean teleportStored(ServerPlayer player, LockData data, UUID id, String key) {
        Stored stored = load(data, id, key, player);
        if (stored == null || player.getServer() == null) return false;
        ServerLevel target = player.getServer().getLevel(stored.dimension());
        if (target == null) return false;
        return teleportSafe(player, target, stored.position());
    }

    public static boolean teleportSafe(ServerPlayer player, ServerLevel target, Vec3 preferred) {
        if (player == null || target == null || preferred == null) return false;
        Vec3 safe = findSafe(target, player, preferred);
        if (safe == null) return false;
        player.teleportTo(target, safe.x, safe.y, safe.z, player.getYRot(), player.getXRot());
        player.fallDistance = 0;
        return true;
    }

    public static Vec3 findSafe(ServerLevel level, ServerPlayer player, Vec3 preferred) {
        if (level == null || player == null) return null;
        Vec3 fallback = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 1, 0);
        if (!finite(preferred)) preferred = fallback;
        if (safeAt(level, player, preferred)) return preferred;

        BlockPos base = BlockPos.containing(preferred);
        for (int r = 1; r <= 6; r++) {
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                if (Math.abs(x) != r && Math.abs(z) != r) continue;
                for (int y = -2; y <= 4; y++) {
                    Vec3 candidate = Vec3.atBottomCenterOf(base.offset(x, y, z));
                    if (safeAt(level, player, candidate)) return candidate;
                }
            }
        }
        return safeAt(level, player, fallback) ? fallback : null;
    }

    public static boolean safeAt(ServerLevel level, ServerPlayer player, Vec3 pos) {
        if (level == null || player == null || !finite(pos)) return false;
        BlockPos feet = BlockPos.containing(pos), head = feet.above(), floor = feet.below();
        if (!level.isInWorldBounds(head) || !level.getWorldBorder().isWithinBounds(feet)) return false;
        level.getChunkAt(floor);
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(head).isEmpty() || !level.getFluidState(floor).isEmpty()) return false;
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) return false;
        if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty()) return false;
        AABB moved = player.getBoundingBox().move(pos.subtract(player.position()));
        return level.noCollision(player, moved);
    }

    private static boolean finite(Vec3 pos) {
        return pos != null && Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z);
    }
}
