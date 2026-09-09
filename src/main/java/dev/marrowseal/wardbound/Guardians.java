package dev.marrowseal.wardbound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What comes out of a container when you lose its lock.
 *
 * <p>Losing used to be an accounting event: a smaller number, a red line of
 * chat, nothing to react to. A ward that has just beaten you should do something
 * about it, and the container is the obvious thing for it to come out of.
 *
 * <p>What spawns depends on where you are, and is drawn from a pool rather than
 * being one fixed mob, so a failed chest is not the same scene twice. Each
 * dimension's pool and its blacklist live in the config, and unknown dimensions
 * from other mods fall back to the overworld pool. Anything a pack author does
 * not want appearing goes in the blacklist by id and is never picked, even if a
 * pool entry names it.
 */
public final class Guardians {

    /**
     * Defaults, chosen for being recognisable and survivable rather than for
     * being the nastiest thing available. A failed chest should cost you a fight,
     * not the run.
     */
    private static final String[] OVERWORLD = {
            "minecraft:zombie", "minecraft:skeleton", "minecraft:spider",
            "minecraft:husk", "minecraft:stray", "minecraft:witch",
            "minecraft:pillager", "minecraft:vindicator", "minecraft:cave_spider",
    };
    private static final String[] NETHER = {
            "minecraft:blaze", "minecraft:wither_skeleton", "minecraft:magma_cube",
            "minecraft:hoglin", "minecraft:piglin_brute", "minecraft:zombified_piglin",
    };
    private static final String[] END = {
            "minecraft:enderman", "minecraft:shulker", "minecraft:endermite",
    };

    private static final Random RNG = new Random();

    /**
     * Progression gates are datapack tags rather than hard-coded instanceof checks.
     * Modpacks can therefore add their own Nether/End creatures without recompiling Wardbound.
     */
    private static final TagKey<EntityType<?>> REQUIRES_NETHER = TagKey.create(
            Registries.ENTITY_TYPE, new ResourceLocation(Wardbound.MODID, "guardian_requires_nether"));
    private static final TagKey<EntityType<?>> REQUIRES_END = TagKey.create(
            Registries.ENTITY_TYPE, new ResourceLocation(Wardbound.MODID, "guardian_requires_end"));

    private static final String VISITED_NETHER = "guardian_visited_nether";
    private static final String VISITED_END = "guardian_visited_end";

    private Guardians() {
    }

    /**
     * Everything hostile the game knows about, minus the things that would ruin
     * the moment.
     *
     * <p>The curated lists below are the safe default, but a fixed set of nine
     * mobs stops surprising you after the third failed chest. Drawing from the
     * whole registry means a modded pack's monsters turn up in modded chests
     * without anybody writing a config, and it means the answer to "what came
     * out of it" is occasionally something you have not seen come out of a
     * chest before.
     *
     * <p>Filtered on category rather than by name so it stays correct for mobs
     * that did not exist when this was written. Bosses are excluded by hand:
     * a wither out of a village chest is not a scare, it is the end of the save.
     */
    private static final java.util.Set<String> NEVER = java.util.Set.of(
            "minecraft:wither", "minecraft:ender_dragon", "minecraft:warden",
            "minecraft:elder_guardian", "minecraft:giant");

    private static List<EntityType<?>> wildPool(ServerPlayer player, String dimId, int threatTier) {
        List<EntityType<?>> out = new ArrayList<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            if (type.getCategory() != MobCategory.MONSTER) continue;
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (id == null || NEVER.contains(id.toString())) continue;
            if (!eligibleFor(player, type, dimId)) continue;
            if (guardianRisk(type) > threatRiskCap(threatTier)) continue;
            out.add(type);
        }
        return out;
    }

    /**
     * Remember progression independently of the current guardian roll.  This is
     * deliberately player-scoped: one player reaching the End should not unlock
     * End guardians for everybody else on a server.
     */
    public static void recordDimensionVisit(ServerPlayer player) {
        if (player == null) return;
        String dim = player.level().dimension().location().toString();
        LockData data = LockData.get(player.getServer());
        if ("minecraft:the_nether".equals(dim)) data.setUniqueInt(player.getUUID(), VISITED_NETHER, 1);
        if ("minecraft:the_end".equals(dim)) data.setUniqueInt(player.getUUID(), VISITED_END, 1);
    }

    private static boolean hasVisited(ServerPlayer player, String requiredDimension, String currentDimension) {
        // Standing in a dimension is itself proof of having reached it. This also
        // makes the system safe for worlds upgraded while the player is already there.
        if (requiredDimension.equals(currentDimension)) return true;
        if ("minecraft:overworld".equals(requiredDimension)) return true;
        if (player == null) return false;
        LockData data = LockData.get(player.getServer());
        if ("minecraft:the_nether".equals(requiredDimension))
            return data.uniqueInt(player.getUUID(), VISITED_NETHER) > 0;
        if ("minecraft:the_end".equals(requiredDimension))
            return data.uniqueInt(player.getUUID(), VISITED_END) > 0;
        return true;
    }

    private static String requiredDimension(EntityType<?> type) {
        if (type.is(REQUIRES_END)) return "minecraft:the_end";
        if (type.is(REQUIRES_NETHER)) return "minecraft:the_nether";
        return null;
    }

    /** Applies to curated pools, config pools and wild rolls alike. */
    private static boolean eligibleFor(ServerPlayer player, EntityType<?> type, String dimId) {
        String required = requiredDimension(type);
        if (required == null) return true;
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(type);
        boolean visitedNether = player != null && hasVisited(player, "minecraft:the_nether", dimId);
        boolean visitedEnd = player != null && hasVisited(player, "minecraft:the_end", dimId);
        String cacheKey = (typeId == null ? "unknown" : typeId.toString()) + "|" + dimId
                + "|dl=" + WardConfig.guardianDimensionLock + "|pl=" + WardConfig.guardianProgressionLock
                + "|n=" + visitedNether + "|e=" + visitedEnd;
        Boolean cached = WardPerformance.guardianEligibility(cacheKey);
        if (cached != null) return cached;

        boolean eligible;
        // Strong/default rule: native progression mobs stay in their own dimension.
        if (WardConfig.guardianDimensionLock && !required.equals(dimId)) eligible = false;
        // Optional looser mode: after visiting the source dimension, a pack may
        // allow its creatures elsewhere by disabling guardian_dimension_lock.
        else eligible = !WardConfig.guardianProgressionLock || hasVisited(player, required, dimId);
        WardPerformance.putGuardianEligibility(cacheKey, eligible);
        return eligible;
    }

    /** The stock pool for a dimension, used when the config does not name one. */
    public static List<String> defaultPool(String dimId) {
        String[] src = switch (dimId) {
            case "minecraft:the_nether" -> NETHER;
            case "minecraft:the_end" -> END;
            default -> OVERWORLD;
        };
        return new ArrayList<>(List.of(src));
    }

    /**
     * Spawns the container's answer to being beaten.
     *
     * @return how many actually appeared, which can be zero if the pool is empty
     * or every entry in it is blacklisted or unknown to this install
     */
    public static int spawn(ServerLevel level, BlockPos pos, String dimId, int chestValue) {
        return spawn(level, pos, dimId, chestValue, null, false, 1);
    }

    public static int spawn(ServerLevel level, BlockPos pos, String dimId, int chestValue,
                            ServerPlayer player) {
        return spawn(level, pos, dimId, chestValue, player, false, 1);
    }

    /**
     * @param horde many more of them, all of one kind. Used by the endgame; a
     *              crowd of one mob reads as something sent rather than as bad
     *              luck, which is exactly the difference being aimed at.
     */
    public static int spawn(ServerLevel level, BlockPos pos, String dimId, int chestValue,
                            boolean horde) {
        return spawn(level, pos, dimId, chestValue, null, horde, 1);
    }

    public static int spawn(ServerLevel level, BlockPos pos, String dimId, int chestValue,
                            ServerPlayer player, boolean horde) {
        return spawn(level, pos, dimId, chestValue, player, horde, 1);
    }

    /**
     * Threat-aware guardian selection. threatTier is 0..4 (Quiet..Unnameable).
     * It can narrow or bias the already-legal pool, but never bypasses
     * dimension/progression eligibility.
     */
    public static int spawn(ServerLevel level, BlockPos pos, String dimId, int chestValue,
                            ServerPlayer player, boolean horde, int threatTier) {
        if (player != null) recordDimensionVisit(player);
        threatTier = Math.max(0, Math.min(4, threatTier));
        List<EntityType<?>> pool = resolvePool(player, dimId, threatTier);
        if (pool.isEmpty()) return 0;

        // A richer container was better guarded. Kept low: this is a scene, not
        // a raid, and the player has just lost a lock and is probably annoyed.
        int count = 1 + Math.round((chestValue / 100f) * (WardConfig.guardianMaxCount - 1));
        count = Math.max(1, Math.min(WardConfig.guardianMaxCount, count));
        EntityType<?> only = null;
        if (horde) {
            count = WardConfig.hordeSize;
            only = pickForThreat(pool, threatTier);
        }

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            EntityType<?> type = only != null ? only : pickForThreat(pool, threatTier);
            BlockPos at = scatter(level, pos);
            Entity e = type.spawn(level, at, MobSpawnType.TRIGGERED);
            if (e != null) {
                e.getPersistentData().putBoolean("WardboundGuardian", true);
                e.getPersistentData().putInt("WardboundGuardianValue", chestValue);
                spawned++;
                if (player != null) {
                    ResourceLocation spawnedId = ForgeRegistries.ENTITY_TYPES.getKey(type);
                    if (spawnedId != null) WardHistory.discoverGuardian(player, spawnedId.toString());
                }
            }
        }
        return spawned;
    }

    /**
     * Somewhere next to the container rather than on top of it.
     *
     * <p>Spawning everything on the same block stacks mobs inside each other and
     * frequently suffocates them into a wall, which turns the mod's one moment
     * of threat into a joke.
     */
    private static BlockPos scatter(ServerLevel level, BlockPos pos) {
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos candidate = pos.offset(
                    RNG.nextInt(5) - 2,
                    RNG.nextInt(3) - 1,
                    RNG.nextInt(5) - 2);
            if (level.getBlockState(candidate).isAir()
                    && level.getBlockState(candidate.above()).isAir()) {
                return candidate;
            }
        }
        return pos.above();
    }

    /** Turns the configured id strings into entity types, dropping anything unusable. */
    private static List<EntityType<?>> resolvePool(ServerPlayer player, String dimId, int threatTier) {
        List<String> banned = WardConfig.guardianBlacklist(dimId);

        // Roll off the curated list and into the wild one now and then, so a
        // failed chest is not drawing from the same nine mobs forever.
        if (WardConfig.guardianWildChance > 0f && RNG.nextFloat() < WardConfig.guardianWildChance) {
            List<EntityType<?>> wild = new ArrayList<>();
            for (EntityType<?> type : wildPool(player, dimId, threatTier)) {
                ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
                if (id != null && !banned.contains(id.toString())) wild.add(type);
            }
            if (!wild.isEmpty()) return wild;
        }

        List<String> ids = WardConfig.guardianPool(dimId);

        List<EntityType<?>> out = new ArrayList<>();
        for (String id : ids) {
            if (banned.contains(id)) continue;
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) {
                Wardbound.LOG.warn("[Wardbound] guardian pool for {} has an invalid entity id '{}', skipping it.",
                        dimId, id);
                continue;
            }
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
            // getValue falls back to the pig for anything unregistered, so an id
            // from a mod that is not installed would quietly spawn livestock.
            if (type == null || type == EntityType.PIG && !"minecraft:pig".equals(id)) {
                Wardbound.LOG.warn("[Wardbound] guardian pool for {} references unknown entity '{}' "
                        + "(mod not installed?), skipping it.", dimId, id);
                continue;
            }
            if (!eligibleFor(player, type, dimId)) continue;
            if (guardianRisk(type) > threatRiskCap(threatTier)) continue;
            out.add(type);
        }
        return out;
    }

    /** Coarse threat rank used only after dimension/progression legality is established. */
    private static int guardianRisk(EntityType<?> type) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (key == null) return 1;
        return switch (key.toString()) {
            case "minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:zombified_piglin", "minecraft:endermite" -> 1;
            case "minecraft:husk", "minecraft:stray", "minecraft:cave_spider", "minecraft:pillager", "minecraft:magma_cube", "minecraft:blaze" -> 2;
            case "minecraft:witch", "minecraft:vindicator", "minecraft:wither_skeleton", "minecraft:hoglin", "minecraft:enderman" -> 3;
            case "minecraft:piglin_brute", "minecraft:shulker" -> 4;
            default -> 1; // modded entries remain usable; pack authors still control their pool/tags.
        };
    }

    private static int threatRiskCap(int threatTier) {
        return switch (Math.max(0, Math.min(4, threatTier))) {
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 3;
            default -> 4;
        };
    }

    private static EntityType<?> pickForThreat(List<EntityType<?>> pool, int threatTier) {
        if (pool == null || pool.isEmpty()) return EntityType.ZOMBIE;
        if (threatTier >= 2 && RNG.nextFloat() < (0.35f + 0.12f * threatTier)) {
            int best = 0;
            for (EntityType<?> type : pool) best = Math.max(best, guardianRisk(type));
            List<EntityType<?>> upper = new ArrayList<>();
            for (EntityType<?> type : pool) if (guardianRisk(type) == best) upper.add(type);
            if (!upper.isEmpty()) return upper.get(RNG.nextInt(upper.size()));
        }
        return pool.get(RNG.nextInt(pool.size()));
    }

    /** Deterministic diagnostic list: every currently eligible hostile type for this player/dimension. */
    public static List<String> debugEligible(ServerPlayer player, String dimId) {
        List<String> banned = WardConfig.guardianBlacklist(dimId);
        java.util.TreeSet<String> ids = new java.util.TreeSet<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            if (type.getCategory() != MobCategory.MONSTER) continue;
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (id == null || NEVER.contains(id.toString()) || banned.contains(id.toString())) continue;
            if (eligibleFor(player, type, dimId)) ids.add(id.toString());
        }
        return new ArrayList<>(ids);
    }
}
