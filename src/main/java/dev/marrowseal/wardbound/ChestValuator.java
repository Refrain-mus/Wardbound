package dev.marrowseal.wardbound;

import com.mojang.brigadier.ParseResults;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import dev.marrowseal.wardbound.item.Charm;
import dev.marrowseal.wardbound.champion.SilasProgression;
import dev.marrowseal.wardbound.item.RelicItem;
import dev.marrowseal.wardbound.item.RelicDropItem;
import dev.marrowseal.wardbound.item.SealedCardItem;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;
import dev.marrowseal.wardbound.net.OpenBargainPacket;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server side brain of the mod. Scores loot tables, tags chests, decides whether
 * a chest is locked and opens the right minigame for the dimension the player is in.
 */
public final class ChestValuator {

    public static final String TAG_VALUE = "WardValue";
    public static final String TAG_SCANNED = "WardScanned";
    public static final String TAG_LOCKED = "WardLocked";
    public static final String TAG_MULT = "WardMultiplier";
    /** Who to tell about the result once the loot table actually rolls. */
    public static final String TAG_WHO = "WardWho";
    /**
     * Set on a container whose lock has just been beaten but which the player
     * has not taken yet. While this is present the loot is banked and the ward
     * is offering to be wound tighter. See {@link #resolveSuccess}.
     */
    public static final String TAG_TEMPT = "WardTempt";
    /** Explicit card IDs currently offered by this container. No bit-mask ceiling. */
    public static final String TAG_BARGAIN_OFFERS = "WardBargainOffers";
    /** How many times this container has already been re-sealed. */
    public static final String TAG_DEPTH = "WardDepth";
    /** Which lock this container ran last, so a re-seal never repeats it. */
    public static final String TAG_LAST_GAME = "WardLastGame";
    /** Game time before which a container beaten out of the player will not open. */
    public static final String TAG_SEALED_UNTIL = "WardSealedUntil";
    /** How many times this exact container has turned this player away. */
    public static final String TAG_SPITE = "WardSpite";
    /** Persistent visual history; never cleared when the chest finally opens. */
    public static final String TAG_SCAR_BREAKS = "WardScarBreaks";
    public static final String TAG_SCAR_BURNED = "WardScarBurned";
    public static final String TAG_SCAR_GRUDGE = "WardScarGrudge";
    public static final String TAG_SCAR_RESEALED = "WardScarResealed";
    /** Seed of the signed ward currently offering bargain cards. */
    public static final String TAG_BARGAIN_SEED = "WardBargainSeed";
    /** 0 normal, 1 solo epic, 2 forced curse deck, 3 solo unique law, 4 forced death hand. */
    public static final String TAG_BARGAIN_MODE = "WardBargainMode";
    /** Number of refreshes already spent on this chest; intentionally capped at one. */
    public static final String TAG_BARGAIN_REFRESHED = "WardBargainRefreshed";
    /** Progression loot ceiling snapshotted when this physical ward resolved. */
    public static final String TAG_BARGAIN_CAP = "WardBargainCap";
    private static final String LOOSE_OFFERS = "WardboundLooseCardOffers";
    private static final String LOOSE_MODE = "WardboundLooseCardMode";
    private static final String LOOSE_SEED = "WardboundLooseCardSeed";
    private static final String LOOSE_POS = "WardboundLooseCardPos";
    private static final String LOOSE_DEALER = "WardboundLooseCardDealer";
    /** The one player currently allowed to operate this physical ward. */
    private static final String TAG_ACTIVE_WHO = "WardActiveWho";
    /** Short lease prevents a disconnected player from orphaning a shared ward forever. */
    private static final String TAG_ACTIVE_UNTIL = "WardActiveUntil";
    private static final long ACTIVE_LEASE_TICKS = 20L * 180L;
    /** This container's seal carries no maker's mark. Decided once, at binding. */
    public static final String TAG_UNSIGNED = "WardUnsigned";
    /** Which link of a chain this container is, 1-based. 0 for an ordinary ward. */
    public static final String TAG_CHAIN_LINK = "WardChainLink";
    /**
     * The loot table this container was scanned from.
     *
     * <p>Kept so the multiplier can roll the table again for genuinely new
     * items instead of only duplicating what came out the first time. The table
     * id is gone from the block entity by the time loot is being generated, so
     * it has to be written down here while it is still known.
     */
    public static final String TAG_TABLE = "WardTable";
    /** Stages left on a gauntlet. See {@link #newState}. */
    public static final String TAG_GAUNTLET = "WardGauntlet";
    public static final String TAG_AFFLICTION = "WardAffliction";
    public static final String TAG_LIVING = "WardLiving";
    public static final String TAG_RELIC_GRADE = "WardRelicGrade";
    /** Stages left on a rare Eldritch Ward. */
    public static final String TAG_ELDRITCH = "WardEldritch";
    /** Bitset of minigames already used by the current Eldritch chain. */
    public static final String TAG_ELDRITCH_USED = "WardEldritchUsedGames";
    /** Rare one-stage corruption, mutually exclusive with an Eldritch chain. */
    public static final String TAG_POSSESSED = "WardPossessed";
    /** An ultra-rare singular ward that always plays Cthulhu's Game. */
    public static final String TAG_CTHULHU = "WardCthulhu";
    /** A consumed Ward Splinter attunement has bound itself to this physical ward. */
    public static final String TAG_SPLINTER_BRACE = "WardSplinterBrace";
    /** A consumed Eldritch Shard attunement has bound itself to this physical ward. */
    public static final String TAG_SHARD_OVERCHARGE = "WardShardOvercharge";
    /** One-shot marker consumed by the loot modifier to duplicate final chest contents. */
    public static final String TAG_DOUBLE_LOOT = "WardDoubleLoot";
    /** Legacy single-player memory keys retained for seamless save migration. */
    public static final String TAG_MEMORY_WHO = "WardMemoryWho";
    public static final String TAG_MEMORY_WINS = "WardMemoryWins";
    public static final String TAG_MEMORY_LOSSES = "WardMemoryLosses";
    /** Signed streak: positive = consecutive wins, negative = consecutive losses on this exact physical chest. */
    public static final String TAG_MEMORY_STREAK = "WardMemoryStreak";
    /** True multiplayer memory: one nested record per UUID on the same physical ward. */
    public static final String TAG_MEMORY_PLAYERS = "WardMemoryPlayers";
    /** A rare Echo Shard is bound to this physical ward. */
    public static final String TAG_ECHO_SHARD = "WardEchoShard";

    private static final float SPLINTER_LOOT = 1.50f;

    /** How many times a loot table is rolled to estimate its worth. */
    private static final int SAMPLE_ROLLS = 8;
    private static final int MAX_SCAN_VOLUME = 60000;
    /** Keep structure-load bursts from spending an entire server tick scanning wards. */
    private static final int MAX_PENDING_SCANS_PER_TICK = 4;
    private static final int STATIC_LOOT_RECURSION_LIMIT = 12;

    private static final Map<ResourceLocation, Integer> SCORE_CACHE = new HashMap<>();
    private static final Random RNG = new Random();
    private static final CopyOnWriteArrayList<PendingScan> PENDING = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<WinResidue> RESIDUES = new CopyOnWriteArrayList<>();
    private record WinResidue(ServerLevel level, BlockPos pos, int[] ticks, int kind) {}

    /**
     * Loose sealed-card hands belong to the player rather than a block. Store them
     * inside Forge's death-persistent player compound; older raw keys are migrated
     * lazily the first time the player touches a loose hand after upgrading.
     */
    private static CompoundTag loosePlayerData(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)
                ? root.getCompound(Player.PERSISTED_NBT_TAG) : new CompoundTag();
        String[] keys = {LOOSE_OFFERS, LOOSE_MODE, LOOSE_SEED, LOOSE_POS, LOOSE_DEALER};
        boolean migrated = false;
        for (String key : keys) {
            Tag legacy = root.get(key);
            if (legacy != null && !persisted.contains(key)) {
                persisted.put(key, legacy.copy());
                migrated = true;
            }
            if (legacy != null) root.remove(key);
        }
        if (migrated || !root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND))
            root.put(Player.PERSISTED_NBT_TAG, persisted);
        return persisted;
    }

    /** Only one live hand may operate a physical ward at a time on multiplayer servers. */
    private static boolean claimWardAttempt(ServerPlayer player, RandomizableContainerBlockEntity container) {
        CompoundTag p = container.getPersistentData();
        long now = player.level().getGameTime();
        if (p.hasUUID(TAG_ACTIVE_WHO)) {
            UUID owner = p.getUUID(TAG_ACTIVE_WHO);
            if (!owner.equals(player.getUUID()) && now < p.getLong(TAG_ACTIVE_UNTIL)) {
                ServerPlayer other = player.getServer() == null ? null
                        : player.getServer().getPlayerList().getPlayer(owner);
                if (other != null) {
                    WardHud.message(player, Component.literal("Another hand is already on this seal.")
                            .withStyle(ChatFormatting.DARK_AQUA), true);
                    return false;
                }
            }
        }
        p.putUUID(TAG_ACTIVE_WHO, player.getUUID());
        p.putLong(TAG_ACTIVE_UNTIL, now + ACTIVE_LEASE_TICKS);
        container.setChanged();
        return true;
    }

    private static void releaseWardAttempt(RandomizableContainerBlockEntity container, UUID player) {
        CompoundTag p = container.getPersistentData();
        if (!p.hasUUID(TAG_ACTIVE_WHO) || p.getUUID(TAG_ACTIVE_WHO).equals(player)) {
            p.remove(TAG_ACTIVE_WHO);
            p.remove(TAG_ACTIVE_UNTIL);
            container.setChanged();
        }
    }

    private static boolean isDecisionOwner(ServerPlayer player, CompoundTag persistent) {
        if (!persistent.hasUUID(TAG_WHO) || persistent.getUUID(TAG_WHO).equals(player.getUUID())) return true;
        // Do not orphan a shared-world chest forever when its original witness logs
        // out. While that witness is online the decision remains theirs; once they
        // are gone, another player may finish the physical container's pending state.
        MinecraftServer server = player.getServer();
        return server != null && server.getPlayerList().getPlayer(persistent.getUUID(TAG_WHO)) == null;
    }

    private ChestValuator() {
    }

    /** Migrates the v32 single-owner fields into a UUID-keyed ledger and ensures a record exists. */
    private static void prepareChestMemory(CompoundTag tag, UUID playerId) {
        if (tag == null || playerId == null) return;
        CompoundTag all = tag.getCompound(TAG_MEMORY_PLAYERS);
        String key = playerId.toString();
        if (!all.contains(key)) {
            CompoundTag record = new CompoundTag();
            if (tag.hasUUID(TAG_MEMORY_WHO) && tag.getUUID(TAG_MEMORY_WHO).equals(playerId)) {
                record.putInt("wins", tag.getInt(TAG_MEMORY_WINS));
                record.putInt("losses", tag.getInt(TAG_MEMORY_LOSSES));
                record.putInt("streak", tag.getInt(TAG_MEMORY_STREAK));
            }
            all.put(key, record);
            tag.put(TAG_MEMORY_PLAYERS, all);
        }
    }

    /** Package-visible so ChestThreat can read the same player record without duplicating migration logic. */
    static CompoundTag chestMemory(CompoundTag tag, UUID playerId) {
        prepareChestMemory(tag, playerId);
        CompoundTag all = tag.getCompound(TAG_MEMORY_PLAYERS);
        return all.getCompound(playerId.toString()).copy();
    }

    static void saveChestMemory(CompoundTag tag, UUID playerId, CompoundTag record) {
        if (tag == null || playerId == null || record == null) return;
        CompoundTag all = tag.getCompound(TAG_MEMORY_PLAYERS);
        all.put(playerId.toString(), record.copy());
        tag.put(TAG_MEMORY_PLAYERS, all);
        // Mirror the active player's values into the legacy fields so old worlds/tools remain intelligible.
        tag.putUUID(TAG_MEMORY_WHO, playerId);
        tag.putInt(TAG_MEMORY_WINS, record.getInt("wins"));
        tag.putInt(TAG_MEMORY_LOSSES, record.getInt("losses"));
        tag.putInt(TAG_MEMORY_STREAK, record.getInt("streak"));
    }

    private record PendingScan(ServerLevel level, BoundingBox box, int[] delay) {
    }

    // ------------------------------------------------------------------ scoring

    private static int scoreLootTable(ServerLevel level, ResourceLocation tableId, BlockPos pos) {
        Integer cached = SCORE_CACHE.get(tableId);
        if (cached != null) return cached;

        int score = 0;
        try {
            /*
             * Never execute a loot table for valuation when it contains a world-query
             * function such as minecraft:exploration_map. Running that function from the
             * server-tick chest scanner can trigger a synchronous structure lookup and
             * block the integrated/dedicated server for tens of seconds.
             *
             * For those tables we inspect the data-pack JSON instead. It is intentionally
             * an estimate: valuation only needs a stable 0-100 richness signal; it must not
             * generate real loot or cause chunks/structures to be searched.
             */
            if (requiresStaticLootScoring(level.getServer(), tableId)) {
                score = scoreLootTableStatic(level.getServer(), tableId);
                Wardbound.LOG.debug("[Wardbound] statically scored world-query loot table {} as {}", tableId, score);
                SCORE_CACHE.put(tableId, score);
                return score;
            }

            LootTable table = level.getServer().getLootData().getLootTable(tableId);
            if (table == LootTable.EMPTY) {
                SCORE_CACHE.put(tableId, 0);
                return 0;
            }
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .create(LootContextParamSets.CHEST);

            // roll the table a few times and remember, per item, how often it showed up
            // and how many came out. Frequency is our free rarity signal: something that
            // only drops in one roll out of eight is a jackpot entry.
            Map<Item, int[]> stats = new HashMap<>();          // [appearances, total count]
            Map<Item, ItemStack> sample = new HashMap<>();

            for (int i = 0; i < SAMPLE_ROLLS; i++) {
                for (ItemStack st : table.getRandomItems(params)) {
                    if (st.isEmpty()) continue;
                    Item item = st.getItem();
                    int[] a = stats.computeIfAbsent(item, k -> new int[2]);
                    a[0]++;
                    a[1] += st.getCount();
                    sample.putIfAbsent(item, st);
                }
            }

            if (stats.isEmpty()) {
                SCORE_CACHE.put(tableId, 0);
                return 0;
            }

            double raw = 0;
            int best = 0;
            int distinct = 0;
            int valuable = 0;

            for (Map.Entry<Item, int[]> e : stats.entrySet()) {
                ItemStack st = sample.get(e.getKey());
                int per = ItemValueResolver.score(st);

                float freq = Math.min(1f, e.getValue()[0] / (float) SAMPLE_ROLLS);
                float avgCount = e.getValue()[1] / (float) SAMPLE_ROLLS;
                float scarcity = 1f + 0.75f * (1f - freq);

                raw += per * Math.sqrt(Math.max(1f, avgCount)) * scarcity;
                distinct++;
                if (per > 2) valuable++;
                if (per > best) best = per;
            }

            float valuableRatio = (float) valuable / (float) distinct;
            float junkPenalty = valuableRatio < 0.20f ? 0.55f : 1.0f;
            float jackpotBonus = best >= 30 ? 1.35f : (best >= 22 ? 1.15f : 1.0f);

            score = (int) (Math.sqrt(raw) * 5.5d * Math.max(0.35f, valuableRatio + 0.25f)
                    * junkPenalty * jackpotBonus);
            score = Math.max(0, Math.min(100, score));
        } catch (Exception e) {
            Wardbound.LOG.debug("[Wardbound] could not score {}: {}", tableId, e.toString());
        }

        SCORE_CACHE.put(tableId, score);
        return score;
    }

    /** Returns the raw data-pack JSON for a loot table without executing it. */
    private static JsonObject lootTableJson(MinecraftServer server, ResourceLocation tableId) {
        if (server == null || tableId == null) return null;
        ResourceLocation resourceId = new ResourceLocation(tableId.getNamespace(),
                "loot_tables/" + tableId.getPath() + ".json");
        try {
            var resource = server.getResourceManager().getResource(resourceId);
            if (resource.isEmpty()) return null;
            try (BufferedReader reader = resource.get().openAsReader()) {
                JsonElement parsed = JsonParser.parseReader(reader);
                return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
            }
        } catch (Exception e) {
            Wardbound.LOG.debug("[Wardbound] could not inspect loot table {}: {}", tableId, e.toString());
            return null;
        }
    }

    /**
     * Detect functions whose normal execution may search the world or wait for
     * structure generation. Nested loot-table entries are followed as well.
     */
    private static boolean requiresStaticLootScoring(MinecraftServer server, ResourceLocation tableId) {
        return containsWorldQueryLootFunction(server, tableId, new HashSet<>(), 0);
    }

    private static boolean containsWorldQueryLootFunction(MinecraftServer server, ResourceLocation tableId,
                                                           Set<ResourceLocation> visiting, int depth) {
        if (server == null || tableId == null || depth > STATIC_LOOT_RECURSION_LIMIT || !visiting.add(tableId))
            return false;
        try {
            JsonObject root = lootTableJson(server, tableId);
            if (root == null) return false;
            if (containsWorldQueryLootFunction(root)) return true;

            // A safe-looking parent can still delegate to an unsafe child table.
            for (ResourceLocation nested : referencedLootTables(root)) {
                if (containsWorldQueryLootFunction(server, nested, visiting, depth + 1)) return true;
            }
            return false;
        } finally {
            visiting.remove(tableId);
        }
    }

    private static boolean containsWorldQueryLootFunction(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray())
                if (containsWorldQueryLootFunction(child)) return true;
            return false;
        }
        if (!element.isJsonObject()) return false;

        JsonObject object = element.getAsJsonObject();
        JsonElement function = object.get("function");
        if (function != null && function.isJsonPrimitive()) {
            String id = normalizeLootId(function.getAsString());
            if ("minecraft:exploration_map".equals(id)) return true;
        }
        for (Map.Entry<String, JsonElement> child : object.entrySet())
            if (containsWorldQueryLootFunction(child.getValue())) return true;
        return false;
    }

    private static Set<ResourceLocation> referencedLootTables(JsonElement element) {
        Set<ResourceLocation> out = new HashSet<>();
        collectReferencedLootTables(element, out);
        return out;
    }

    private static void collectReferencedLootTables(JsonElement element, Set<ResourceLocation> out) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectReferencedLootTables(child, out);
            return;
        }
        if (!element.isJsonObject()) return;

        JsonObject object = element.getAsJsonObject();
        String type = object.has("type") && object.get("type").isJsonPrimitive()
                ? normalizeLootId(object.get("type").getAsString()) : "";
        if ("minecraft:loot_table".equals(type) && object.has("name") && object.get("name").isJsonPrimitive()) {
            ResourceLocation nested = ResourceLocation.tryParse(object.get("name").getAsString());
            if (nested != null) out.add(nested);
        }
        for (Map.Entry<String, JsonElement> child : object.entrySet())
            collectReferencedLootTables(child.getValue(), out);
    }

    private static String normalizeLootId(String id) {
        if (id == null || id.isBlank()) return "";
        return id.indexOf(':') >= 0 ? id : "minecraft:" + id;
    }

    /**
     * Side-effect-free fallback used only for tables that cannot safely be sampled.
     * It reads item entries and rough count/weight information from JSON.
     */
    private static int scoreLootTableStatic(MinecraftServer server, ResourceLocation tableId) {
        Map<Item, double[]> stats = new HashMap<>(); // [weighted presence, weighted count]
        collectStaticLootTable(server, tableId, 1.0d, stats, new HashSet<>(), 0);

        if (stats.isEmpty()) {
            // Unknown/custom world-query table: keep it eligible without pretending it is a jackpot.
            return Math.max(10, Math.min(100, WardConfig.lockThreshold));
        }

        double totalPresence = 0d;
        for (double[] a : stats.values()) totalPresence += Math.max(0.0001d, a[0]);

        double raw = 0d;
        int best = 0;
        int distinct = 0;
        int valuable = 0;
        for (Map.Entry<Item, double[]> e : stats.entrySet()) {
            int per = ItemValueResolver.score(new ItemStack(e.getKey()));
            double presence = Math.max(0.0001d, e.getValue()[0]);
            double avgCount = Math.max(1d, e.getValue()[1] / presence);
            float freq = (float) Math.min(1d, presence / Math.max(0.0001d, totalPresence));
            float scarcity = 1f + 0.75f * (1f - freq);

            raw += per * Math.sqrt(avgCount) * scarcity;
            distinct++;
            if (per > 2) valuable++;
            if (per > best) best = per;
        }

        float valuableRatio = distinct <= 0 ? 0f : (float) valuable / (float) distinct;
        float junkPenalty = valuableRatio < 0.20f ? 0.55f : 1.0f;
        float jackpotBonus = best >= 30 ? 1.35f : (best >= 22 ? 1.15f : 1.0f);
        int score = (int) (Math.sqrt(raw) * 5.5d * Math.max(0.35f, valuableRatio + 0.25f)
                * junkPenalty * jackpotBonus);
        return Math.max(0, Math.min(100, score));
    }

    private static void collectStaticLootTable(MinecraftServer server, ResourceLocation tableId, double scale,
                                               Map<Item, double[]> stats, Set<ResourceLocation> visiting, int depth) {
        if (server == null || tableId == null || depth > STATIC_LOOT_RECURSION_LIMIT || !visiting.add(tableId)) return;
        try {
            JsonObject root = lootTableJson(server, tableId);
            if (root == null || !root.has("pools") || !root.get("pools").isJsonArray()) return;
            for (JsonElement poolElement : root.getAsJsonArray("pools")) {
                if (!poolElement.isJsonObject()) continue;
                JsonObject pool = poolElement.getAsJsonObject();
                double rolls = staticNumber(pool.get("rolls"), 1d);
                if (!pool.has("entries") || !pool.get("entries").isJsonArray()) continue;
                for (JsonElement entry : pool.getAsJsonArray("entries"))
                    collectStaticEntry(server, entry, scale * Math.max(0.25d, rolls), stats, visiting, depth);
            }
        } finally {
            visiting.remove(tableId);
        }
    }

    private static void collectStaticEntry(MinecraftServer server, JsonElement element, double scale,
                                           Map<Item, double[]> stats, Set<ResourceLocation> visiting, int depth) {
        if (element == null || !element.isJsonObject() || depth > STATIC_LOOT_RECURSION_LIMIT) return;
        JsonObject entry = element.getAsJsonObject();
        double weight = entry.has("weight") && entry.get("weight").isJsonPrimitive()
                ? Math.max(0.05d, entry.get("weight").getAsDouble()) : 1d;
        double count = staticSetCount(entry.get("functions"));
        double effective = Math.min(128d, Math.max(0.0001d, scale * weight));

        String type = entry.has("type") && entry.get("type").isJsonPrimitive()
                ? normalizeLootId(entry.get("type").getAsString()) : "";

        if ("minecraft:item".equals(type) && entry.has("name") && entry.get("name").isJsonPrimitive()) {
            ResourceLocation itemId = ResourceLocation.tryParse(entry.get("name").getAsString());
            if (itemId != null) {
                Item item = ForgeRegistries.ITEMS.getValue(itemId);
                if (item != null) {
                    double[] a = stats.computeIfAbsent(item, k -> new double[2]);
                    a[0] += effective;
                    a[1] += effective * Math.max(1d, count);
                }
            }
            return;
        }

        if ("minecraft:loot_table".equals(type) && entry.has("name") && entry.get("name").isJsonPrimitive()) {
            ResourceLocation nested = ResourceLocation.tryParse(entry.get("name").getAsString());
            if (nested != null) collectStaticLootTable(server, nested, effective, stats, visiting, depth + 1);
            return;
        }

        // alternatives / group / sequence and modded composite entries usually expose children.
        JsonElement children = entry.get("children");
        if (children != null && children.isJsonArray()) {
            JsonArray array = children.getAsJsonArray();
            for (JsonElement child : array)
                collectStaticEntry(server, child, effective, stats, visiting, depth + 1);
        }
    }

    private static double staticSetCount(JsonElement functions) {
        if (functions == null || !functions.isJsonArray()) return 1d;
        double count = 1d;
        for (JsonElement f : functions.getAsJsonArray()) {
            if (!f.isJsonObject()) continue;
            JsonObject function = f.getAsJsonObject();
            if (!function.has("function") || !function.get("function").isJsonPrimitive()) continue;
            if (!"minecraft:set_count".equals(normalizeLootId(function.get("function").getAsString()))) continue;
            double next = staticNumber(function.get("count"), count);
            boolean add = function.has("add") && function.get("add").isJsonPrimitive() && function.get("add").getAsBoolean();
            count = add ? count + next : next;
        }
        return Math.max(1d, count);
    }

    private static double staticNumber(JsonElement element, double fallback) {
        if (element == null || element.isJsonNull()) return fallback;
        try {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) return element.getAsDouble();
            if (!element.isJsonObject()) return fallback;
            JsonObject object = element.getAsJsonObject();
            if (object.has("value") && object.get("value").isJsonPrimitive()
                    && object.get("value").getAsJsonPrimitive().isNumber())
                return object.get("value").getAsDouble();
            if (object.has("min") && object.has("max")
                    && object.get("min").isJsonPrimitive() && object.get("max").isJsonPrimitive())
                return (object.get("min").getAsDouble() + object.get("max").getAsDouble()) * 0.5d;
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    private static ResourceLocation lootTableOf(RandomizableContainerBlockEntity container) {
        CompoundTag full = container.serializeNBT();
        if (!full.contains("LootTable")) return null;
        return ResourceLocation.tryParse(full.getString("LootTable"));
    }

    private static void tagContainer(ServerLevel level, BlockPos pos, RandomizableContainerBlockEntity container) {
        CompoundTag persistent = container.getPersistentData();
        if (persistent.contains(TAG_SCANNED)) return;
        ResourceLocation table = lootTableOf(container);
        if (table == null) return;

        int value = scoreLootTable(level, table, pos);
        persistent.putBoolean(TAG_SCANNED, true);
        persistent.putInt(TAG_VALUE, value);
        persistent.putString(TAG_TABLE, table.toString());
        container.setChanged();
    }

    private static void scanBox(ServerLevel level, BoundingBox box) {
        long volume = (long) (box.maxX() - box.minX() + 1)
                * (box.maxY() - box.minY() + 1)
                * (box.maxZ() - box.minZ() + 1);
        if (volume > MAX_SCAN_VOLUME) return;

        int cxFrom = box.minX() >> 4, cxTo = box.maxX() >> 4;
        int czFrom = box.minZ() >> 4, czTo = box.maxZ() >> 4;

        for (int cx = cxFrom; cx <= cxTo; cx++) {
            for (int cz = czFrom; cz <= czTo; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> e : new ArrayList<>(chunk.getBlockEntities().entrySet())) {
                    BlockPos pos = e.getKey();
                    if (!box.isInside(pos)) continue;
                    if (e.getValue() instanceof RandomizableContainerBlockEntity container) {
                        tagContainer(level, pos, container);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ opening the minigame

    /**
     * Weighted anti-repetition selection. Recent games are merely de-weighted,
     * not forbidden, so a two-game dimension can never paint itself into a
     * corner. The chosen ordinal is stored in the live state immediately after
     * binding, so leaving and reopening a ward cannot reroll it.
     */
    private static MinigameType directedGame(ServerPlayer player, DimSettings settings,
                                             long seed, MinigameType avoid) {
        MinecraftServer server = player.getServer();
        if (!WardConfig.antiRepetitionEnabled || server == null)
            return settings.gameFor(seed, avoid);

        List<MinigameType> candidates = new ArrayList<>();
        if (settings.pool != null && settings.pool.length > 0) {
            for (MinigameType g : settings.pool)
                if (WardConfig.tuning(g).enabled && !candidates.contains(g)) candidates.add(g);
        } else if (settings.randomGame) {
            for (MinigameType g : MinigameType.values())
                if (g != MinigameType.CTHULHUS_GAME && WardConfig.tuning(g).enabled) candidates.add(g);
        } else if (WardConfig.tuning(settings.minigame).enabled) {
            candidates.add(settings.minigame);
        }
        if (candidates.isEmpty()) return settings.gameFor(seed, avoid);
        if (avoid != null && candidates.size() > 1) candidates.remove(avoid);
        if (candidates.size() == 1) return candidates.get(0);

        LockData data = LockData.get(server);
        String directorDim = player.level().dimension().location().toString();
        int[] recent = data.recentGames(player.getUUID(), directorDim);
        int directorBand = data.directorBand(player.getUUID(), directorDim);
        int window = Math.min(WardConfig.antiRepetitionWindow, recent.length);
        double total = 0.0;
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            MinigameType g = candidates.get(i);
            double w = 1.0;
            for (int age = 0; age < window; age++) {
                if (recent[age] == g.ordinal()) {
                    w = Math.min(1.0, WardConfig.antiRepetitionRecentWeight
                            + age * WardConfig.antiRepetitionDecay);
                    break;
                }
            }
            // The director changes variety, not the screen's raw difficulty.
            // Strong recent play nudges the deck toward denser/stranger games;
            // struggling play rotates toward a different texture of challenge.
            if (directorBand > 0) w *= 0.78 + g.reward * 0.34;
            else if (directorBand < 0) w *= Math.max(0.78, 1.32 - Math.max(0f, g.reward - 1.0f) * 0.55);
            weights[i] = Math.max(0.01, w);
            total += weights[i];
        }

        long h = seed ^ player.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 17);
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        double pick = ((h >>> 1) / (double) Long.MAX_VALUE) * total;
        for (int i = 0; i < candidates.size(); i++) {
            pick -= weights[i];
            if (pick <= 0.0) return candidates.get(i);
        }
        return candidates.get(candidates.size() - 1);
    }

    /** Picks a chain stage that has not already appeared, whenever the configured pool allows it. */
    private static MinigameType directedEldritchGame(ServerPlayer player, DimSettings settings,
                                                       long seed, MinigameType avoid, long usedMask) {
        MinigameType fallback = directedGame(player, settings, seed, avoid);
        if ((usedMask & (1L << fallback.ordinal())) == 0L) return fallback;
        // Vary only the deterministic selection salt. In a one-game dimension
        // pool repetition is unavoidable, so eventually fall back cleanly.
        for (int i = 1; i <= 48; i++) {
            long probe = seed + i * 0x9E3779B97F4A7C15L;
            MinigameType pick = directedGame(player, settings, probe, avoid);
            if ((usedMask & (1L << pick.ordinal())) == 0L) return pick;
        }
        return fallback;
    }

    /** Echo Shard selection: avoid the last five resolved games whenever the configured pool permits it. */
    private static MinigameType directedNovelGame(ServerPlayer player, DimSettings settings, long seed, MinigameType avoid) {
        MinigameType fallback = directedGame(player, settings, seed, avoid);
        MinecraftServer server = player.getServer();
        if (server == null) return fallback;
        int[] recent = LockData.get(server).recentGames(player.getUUID(), player.level().dimension().location().toString());
        for (int attempt = 0; attempt < 64; attempt++) {
            MinigameType pick = directedGame(player, settings, seed + attempt * 0x632BE59BD9B4E019L, avoid);
            boolean seen = false;
            for (int i = 0; i < Math.min(5, recent.length); i++) if (recent[i] == pick.ordinal()) { seen = true; break; }
            if (!seen) return pick;
        }
        return fallback;
    }

    private static String afflictionName(int id) {
        return switch (id) {
            case 1 -> "whispering";
            case 2 -> "hollow";
            case 3 -> "blind";
            case 4 -> "starved";
            case 5 -> "blood-warm";
            default -> "";
        };
    }

    private static int mutationLevel(RandomizableContainerBlockEntity container) {
        return mutationLevel(container, Integer.MAX_VALUE);
    }

    private static int mutationLevel(RandomizableContainerBlockEntity container, int totalBeaten) {
        CompoundTag p = container.getPersistentData();
        int age = p.getInt(TAG_SCAR_BREAKS)
                + (p.getBoolean(TAG_SCAR_RESEALED) ? 1 : 0)
                + (p.getBoolean(TAG_SCAR_GRUDGE) ? 1 : 0)
                + (p.getBoolean(TAG_SCAR_BURNED) ? 1 : 0);
        if (totalBeaten >= WardConfig.mutationTier3AfterBeaten && age >= WardConfig.mutationTier3Age) return 3;
        if (totalBeaten >= WardConfig.mutationTier2AfterBeaten && age >= WardConfig.mutationTier2Age) return 2;
        if (totalBeaten >= WardConfig.mutationTier1AfterBeaten && age >= WardConfig.mutationTier1Age) return 1;
        return 0;
    }

    private static boolean rivalryActive(LockData data, UUID player, long lockSeed) {
        if (!WardConfig.masterRivalriesEnabled || lockSeed == 0L) return false;
        String rival = Sealmakers.rival(Sealmakers.house(lockSeed));
        return data.familiarity(player, rival) >= WardConfig.masterRivalryAfter;
    }

    private static void queueWinResidue(ServerLevel level, BlockPos pos, int kind) {
        if (!WardConfig.postWinResidueEnabled) return;
        RESIDUES.add(new WinResidue(level, pos.immutable(), new int[]{45}, kind));
    }

    private static float oldWardLoot(RandomizableContainerBlockEntity container) {
        CompoundTag p = container.getPersistentData();
        int visibleAge = p.getInt(TAG_SCAR_BREAKS)
                + (p.getBoolean(TAG_SCAR_RESEALED) ? 1 : 0)
                + (p.getBoolean(TAG_SCAR_GRUDGE) ? 1 : 0);
        if (visibleAge <= 0) return 1f;
        return Math.min(WardConfig.oldWardLootCap,
                1f + visibleAge * WardConfig.oldWardLootPerScar);
    }

    private static int scarFlags(RandomizableContainerBlockEntity container) {
        CompoundTag p = container.getPersistentData();
        int f = 0;
        if (p.getBoolean(TAG_SCAR_BURNED)) f |= 1;
        if (p.getBoolean(TAG_SCAR_GRUDGE)) f |= 2;
        if (p.getBoolean(TAG_SCAR_RESEALED)) f |= 4;
        return f;
    }

    public static void openMinigame(ServerPlayer player, BlockPos pos, RandomizableContainerBlockEntity container) {
        openMinigame(player, pos, container, ItemStack.EMPTY);
    }

    /**
     * Opens a ward using only the exact stack from the hand that performed the
     * interaction. The stack is consulted only when a brand-new ward state is
     * created; an already-bound ward keeps the aid committed at creation time.
     */
    private static void openMinigame(ServerPlayer player, BlockPos pos,
                                     RandomizableContainerBlockEntity container,
                                     ItemStack interactionStack) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        String dimId = player.level().dimension().location().toString();
        DimSettings settings = WardConfig.forDimension(dimId);
        LockData data = LockData.get(server);
        UUID uuid = player.getUUID();
        if (!claimWardAttempt(player, container)) return;

        int value = container.getPersistentData().getInt(TAG_VALUE);

        CompoundTag state;
        if (data.has(uuid, dimId, pos)) {
            state = data.getOrCreate(uuid, dimId, pos);
            // Migration guard for pre-hardening saves. Older builds persisted a
            // client-resumable snapshot after ESC. The current lifecycle never
            // resumes a live puzzle, so an old snapshot is authoritatively
            // converted into the same abandonment failure as a vanished screen.
            if (state.getBoolean("resume") && !state.getBoolean("attemptIssued")) {
                int legacyLives = Math.max(1, state.getInt("lives"));
                state.putInt("issuedLives", legacyLives);
                state.putInt("issuedMaxLives", legacyLives);
                state.putBoolean("attemptIssued", true);
                data.put(uuid, dimId, pos, state);
                resolveFailure(player, pos, state.getLong("seed"), legacyLives, 0f,
                        Math.max(0f, state.getFloat("elapsedSeconds")),
                        Math.max(1, state.getInt("mistakes") + 1), 0, "ABANDONED_LEGACY",
                        Math.max(0, state.getInt("rapidMistakes")),
                        state.getBoolean("hybridActive") ? 2 : state.getInt("hybridOutcome"));
                return;
            }
            // A prior screen was issued but never produced a result (disconnect,
            // forced screen replacement, crash). Reopening cannot become a free reset.
            if (state.getBoolean("attemptIssued")) {
                long staleSeed = state.getLong("seed");
                // Do not clear attemptIssued here. resolveFailure owns the atomic
                // consume step; pre-clearing it would make a replay guard reject
                // this legitimate server-side abandonment resolution.
                resolveFailure(player, pos, staleSeed, Math.max(1, state.getInt("lives")), 0f,
                        Math.max(0f, state.getFloat("elapsedSeconds")),
                        Math.max(1, state.getInt("mistakes") + 1), 0, "ABANDONED_SERVER",
                        state.getInt("rapidMistakes"), state.getBoolean("hybridActive") ? 2 : state.getInt("hybridOutcome"));
                return;
            }
        } else {
            state = newState(player, settings, pos, container, interactionStack);
            data.put(uuid, dimId, pos, state);
        }

        // Whatever charm was bound when this chest first locked, not whatever is
        // in the player's bag right now. Re-reading the inventory here would let
        // someone start a chest bare, walk away when it goes badly, and come
        // back holding a heart.
        int tier = tierOf(data.totalBeaten(uuid));
        // How many times this particular container has already beaten you.
        int spite = container.getPersistentData().getInt(TAG_SPITE);
        String verdict = data.verdict(uuid);
        boolean watchingMarkProvenanceAllowed = !state.getBoolean("watchingMarkForced")
                || state.getBoolean("watchingMarkBound");
        boolean watched = watchingMarkProvenanceAllowed
                && Sealmakers.house(state.getLong("seed")).equals(data.watcher(uuid));

        int chainLink = container.getPersistentData().getInt(TAG_CHAIN_LINK);

        Charm charm = Charm.byId(state.getString("charm"));
        // Favour is a standing gift and applies to every lock anywhere, which is
        // what makes it worth having over a bigger number on one house's work.
        int maxLives = settings.lives + charm.bonusLives()
                + ("favour".equals(verdict) ? 1 : 0)
                + (state.getBoolean("mercy") ? 1 : 0)
                + state.getInt("cardLifeDelta");
        if (state.getBoolean("borrowedBreath")) maxLives = Math.max(1, maxLives - 1);
        if (state.getBoolean("cardForceOneLife")) maxLives = 1;
        maxLives = Math.max(1, maxLives);
        int lives = Math.min(state.getInt("lives"), maxLives);

        int streak = data.streak(uuid, dimId);
        MinigameType game;
        if (state.contains("game")) {
            game = MinigameType.byOrdinal(state.getInt("game"));
        } else {
            game = directedGame(player, settings, state.getLong("seed"), lastGame(container));
            state.putInt("game", game.ordinal());
            data.put(uuid, dimId, pos, state);
        }
        WardAdvancements.firstWard(player);
        WardAdvancements.enterMinigame(player, game);

        int historyCount = Math.min(WardConfig.wardHistoryScarCap,
                container.getPersistentData().getInt(TAG_SCAR_BREAKS)
                        + container.getPersistentData().getInt(TAG_SPITE));

        int eldritchLeft = container.getPersistentData().getInt(TAG_ELDRITCH);
        if (eldritchLeft > 0) {
            long used = container.getPersistentData().getLong(TAG_ELDRITCH_USED);
            container.getPersistentData().putLong(TAG_ELDRITCH_USED, used | (1L << game.ordinal()));
        }
        int eldritchTotal = eldritchLeft > 0 ? Math.max(eldritchLeft, container.getPersistentData().getInt(TAG_ELDRITCH + "Total")) : 0;
        int eldritchStage = eldritchLeft > 0 ? Math.max(1, eldritchTotal - eldritchLeft + 1) : 0;
        boolean possessed = container.getPersistentData().getBoolean(TAG_POSSESSED);
        int affliction = container.getPersistentData().getInt(TAG_AFFLICTION);
        boolean living = container.getPersistentData().getBoolean(TAG_LIVING);
        boolean cthulhu = container.getPersistentData().getBoolean(TAG_CTHULHU);
        boolean splinterBrace = container.getPersistentData().getBoolean(TAG_SPLINTER_BRACE);
        boolean shardOvercharge = container.getPersistentData().getBoolean(TAG_SHARD_OVERCHARGE);
        boolean mercy = state.getBoolean("mercy");
        boolean rivalry = !container.getPersistentData().getBoolean(TAG_UNSIGNED)
                && rivalryActive(data, uuid, state.getLong("seed"));
        int mutation = mutationLevel(container, data.totalBeaten(uuid));
        int masteryTier;
        MinigameType examWeak;
        MinigameType examStrong;
        if (game == MinigameType.CTHULHUS_GAME) {
            if (!state.contains("examTier")) {
                state.putInt("examTier", MinigameMastery.examTier(data, uuid));
                state.putInt("examWeakGame", MinigameMastery.weakestDiscipline(data, uuid).ordinal());
                state.putInt("examStrongGame", MinigameMastery.strongestDiscipline(data, uuid).ordinal());
                data.put(uuid, dimId, pos, state);
            }
            masteryTier = Mth.clamp(state.getInt("examTier"), 0, MinigameMastery.MAX_TIER);
            examWeak = MinigameType.byOrdinal(state.getInt("examWeakGame"));
            examStrong = MinigameType.byOrdinal(state.getInt("examStrongGame"));
        } else {
            masteryTier = MinigameMastery.tier(data, uuid, game);
            examWeak = MinigameMastery.weakestDiscipline(data, uuid);
            examStrong = MinigameMastery.strongestDiscipline(data, uuid);
        }
        int corruptionSlot;
        if (state.contains("corruptionVariant")) {
            corruptionSlot = state.getInt("corruptionVariant");
        } else {
            boolean unsignedSeal = container.getPersistentData().getBoolean(TAG_UNSIGNED);
            MinigameCorruption.Variant rolled = MinigameCorruption.choose(data, uuid, game, state.getLong("seed"),
                    data.totalBeaten(uuid), eldritchLeft > 0 || possessed || cthulhu || unsignedSeal);
            corruptionSlot = rolled.slot();
            state.putInt("corruptionVariant", corruptionSlot);
            data.put(uuid, dimId, pos, state);
        }
        MinigameCorruption.Variant corruption = MinigameCorruption.variant(game, corruptionSlot);
        if (corruption.active() && !state.getBoolean("corruptionRecorded")) {
            MinigameMastery.recordCorruption(player, data, game, corruption);
            state.putBoolean("corruptionRecorded", true);
            data.put(uuid, dimId, pos, state);
        }
        boolean advancedBlocked = eldritchLeft > 0 || possessed || cthulhu
                || container.getPersistentData().getBoolean(TAG_UNSIGNED);
        if (!state.contains("deceptionMode")) {
            MinigameDeception.Mode deception = MinigameDeception.choose(data, uuid, game, state.getLong("seed"),
                    data.totalBeaten(uuid), masteryTier, advancedBlocked, corruption);
            state.putInt("deceptionMode", deception.ordinal());
            MinigameDeception.recordEncounter(player, data, deception);
            data.put(uuid, dimId, pos, state);
        }
        if (!state.contains("hybridMode")) {
            HybridRound.Mode hybrid = HybridRound.choose(data, uuid, game, state.getLong("seed"),
                    data.totalBeaten(uuid), masteryTier, advancedBlocked);
            state.putInt("hybridMode", hybrid.ordinal());
            // Discovery is recorded only when the borrowed mechanism actually appears,
            // not merely when a physical ward rolls one that a very fast clear may outrun.
            data.put(uuid, dimId, pos, state);
        }
        // Resume fields remain in OpenMinigamePacket only to preserve its wire
        // layout. Live server state never supplies resumable client snapshots.
        boolean resumed = false;
        float savedClockLeft = 0f;
        float savedElapsed = 0f;
        int savedMistakes = 0;
        int savedRapidMistakes = 0;
        float savedHurriedClock = 1f;
        boolean spentApplied = state.getBoolean("spentApplied");
        boolean hybridCompleted = state.getBoolean("hybridCompleted");
        int savedHybridOutcome = 0;
        boolean savedHybridActive = false;
        float savedHybridTimer = 0f;
        int savedHybridStep = 0;
        float savedHybridGauge = 0f;

        OpenMinigamePacket pkt = new OpenMinigamePacket(
                pos,
                value,
                game.ordinal(),
                lives,
                maxLives,
                state.getLong("seed"),
                state.getInt("progress"),
                settings.difficulty * WardConfig.tuning(game).difficultyScale
                        * Math.max(streakDifficulty(streak), depthDifficulty(container))
                        * MinigameMastery.learningDifficultyScale(masteryTier, advancedBlocked)
                        * tierDifficulty(tier)
                        * (1f + chainLink * WardConfig.chainDifficultyPerLink)
                        * (1f + spite * WardConfig.spiteDifficulty)
                        * (watched && "contempt".equals(verdict) ? WardConfig.contemptDifficulty : 1f)
                        * (rivalry ? WardConfig.masterRivalryDifficulty : 1f)
                        * (1f + mutation * WardConfig.wardMutationDifficultyPerLevel)
                        * (eldritchLeft > 0 ? (eldritchLeft == 1 ? 1.12f : 1.06f) : 1f)
                        * (possessed ? 1.05f : 1f)
                        * (living ? 1.08f : 1f)
                        * (cthulhu ? WardConfig.cthulhuWardDifficulty : 1f)
                        * (mercy ? 0.88f : 1f)
                        * (affliction == 4 ? 1.06f : affliction == 3 ? 1.04f : 1f)
                        * ChestThreat.difficultyScale(ChestThreat.boundPressure(player, container, data))
                        * AttentionSystem.difficultyScale(AttentionSystem.current(data, uuid))
                        * PlayerImprint.difficultyScale(data, uuid)
                        * Math.max(0.85f, state.getFloat("memoryDifficulty")),
                Math.min(progressionLootCap(data.totalBeaten(uuid)),
                        RewardBreakdown.clamp(RewardBreakdown.preview(game, settings, streak, charm, tier,
                                container.getPersistentData().getInt(TAG_DEPTH), chainLink, spite,
                                watched && "contempt".equals(verdict),
                                container.getPersistentData().getBoolean(TAG_UNSIGNED),
                                container.getPersistentData().getInt(TAG_GAUNTLET) == 1)
                                * (eldritchLeft == 1 ? WardConfig.eldritchWardLoot : 1f)
                                * (possessed ? WardConfig.possessedWardLoot : 1f)
                                * (living ? WardConfig.livingWardLoot : 1f)
                                * (cthulhu ? WardConfig.cthulhuWardLoot : 1f)
                                * (splinterBrace ? SPLINTER_LOOT : 1f)
                                * (mercy ? WardConfig.mercyLoot : 1f)
                                * (rivalry ? WardConfig.masterRivalryLoot : 1f)
                                * ChestThreat.lootScaleForPressure(ChestThreat.boundPressure(player, container, data))
                                * AttentionSystem.lootScale(AttentionSystem.current(data, uuid))
                                * PlayerImprint.lootScale(data, uuid)
                                * (1f + state.getFloat("cardLootBonus"))
                                * (1f + MinigameCorruption.rewardBonus(corruption))
                                * oldWardLoot(container))),
                dimId,
                streak,
                charm.ordinal(),
                data.familiarity(uuid, Sealmakers.house(state.getLong("seed"))),
                container.getPersistentData().getBoolean(TAG_UNSIGNED),
                state.getInt("seconds"),
                WardConfig.tuning(game).timeScale,
                WardConfig.tuning(game).speedScale,
                WardConfig.tuning(game).inputWindowScale,
                historyCount, scarFlags(container),
                eldritchLeft > 0, eldritchStage, eldritchTotal,
                possessed, rivalry, mutation,
                affliction, living, mercy,
                resumed, savedClockLeft, savedElapsed, savedMistakes, savedHurriedClock, spentApplied,
                state.getInt("cardMinigameMask"), masteryTier, corruptionSlot, examWeak.ordinal(), examStrong.ordinal(),
                state.getInt("deceptionMode"), state.getInt("hybridMode"), hybridCompleted, savedRapidMistakes, savedHybridOutcome,
                savedHybridActive, savedHybridTimer, savedHybridStep, savedHybridGauge,
                state.getInt("measureStage"), state.getInt("measureClause"), data.totalBeaten(uuid));

        // SPENT is a one-time anomaly tax. Mark it server-side at issuance; an
        // unresolved screen can only fail from here, never reopen as a fresh try.
        if (!spentApplied) state.putBoolean("spentApplied", true);
        // Server-side commitment marker closes the disconnect/crash loophole. A
        // legitimate result clears it before any next stage is issued.
        state.putInt("issuedLives", Math.max(0, lives));
        state.putInt("issuedMaxLives", Math.max(1, maxLives));
        state.putBoolean("attemptIssued", true);
        data.put(uuid, dimId, pos, state);
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
    }

    /**
     * Lets one house's work find you.
     *
     * <p>A seal's maker is a pure function of its seed, so rather than storing a
     * maker anywhere this nudges the seed until the name it produces is the one
     * that has taken an interest. Everything downstream, on both sides of the
     * network, keeps deriving the name the same way it always did and does not
     * need to know this happened.
     *
     * <p>The chance climbs with how many seals the player has broken in total,
     * from nothing at the start to about a third once they are deep in. It is
     * never certain, because a hand that turns up every single time is not
     * following you, it is just the only hand there is.
     */
    private static long attend(ServerPlayer player, long seed) {
        MinecraftServer server = player.getServer();
        if (server == null || !WardConfig.watcherEnabled) return seed;

        LockData data = LockData.get(server);
        int total = data.totalBeaten(player.getUUID());
        // Deliberately slow. Containers are everywhere, so a hand that notices
        // you after a dozen chests has noticed nothing; it has just met the
        // average player on their first afternoon. This should take a save.
        float chance = Math.min(WardConfig.watcherMaxChance,
                Math.max(0f, (total - WardConfig.watcherAfter) * WardConfig.watcherRamp));
        if (chance <= 0f || RNG.nextFloat() >= chance) return seed;

        String house = data.watcher(player.getUUID());
        for (int nudge = 0; nudge < 96; nudge++) {
            long candidate = seed + nudge * 0x2545F4914F6CDD1DL;
            if (Sealmakers.house(candidate).equals(house)) return candidate;
        }
        return seed;
    }

    /** Force a seed into one house without storing a second source of truth. */
    private static long forceHouse(long seed, String house) {
        for (int nudge = 0; nudge < 128; nudge++) {
            long candidate = seed + nudge * 0x2545F4914F6CDD1DL;
            if (Sealmakers.house(candidate).equals(house)) return candidate;
        }
        return seed;
    }

    /**
     * Special encounters keep their own progression economy. They may still use
     * ordinary minigames internally, but they must not consume "next ordinary
     * ward" promises or advance ordinary-only cadence systems.
     */
    private static boolean isSpecialEncounterWard(CompoundTag persistent) {
        if (persistent == null) return false;
        return persistent.getBoolean(TAG_CTHULHU)
                || persistent.getBoolean(TAG_UNSIGNED)
                || persistent.getBoolean(TAG_POSSESSED)
                || persistent.getInt(TAG_ELDRITCH) > 0
                || persistent.getInt(TAG_GAUNTLET) > 0
                || persistent.getInt(TAG_CHAIN_LINK) > 0;
    }

    /** A normal physical ward: not special, not an internal re-seal/depth loop. */
    private static boolean isOrdinaryPhysicalWard(CompoundTag persistent) {
        return persistent != null
                && !isSpecialEncounterWard(persistent)
                && persistent.getInt(TAG_DEPTH) == 0;
    }

    /**
     * Fresh states persist this classification so success/failure resolution is
     * based on what was actually issued, even if container tags are cleared later.
     * Old in-progress saves fall back to the physical container classification.
     */
    private static boolean isOrdinaryAttempt(CompoundTag state, CompoundTag persistent) {
        return state != null && state.contains("ordinaryWard")
                ? state.getBoolean("ordinaryWard")
                : isOrdinaryPhysicalWard(persistent);
    }

    /** Pale Margin is global to every minigame, including special/internal stages. */
    private static void applyPermanentMinigameMargins(LockData data, UUID playerId, CompoundTag state) {
        if (data != null && playerId != null && state != null
                && data.hasUnique(playerId, "minigame_seconds_perm")) {
            state.putInt("seconds", state.getInt("seconds") + 2);
        }
    }

    private static CompoundTag newState(ServerPlayer player, DimSettings settings, BlockPos pos,
                                        RandomizableContainerBlockEntity container) {
        return newState(player, settings, pos, container, ItemStack.EMPTY);
    }

    private static CompoundTag newState(ServerPlayer player, DimSettings settings, BlockPos pos,
                                        RandomizableContainerBlockEntity container,
                                        ItemStack interactionStack) {
        MinecraftServer server = player.getServer();
        CompoundTag state = new CompoundTag();
        state.putInt("progress", 0);
        state.putFloat("memoryDifficulty", 1.0f);
        // Chest value was already persisted when the ward was tagged. Cthulhu's
        // eligibility is decided while constructing a fresh lock state, so keep
        // the value local here instead of relying on the caller's local variable.
        int value = container.getPersistentData().getInt(TAG_VALUE);
        int totalBeaten = server == null ? 0 : LockData.get(server).totalBeaten(player.getUUID());
        bindPendingShardAttunement(player, container);
        if (server != null) {
            LockData threatData = LockData.get(server);
            int threatTier = ChestThreat.bind(player, container, threatData);
            int threatPressure = ChestThreat.boundPressure(player, container, threatData);
            state.putInt("threatTier", threatTier);
            state.putInt("threatPressure", threatPressure);
            if (threatTier >= ChestThreat.Tier.RESTLESS.ordinal() || threatPressure >= 28) {
                ChestThreat.Tier threat = ChestThreat.tier(threatTier);
                WardHud.message(player, Component.literal("WARD PRESSURE // " + threat.title.toUpperCase(java.util.Locale.ROOT)
                                + " " + threatPressure + "/100 · " + threat.hint)
                        .withStyle(threatTier >= ChestThreat.Tier.PROFANE.ordinal() ? ChatFormatting.DARK_PURPLE : ChatFormatting.GRAY), false);
            }
        }

        // Some seals have no maker.
        //
        // Every other ward in this mod was made by somebody with a workshop and
        // a ledger. These were not. They turn up anywhere, rarely, and far more
        // often in the End, which is the only honest place to point at when the
        // question is what has been making them.
        //
        // Mechanically an unsigned seal is the hardest thing the mod can build:
        // every quirk its lock has, every anomaly the chest can carry, all at
        // once. That configuration exists already; nothing here is new content,
        // it is the ceiling of the existing content, and it is worth going and
        // finding out about.
        boolean isEnd = "minecraft:the_end".equals(
                player.level().dimension().location().toString());
        float unsignedOdds = WardConfig.unsignedChance * (isEnd ? WardConfig.unsignedEndFactor : 1f);
        boolean unsigned = container.getPersistentData().getBoolean(TAG_UNSIGNED);
        if (!unsigned && totalBeaten >= WardConfig.unsignedAfterBeaten && RNG.nextFloat() < unsignedOdds) {
            unsigned = true;
            container.getPersistentData().putBoolean(TAG_UNSIGNED, true);
        }
        if (unsigned) WardHistory.discoverAnomaly(player, "unsigned");

        long lockSeed = pos.hashCode() * 31L
                ^ player.getUUID().getLeastSignificantBits() ^ System.nanoTime();
        boolean watchingMarkForced = false;
        String watchingMarkHouse = "";

        // Chains keep their own maker. A Watching Mark may provisionally bind the
        // maker of a signed ward, but it is not consumed until the finished
        // encounter classification proves this is an ordinary physical ward.
        if (server != null && container.getPersistentData().getInt(TAG_CHAIN_LINK) > 0) {
            lockSeed = Chain.seedFor(LockData.get(server).chain(player.getUUID()), lockSeed);
        } else if (server != null && !unsigned) {
            LockData debts = LockData.get(server);
            CompoundTag persistent = container.getPersistentData();
            boolean alreadySpecialOrDepth = isSpecialEncounterWard(persistent) || persistent.getInt(TAG_DEPTH) > 0;
            if (debts.hasWatchingMark(player.getUUID()) && !alreadySpecialOrDepth) {
                watchingMarkHouse = debts.watcher(player.getUUID());
                lockSeed = forceHouse(lockSeed, watchingMarkHouse);
                watchingMarkForced = Sealmakers.house(lockSeed).equals(watchingMarkHouse);
                state.putBoolean("watchingMarkForced", watchingMarkForced);
            } else {
                // Special/depth wards keep their native maker seed. A pending
                // Watching Mark waits for a later ordinary signed ward instead
                // of altering an encounter it is not allowed to consume.
                lockSeed = attend(player, lockSeed);
            }
        } else {
            lockSeed = attend(player, lockSeed);
        }
        state.putLong("seed", lockSeed);
        int directorBand = server == null ? 0 : LockData.get(server).directorBand(player.getUUID(),
                player.level().dimension().location().toString());
        state.putInt("directorBand", directorBand);
        state.putInt("onboardingBeaten", totalBeaten);
        state.putInt("game", directedGame(player, settings, lockSeed, lastGame(container)).ordinal());
        if (container.getPersistentData().getBoolean(TAG_ECHO_SHARD)) {
            MinigameType echoGame = directedNovelGame(player, settings, lockSeed, lastGame(container));
            state.putInt("game", echoGame.ordinal());
            state.putFloat("cardLootBonus", state.getFloat("cardLootBonus") + 0.10f);
            WardHud.message(player, Component.literal("The echo rejects the last few patterns. This seal answers with a less familiar discipline.")
                    .withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC), false);
        }
        WardHistory.discoverGame(player, MinigameType.byOrdinal(state.getInt("game")));
        if (!unsigned) {
            MasterSignature signature = MasterSignature.ofSeed(lockSeed);
            int relationNow = server == null ? 0 : LockData.get(server).relation(player.getUUID(), signature);
            WardHistory.discoverMaster(player, signature, relationNow);
            state.putInt("masterRelation", relationNow);
            if (container.getPersistentData().getBoolean(TAG_ECHO_SHARD)
                    && LockData.get(server).masterPact(player.getUUID()) == signature.ordinal()) {
                int resonant = LockData.get(server).addRelation(player.getUUID(), signature, 1);
                state.putInt("masterRelation", resonant);
                WardHud.message(player, Component.literal("The Echo Shard recognizes the private law in this maker's hand.")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC), false);
            }
        }

        CompoundTag wardMemory = container.getPersistentData();
        CompoundTag playerMemory = chestMemory(wardMemory, player.getUUID());
        {
            int rememberedWins = playerMemory.getInt("wins");
            int rememberedLosses = playerMemory.getInt("losses");
            int rememberedStreak = playerMemory.getInt("streak");
            if (rememberedWins + rememberedLosses > 0) {
                state.putBoolean("recognizedMemory", true);
                if (rememberedStreak >= 3 || rememberedWins > rememberedLosses + 1) {
                    float memoryScale = 1.05f + Math.min(0.05f, Math.max(0, rememberedStreak - 2) * 0.01f);
                    state.putFloat("memoryDifficulty", memoryScale);
                    state.putFloat("cardLootBonus", state.getFloat("cardLootBonus") + 0.08f + Math.min(0.04f, Math.max(0, rememberedStreak - 2) * 0.01f));
                    WardHud.message(player, Component.literal("The seal remembers the way you beat it. It has revised the lesson"
                                    + (rememberedStreak >= 4 ? " again." : "."))
                            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
                } else if (rememberedStreak <= -2 || rememberedLosses >= rememberedWins) {
                    float memoryScale = 0.95f - Math.min(0.05f, Math.max(0, -rememberedStreak - 1) * 0.01f);
                    state.putFloat("memoryDifficulty", memoryScale);
                    WardHud.message(player, Component.literal(rememberedStreak <= -4
                                    ? "The seal has seen you fail here often enough to become careless. More than one tell is exposed."
                                    : "The seal remembers where it stopped you. One tell has been left exposed." )
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
                }
            }
        }

        // A physical ward may acquire a persistent affliction. Once named, it stays
        // with this chest through retries/reseals until the ward is finally cleared.
        float directorWeirdness = directorBand > 0 ? 1.35f : directorBand < 0 ? 0.82f : 1f;
        if (WardConfig.wardAfflictionsEnabled && totalBeaten >= WardConfig.afflictionAfterBeaten
                && !container.getPersistentData().contains(TAG_AFFLICTION)
                && RNG.nextFloat() < Math.min(1f, WardConfig.wardAfflictionChance * directorWeirdness)) {
            container.getPersistentData().putInt(TAG_AFFLICTION, 1 + RNG.nextInt(5));
        }
        // Old mutated wards occasionally become living things. This is persistent
        // and therefore a property of the chest, not a rerolled attempt modifier.
        if (WardConfig.livingWardsEnabled && !container.getPersistentData().getBoolean(TAG_LIVING)
                && mutationLevel(container, totalBeaten) >= 2 && RNG.nextFloat() < Math.min(1f, WardConfig.livingWardChance * directorWeirdness)) {
            container.getPersistentData().putBoolean(TAG_LIVING, true);
            WardHistory.discoverAnomaly(player, "living");
        }
        // Mercy only appears after repeated defeats and never on elite/corrupted
        // sequences. It makes the retry easier but pays a little less.
        if (container.getPersistentData().getInt(TAG_SPITE) >= WardConfig.mercyAfterLosses
                && !container.getPersistentData().getBoolean(TAG_UNSIGNED)
                && !container.getPersistentData().getBoolean(TAG_CTHULHU)
                && container.getPersistentData().getInt(TAG_CHAIN_LINK) == 0
                && container.getPersistentData().getInt(TAG_DEPTH) == 0
                && container.getPersistentData().getInt(TAG_GAUNTLET) == 0
                && container.getPersistentData().getInt(TAG_ELDRITCH) == 0
                && !container.getPersistentData().getBoolean(TAG_POSSESSED)
                && RNG.nextFloat() < WardConfig.mercyChance) {
            state.putBoolean("mercy", true);
            state.putInt("lives", settings.lives + 1);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal("For once, the ward loosens its hand.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
        }

        // Roll the singular Cthulhu encounter before any consumable aid is
        // charged. This prevents a Cold Shim from silently skipping phase one
        // (and prevents the player losing a Shim/Ember on a ward that refuses
        // ordinary shortcuts).
        if (WardConfig.cthulhuWardsEnabled
                && totalBeaten >= WardConfig.cthulhuAfterBeaten
                && !container.getPersistentData().getBoolean(TAG_CTHULHU)
                && !state.getBoolean("mercy")
                && value >= WardConfig.cthulhuWardMinValue
                && !container.getPersistentData().getBoolean(TAG_UNSIGNED)
                && container.getPersistentData().getInt(TAG_GAUNTLET) == 0
                && container.getPersistentData().getInt(TAG_CHAIN_LINK) == 0
                && container.getPersistentData().getInt(TAG_DEPTH) == 0
                && RNG.nextFloat() < WardConfig.cthulhuWardChance) {
            container.getPersistentData().putBoolean(TAG_CTHULHU, true);
            WardHistory.discoverAnomaly(player, "cthulhu");
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "The seal opens one wet eye. It wants something longer.")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
        }
        boolean cthulhuWard = container.getPersistentData().getBoolean(TAG_CTHULHU);
        if (cthulhuWard) {
            state.putInt("game", MinigameType.CTHULHUS_GAME.ordinal());
            state.putInt("progress", 0);
        }

        // At strong relationship extremes a maker occasionally addresses the
        // player personally. Favour exposes one tell; resentment corrupts the
        // seal. These are encounter changes, not permanent stat bonuses.
        if (!cthulhuWard && !unsigned && server != null && container.getPersistentData().getInt(TAG_CHAIN_LINK) == 0) {
            int relationNow = state.getInt("masterRelation");
            String makerName = Sealmakers.house(lockSeed);
            if (relationNow >= 12 && RNG.nextFloat() < 0.09f) {
                state.putInt("progress", Math.max(1, state.getInt("progress")));
                state.putFloat("cardLootBonus", state.getFloat("cardLootBonus") + 0.05f);
                WardHud.message(player, Component.literal(makerName + " leaves one answer visible. That is not the same thing as mercy.")
                        .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC), false);
            } else if (relationNow <= -12
                    && !state.getBoolean("mercy")
                    && container.getPersistentData().getInt(TAG_DEPTH) == 0
                    && totalBeaten >= WardConfig.possessedAfterBeaten
                    && RNG.nextFloat() < 0.08f
                    && container.getPersistentData().getInt(TAG_ELDRITCH) == 0) {
                container.getPersistentData().putBoolean(TAG_POSSESSED, true);
                WardHistory.discoverAnomaly(player, "possessed");
                WardHud.message(player, Component.literal(makerName + " appears to have made this one with you in mind.")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), false);
            }
        }

        int favourBonus = server != null
                && "favour".equals(LockData.get(server).verdict(player.getUUID())) ? 1 : 0;

        // Finalize special encounter classification before charging any item or
        // consuming any "next ordinary ward" promise. This ordering is important:
        // once an item/debt is charged it cannot be reconstructed safely if this
        // same physical seal later upgrades itself into a special encounter.

        // The gauntlet is an unsigned endgame sequence and explicitly refuses
        // carried aids. Bind it before charm/Ember/Shim selection so "nothing you
        // are carrying will help" is mechanically true, not only presentation.
        boolean gauntletWard = false;
        if (container.getPersistentData().getBoolean(TAG_UNSIGNED) && server != null) {
            LockData ld = LockData.get(server);
            if (ld.unsignedBeaten(player.getUUID()) >= WardConfig.gauntletAfter) {
                container.getPersistentData().putInt(TAG_GAUNTLET, WardConfig.gauntletStages);
                WardHistory.discoverAnomaly(player, "gauntlet");
                gauntletWard = true;
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                                "Three of them, one after another, and nothing you are carrying "
                                        + "will help.")
                        .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD), false);
            }
        }

        // Rare elite wards: a premium multi-seal collapse even on signed work.
        // Classification happens before item binding because Eldritch encounters
        // already refuse charms; Ember/Shim now follow that same special-rule
        // boundary instead of being consumed on stage one by accident.
        if (WardConfig.eldritchWardsEnabled
                && totalBeaten >= WardConfig.eldritchAfterBeaten
                && !container.getPersistentData().getBoolean(TAG_CTHULHU)
                && !container.getPersistentData().getBoolean(TAG_POSSESSED)
                && !state.getBoolean("mercy")
                && !container.getPersistentData().getBoolean(TAG_UNSIGNED)
                && container.getPersistentData().getInt(TAG_GAUNTLET) == 0
                && container.getPersistentData().getInt(TAG_CHAIN_LINK) == 0
                && container.getPersistentData().getInt(TAG_DEPTH) == 0
                && RNG.nextFloat() < WardConfig.eldritchWardChance) {
            container.getPersistentData().putInt(TAG_ELDRITCH, WardConfig.eldritchWardStages);
            container.getPersistentData().putInt(TAG_ELDRITCH + "Total", WardConfig.eldritchWardStages);
            container.getPersistentData().putLong(TAG_ELDRITCH_USED, 1L << state.getInt("game"));
            WardHistory.discoverAnomaly(player, "eldritch");
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "The seal twitches, splits, and begins to rewrite itself.")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
        }
        boolean eldritchWard = container.getPersistentData().getInt(TAG_ELDRITCH) > 0;

        // A possessed ward is a single corrupted lock: rarer than ordinary quirks,
        // but much smaller than an Eldritch chain. It never overlaps an Eldritch Ward.
        if (WardConfig.possessedWardsEnabled
                && totalBeaten >= WardConfig.possessedAfterBeaten
                && !container.getPersistentData().getBoolean(TAG_CTHULHU)
                && !container.getPersistentData().getBoolean(TAG_POSSESSED)
                && !state.getBoolean("mercy")
                && container.getPersistentData().getInt(TAG_ELDRITCH) == 0
                && !container.getPersistentData().getBoolean(TAG_UNSIGNED)
                && container.getPersistentData().getInt(TAG_CHAIN_LINK) == 0
                && container.getPersistentData().getInt(TAG_DEPTH) == 0
                && RNG.nextFloat() < Math.min(1f, WardConfig.possessedWardChance * directorWeirdness)) {
            container.getPersistentData().putBoolean(TAG_POSSESSED, true);
            WardHistory.discoverAnomaly(player, "possessed");
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "Something inside the seal moves before you touch it.")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
        }

        boolean ordinaryWard = isOrdinaryPhysicalWard(container.getPersistentData());
        state.putBoolean("ordinaryWard", ordinaryWard);

        // Watching Mark is a promise about the next ordinary signed ward. A
        // special transformation may still have provisionally inherited its
        // maker seed, but it must not spend the mark.
        if (server != null && ordinaryWard && watchingMarkForced) {
            LockData debts = LockData.get(server);
            debts.consumeWatchingMark(player.getUUID());
            state.putBoolean("watchingMarkBound", true);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "The mark is collected. " + watchingMarkHouse + " made this one for you.")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);
        }

        // The charm is chosen only after special classification. Cthulhu,
        // gauntlets and Eldritch chains refuse ordinary carried shortcuts.
        boolean refuseOrdinaryAids = cthulhuWard || gauntletWard || eldritchWard;
        Charm charm = refuseOrdinaryAids ? Charm.NONE
                : Charm.fromInteractionStack(interactionStack, Sealmakers.house(lockSeed));
        state.putInt("lives", settings.lives + charm.bonusLives() + favourBonus
                + (state.getBoolean("mercy") ? 1 : 0));
        state.putString("charm", charm.id);

        // The ember is set down beside the lock and left burning. Charged at
        // binding like everything else, so it cannot be produced halfway through
        // a ward that is running out.
        ItemStack ember = refuseOrdinaryAids ? ItemStack.EMPTY
                : RelicItem.fromInteractionStack(interactionStack, RelicItem.Kind.EMBER);
        if (!ember.isEmpty()) {
            state.putInt("seconds", WardConfig.emberSeconds);
            ember.hurtAndBreak(1, player, pl -> {
            });
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "The ember is still burning. The ward will take longer to close.")
                    .withStyle(ChatFormatting.GOLD), true);
        }

        // The shim is slid in before anything else happens, so the lock is built
        // one stage down. Every screen already restores from a progress value,
        // which is why this works on all ordinary/possessed single-stage locks.
        ItemStack shim = refuseOrdinaryAids ? ItemStack.EMPTY
                : RelicItem.fromInteractionStack(interactionStack, RelicItem.Kind.SHIM);
        if (!shim.isEmpty()) {
            state.putInt("progress", 1);
            shim.hurtAndBreak(1, player, p -> {
            });
            player.level().playSound(null, pos, WardSounds.PIN_SET.get(),
                    SoundSource.BLOCKS, 0.6f, 0.82f);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(shim.isEmpty()
                            ? "The shim goes in, and does not come out."
                            : "The shim goes in. The ward starts a stage down.")
                    .withStyle(ChatFormatting.GRAY), true);
        }

        // The container takes the seal's name and keeps it. Every chest you have
        // ever beaten is titled with the thing you beat, forever, so a base full
        // of them reads as a record of what you have done rather than as
        // storage. Costs one NBT string and nothing else.
        if (container.getCustomName() == null) {
            container.setCustomName(Component.literal(Sealmakers.sealName(lockSeed))
                    .withStyle(ChatFormatting.GOLD));
        }

        // Borrowed Breath is also explicitly "next ordinary ward". Do not collect
        // it on Cthulhu, unsigned/gauntlet, possessed, Eldritch, chain or depth.
        if (server != null && ordinaryWard) {
            LockData debts = LockData.get(server);
            if (debts.consumeBorrowedBreath(player.getUUID())) {
                state.putBoolean("borrowedBreath", true);
                state.putInt("lives", Math.max(1, state.getInt("lives") - 1));
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                                "The breath you borrowed is collected. This ward gives you one fewer life.")
                        .withStyle(ChatFormatting.DARK_PURPLE), false);
            }
        }

        // House Measures deliberately consume only ordinary signed physical wards.
        // Singular encounters, chains, reseals and depth loops stay outside the cadence so
        // the three-step structure reads as a recurring table rule instead of random noise.
        if (server != null
                && ordinaryWard
                && WardMeasureSystem.unlocked(LockData.get(server), player.getUUID())) {
            WardMeasureSystem.prepare(player, LockData.get(server), state, lockSeed);
        }

        // Card wagers bind only to an ordinary ward. Singular/chain endgame
        // locks do not consume a stored wager; it waits for the next ordinary
        // seal so a rare special ward cannot silently eat a two-ward promise.
        if (server != null && ordinaryWard) {
            LockData cardData = LockData.get(server);
            LockData.CardWardTerms cardTerms = cardData.consumeCardWardTerms(player.getUUID());
            if (!cardTerms.empty()) {
                state.putFloat("cardLootBonus", state.getFloat("cardLootBonus") + cardTerms.lootBonus());
                state.putInt("cardLifeDelta", cardTerms.lifeDelta());
                state.putBoolean("cardForceOneLife", cardTerms.forceOneLife());
                state.putInt("cardMinigameMask", cardTerms.minigameMask());
                if (cardTerms.extraSeconds() > 0)
                    state.putInt("seconds", state.getInt("seconds") + cardTerms.extraSeconds());
                if (cardTerms.forceOneLife()) state.putInt("lives", 1);
                else state.putInt("lives", Math.max(1, state.getInt("lives") + cardTerms.lifeDelta()));
                String life = cardTerms.forceOneLife() ? "one life"
                        : (cardTerms.lifeDelta() > 0 ? "+" + cardTerms.lifeDelta() + " life"
                        : cardTerms.lifeDelta() < 0 ? cardTerms.lifeDelta() + " life" : "");
                String loot = Math.abs(cardTerms.lootBonus()) < 0.0001f ? ""
                        : String.format("%+.0f%% loot", cardTerms.lootBonus() * 100f);
                String minigame = MinigameCardEffects.labels(cardTerms.minigameMask());
                if (cardTerms.extraSeconds() > 0)
                    minigame = (minigame.isEmpty() ? "" : minigame + " · ") + "+" + cardTerms.extraSeconds() + "s";
                java.util.ArrayList<String> due = new java.util.ArrayList<>();
                if (!life.isEmpty()) due.add(life);
                if (!loot.isEmpty()) due.add(loot);
                if (!minigame.isEmpty()) due.add(minigame);
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                                "A card comes due: " + String.join("  ·  ", due) + ".")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
            }
            int chain = Math.max(0, cardData.uniqueInt(player.getUUID(), "minigame_seconds_chain"));
            if (chain > 0) {
                state.putInt("seconds", state.getInt("seconds") + 3);
                cardData.setUniqueInt(player.getUUID(), "minigame_seconds_chain", chain - 1);
            }
        }

        // Pale Margin is not an ordinary-ward wager; it is a permanent rule scar.
        // Apply it after all per-ward time changes so every issued minigame sees it.
        if (server != null) {
            applyPermanentMinigameMargins(LockData.get(server), player.getUUID(), state);
        }

        if (charm != Charm.NONE) {
            // Charged here, at the moment of commitment, rather than when the
            // lock resolves. Charging on resolution meant a charm could be bound
            // to any number of chests at once and paid for only on the ones you
            // bothered to finish.
            Charm.spend(player, charm, interactionStack);
            Component note = charm.bindingMessage(Charm.usesLeft(interactionStack, charm));
            if (note != null) dev.marrowseal.wardbound.WardHud.message(player, note, false);
            player.level().playSound(null, pos,
                    (charm == Charm.ICHORHEART ? WardSounds.HEART_USE : WardSounds.KEY_TURN).get(),
                    SoundSource.BLOCKS, 0.7f, 1.0f);
        }
        return state;
    }

    // SaveProgressPacket intentionally has no server mutation path. Ward screens
    // are fail-on-close and may never resurrect or rewrite an issued attempt.

    // ------------------------------------------------------------------ results

    /** Reward curve: how many lives survived plus how cleanly the game was played. */
    public static float successMultiplier(int livesLeft, int maxLives, float performance) {
        float lifeRatio = maxLives <= 0 ? 1f : (float) livesLeft / (float) maxLives;
        float base = 1.0f + WardConfig.successLifeBonusMax * lifeRatio;
        float skill = WardConfig.successSkillBonusMax * Math.max(0f, Math.min(1f, performance));
        return base + skill;
    }

    /** Extra reward for a run of locks taken without a loss in this dimension. */
    public static float streakBonus(int streak) {
        return 1f + Math.min(WardConfig.streakBonusCap,
                Math.max(0, streak) * WardConfig.streakBonusPerLock);
    }

    /** And the matching squeeze: a run that is paying more is also asking more. */
    public static float streakDifficulty(int streak) {
        return 1f + Math.min(WardConfig.streakDifficultyCap,
                Math.max(0, streak) * WardConfig.streakDifficultyPerLock);
    }

    public static void resolveSuccess(ServerPlayer player, BlockPos pos, long resultSeed,
                                      int livesLeft, int maxLives,
                                      float performance, float timeRemainingRatio, float elapsedSeconds,
                                      int mistakes, int latencyMs, String modifiers, int rapidMistakes, int hybridOutcome) {
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockEntity be = serverLevel.getBlockEntity(pos);
        if (!(be instanceof RandomizableContainerBlockEntity container)) return;

        String dimId = serverLevel.dimension().location().toString();
        DimSettings settings = WardConfig.forDimension(dimId);
        MinecraftServer srv = player.getServer();
        LockData runData = srv == null ? null : LockData.get(srv);

        // Result packets are requests, not authority. A debug screen has no live
        // state and an old screen may arrive after this chest has been re-sealed.
        // Both must be inert, otherwise one stale packet can resolve a different ward.
        if (runData == null || !runData.has(player.getUUID(), dimId, pos)) return;
        CompoundTag liveResultState = runData.getOrCreate(player.getUUID(), dimId, pos);
        if (liveResultState.getLong("seed") != resultSeed) return;
        // A seed match alone is not authority: a replayed/forged result can only
        // consume the one attempt the server actually issued to this player.
        if (!liveResultState.getBoolean("attemptIssued")) return;
        // Lives/max-lives are reward-bearing values. They come from the server's
        // issuance snapshot, never from the client result packet. The client may
        // only report that it lost lives, never manufacture more of them.
        int issuedMaxLives = Math.max(1, liveResultState.contains("issuedMaxLives")
                ? liveResultState.getInt("issuedMaxLives") : liveResultState.getInt("lives"));
        int issuedLives = Math.max(0, Math.min(issuedMaxLives, liveResultState.contains("issuedLives")
                ? liveResultState.getInt("issuedLives") : liveResultState.getInt("lives")));
        maxLives = issuedMaxLives;
        livesLeft = Math.max(0, Math.min(issuedLives, livesLeft));
        liveResultState.putBoolean("attemptIssued", false);
        runData.put(player.getUUID(), dimId, pos, liveResultState);

        // Which lock this actually was, and which charm was riding on it. Both
        // have to be read before the per-chest state is wiped further down.
        MinigameType game = settings.minigame;
        Charm charm = Charm.NONE;
        long lockSeed = 0L;
        if (runData != null && runData.has(player.getUUID(), dimId, pos)) {
            CompoundTag state = runData.getOrCreate(player.getUUID(), dimId, pos);
            lockSeed = state.getLong("seed");
            game = state.contains("game") ? MinigameType.byOrdinal(state.getInt("game"))
                    : directedGame(player, settings, lockSeed, lastGame(container));
            charm = Charm.byId(state.getString("charm"));
        }

        // Capture every active modifier before any success path clears it. One
        // RewardBreakdown then feeds both the chest and the message, so preview
        // and payout cannot quietly diverge again.
        int priorRun = runData == null ? 0 : runData.streak(player.getUUID(), dimId);
        int tier = runData == null ? 0 : tierOf(runData.totalBeaten(player.getUUID()));
        int streak = priorRun;
        if (runData != null) {
            runData.recordGame(player.getUUID(), dimId, game);
            runData.recordDirectorOutcome(player.getUUID(), dimId, true, mistakes, performance);
        }

        CompoundTag persistent = container.getPersistentData();
        int depth = persistent.getInt(TAG_DEPTH);
        boolean wasUnsigned = persistent.getBoolean(TAG_UNSIGNED);
        int chainLinkAtSuccess = persistent.getInt(TAG_CHAIN_LINK);
        int spiteAtSuccess = persistent.getInt(TAG_SPITE);
        int stagesLeft = persistent.getInt(TAG_GAUNTLET);
        boolean gauntletFinal = stagesLeft == 1;
        int eldritchLeft = persistent.getInt(TAG_ELDRITCH);
        boolean eldritchActive = eldritchLeft > 0;
        boolean eldritchFinal = eldritchLeft == 1;
        boolean possessed = persistent.getBoolean(TAG_POSSESSED);
        boolean living = persistent.getBoolean(TAG_LIVING);
        boolean cthulhu = persistent.getBoolean(TAG_CTHULHU);
        boolean ordinaryAtSuccess = isOrdinaryAttempt(liveResultState, persistent);
        boolean specialEncounterAtSuccess = isSpecialEncounterWard(persistent);
        boolean splinterBrace = persistent.getBoolean(TAG_SPLINTER_BRACE);
        boolean shardOvercharge = persistent.getBoolean(TAG_SHARD_OVERCHARGE);
        int affliction = persistent.getInt(TAG_AFFLICTION);
        boolean mercy = runData != null && runData.has(player.getUUID(), dimId, pos)
                && runData.getOrCreate(player.getUUID(), dimId, pos).getBoolean("mercy");
        int mutation = mutationLevel(container, runData == null ? 0 : runData.totalBeaten(player.getUUID()));
        boolean rivalry = !wasUnsigned && runData != null
                && rivalryActive(runData, player.getUUID(), lockSeed);
        String verdict = runData == null ? "" : runData.verdict(player.getUUID());
        boolean watchingMarkProvenanceAllowed = !liveResultState.getBoolean("watchingMarkForced")
                || liveResultState.getBoolean("watchingMarkBound");
        boolean watched = runData != null && lockSeed != 0L
                && watchingMarkProvenanceAllowed
                && Sealmakers.house(lockSeed).equals(runData.watcher(player.getUUID()));
        boolean contempt = watched && "contempt".equals(verdict);
        RewardBreakdown.PerformanceGrade performanceGrade = RewardBreakdown.PerformanceGrade.of(
                livesLeft, maxLives, performance, timeRemainingRatio, mistakes);
        int resultFx = performanceGrade == RewardBreakdown.PerformanceGrade.PERFECT
                ? dev.marrowseal.wardbound.net.WardLodestoneFxPacket.WARD_PERFECT
                : dev.marrowseal.wardbound.net.WardLodestoneFxPacket.WARD_SUCCESS;
        float resultFxScale = performanceGrade == RewardBreakdown.PerformanceGrade.PERFECT ? 1.18f : 0.92f;
        dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(serverLevel,
                new net.minecraft.world.phys.Vec3(pos.getX() + .5, pos.getY() + .72, pos.getZ() + .5), resultFx, resultFxScale);
        MinigameCorruption.Variant corruptionAtSuccess = MinigameCorruption.variant(game, liveResultState.getInt("corruptionVariant"));
        MinigameMastery.record(player, runData, game, true, performanceGrade, performance, timeRemainingRatio, mistakes, corruptionAtSuccess);
        MinigameSkillProfile.record(player, runData, game, true, performance, timeRemainingRatio,
                mistakes, rapidMistakes, maxLives, hybridOutcome);
        if (ordinaryAtSuccess)
            FifthWaveCardEffects.onWardResolved(player, runData, game, true, performanceGrade);
        if (hybridOutcome != 0)
            HybridRound.recordEncounter(player, runData, HybridRound.byOrdinal(liveResultState.getInt("hybridMode")));
        RewardBreakdown reward = RewardBreakdown.success(game, settings, priorRun, charm, tier, depth,
                chainLinkAtSuccess, spiteAtSuccess, contempt, wasUnsigned, gauntletFinal,
                livesLeft, maxLives, performance, performanceGrade);
        float total = reward.total;
        float extraRaw = total
                * (eldritchFinal ? WardConfig.eldritchWardLoot : 1f)
                * (possessed ? WardConfig.possessedWardLoot : 1f)
                * (living ? WardConfig.livingWardLoot : 1f)
                * (cthulhu ? WardConfig.cthulhuWardLoot : 1f)
                * (splinterBrace ? SPLINTER_LOOT : 1f)
                * (mercy ? WardConfig.mercyLoot : 1f)
                * (rivalry ? WardConfig.masterRivalryLoot : 1f)
                * ChestThreat.lootScaleForPressure(runData == null ? 0 : ChestThreat.boundPressure(player, container, runData))
                * (runData == null ? 1f : AttentionSystem.lootScale(AttentionSystem.current(runData, player.getUUID())))
                * (runData == null ? 1f : PlayerImprint.lootScale(runData, player.getUUID()))
                * (1f + liveResultState.getFloat("cardLootBonus"))
                * (1f + MinigameCorruption.rewardBonus(corruptionAtSuccess))
                * (hybridOutcome == 1 ? 1f + WardConfig.hybridSuccessLootBonus : 1f)
                * oldWardLoot(container);
        total = RewardBreakdown.clamp(extraRaw);
        int resolvedForLoot = runData == null ? 0 : runData.totalBeaten(player.getUUID());
        float progressionCap = progressionLootCap(resolvedForLoot);
        total = Math.min(total, progressionCap);
        boolean cappedByExtras = extraRaw > total + 0.0001f;

        persistent.putFloat(TAG_MULT, total);
        persistent.putUUID(TAG_WHO, player.getUUID());
        persistent.putInt(TAG_LAST_GAME, game.ordinal());
        // ---- an Eldritch Ward stage that is not the last one does not open anything
        if (eldritchLeft > 1) {
            persistent.putInt(TAG_ELDRITCH, eldritchLeft - 1);
            persistent.remove(TAG_MULT);
            persistent.remove(TAG_WHO);
            persistent.putBoolean(TAG_LOCKED, true);
            container.setChanged();

            if (runData != null) {
                CompoundTag next = new CompoundTag();
                next.putInt("lives", Math.max(1, livesLeft));
                next.putInt("progress", 0);
                next.putString("charm", Charm.NONE.id);
                next.putBoolean("ordinaryWard", false);
                next.putLong("seed", lockSeed * 41L + eldritchLeft * 11939L ^ serverLevel.getGameTime());
                long usedGames = persistent.getLong(TAG_ELDRITCH_USED) | (1L << game.ordinal());
                MinigameType nextGame = directedEldritchGame(player, settings, next.getLong("seed"), game, usedGames);
                next.putInt("game", nextGame.ordinal());
                persistent.putLong(TAG_ELDRITCH_USED, usedGames | (1L << nextGame.ordinal()));
                applyPermanentMinigameMargins(runData, player.getUUID(), next);
                runData.put(player.getUUID(), dimId, pos, next);
                persistent.putInt(TAG_LAST_GAME, game.ordinal());
            }

            serverLevel.playSound(null, pos, WardSounds.EYE_OPEN.get(), SoundSource.BLOCKS,
                    0.95f, 0.72f + (persistent.getInt(TAG_ELDRITCH + "Total") - eldritchLeft) * 0.08f);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "The ward convulses and opens into another mouth. " + (eldritchLeft - 1) + " remain.")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);
            float stageDifficulty = settings.difficulty * WardConfig.tuning(game).difficultyScale
                    * Math.max(streakDifficulty(priorRun),
                            1f + Math.max(0, depth) * WardConfig.temptDifficultyPerDepth)
                    * tierDifficulty(tier)
                    * (1f + chainLinkAtSuccess * WardConfig.chainDifficultyPerLink)
                    * (1f + spiteAtSuccess * WardConfig.spiteDifficulty)
                    * (contempt ? WardConfig.contemptDifficulty : 1f)
                    * 1.08f;
            WardTelemetry.record(dimId, game, true, stageDifficulty, livesLeft, maxLives, performance,
                    timeRemainingRatio, elapsedSeconds, mistakes, latencyMs, modifiers,
                    wasUnsigned, chainLinkAtSuccess, spiteAtSuccess, depth, performanceGrade.name(), 0f);
            openMinigame(player, pos, container);
            return;
        }
        if (eldritchFinal) {
            persistent.remove(TAG_ELDRITCH);
            persistent.remove(TAG_ELDRITCH + "Total");
            persistent.remove(TAG_ELDRITCH_USED);
            serverLevel.playSound(null, pos, WardSounds.EYE_CHOOSE.get(), SoundSource.BLOCKS, 1.0f, 0.78f);
        }
        // ---- a gauntlet stage that is not the last one does not open anything
        if (stagesLeft > 1) {
            persistent.putInt(TAG_GAUNTLET, stagesLeft - 1);
            persistent.remove(TAG_MULT);
            persistent.remove(TAG_WHO);
            persistent.putBoolean(TAG_LOCKED, true);
            container.setChanged();

            if (runData != null) {
                // A fresh lock, a different game, and whatever lives you have
                // left. Carrying the pool across is the whole shape of the
                // thing: the third seal is hard because of what the first two
                // took, not because it is harder in itself.
                CompoundTag next = new CompoundTag();
                next.putInt("lives", Math.max(1, livesLeft));
                next.putInt("progress", 0);
                next.putString("charm", Charm.NONE.id);
                next.putBoolean("ordinaryWard", false);
                next.putLong("seed", lockSeed * 31L + stagesLeft * 7919L
                        ^ serverLevel.getGameTime());
                next.putInt("game", directedGame(player, settings, next.getLong("seed"), game).ordinal());
                applyPermanentMinigameMargins(runData, player.getUUID(), next);
                runData.put(player.getUUID(), dimId, pos, next);
                persistent.putInt(TAG_LAST_GAME, game.ordinal());
            }

            serverLevel.playSound(null, pos, WardSounds.WARD_SEAL.get(), SoundSource.BLOCKS,
                    0.9f, 1.0f + (WardConfig.gauntletStages - stagesLeft) * 0.12f);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "One down. " + (stagesLeft - 1) + " to go, and it is not letting go.")
                    .withStyle(ChatFormatting.DARK_AQUA), false);
            float stageDifficulty = settings.difficulty * WardConfig.tuning(game).difficultyScale
                    * Math.max(streakDifficulty(priorRun),
                            1f + Math.max(0, depth) * WardConfig.temptDifficultyPerDepth)
                    * tierDifficulty(tier)
                    * (1f + chainLinkAtSuccess * WardConfig.chainDifficultyPerLink)
                    * (1f + spiteAtSuccess * WardConfig.spiteDifficulty)
                    * (contempt ? WardConfig.contemptDifficulty : 1f);
            // Intermediate gauntlet stages bank no loot. They still belong in
            // telemetry or the hardest part of a gauntlet would be invisible.
            WardTelemetry.record(dimId, game, true, stageDifficulty, livesLeft, maxLives, performance,
                    timeRemainingRatio, elapsedSeconds, mistakes, latencyMs, modifiers,
                    wasUnsigned, chainLinkAtSuccess, spiteAtSuccess, depth, performanceGrade.name(), 0f);
            openMinigame(player, pos, container);
            return;
        }

        if (stagesLeft == 1) {
            persistent.remove(TAG_GAUNTLET);
            int done = runData == null ? 1 : runData.bumpGauntlets(player.getUUID());
            if (runData != null && WardConfig.attentionEnabled
                    && !runData.released(player.getUUID())
                    && runData.attention(player.getUUID()) == 0) {
                AttentionSystem.set(player, runData, WardConfig.attentionStart, "the completed gauntlet returned its count");
                WardHistory.discoverAnomaly(player, "attention");
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                                "Something has been counting, and it has your number now.")
                        .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD), false);
            }
            serverLevel.playSound(null, pos, WardSounds.WARD_BREAK.get(), SoundSource.BLOCKS,
                    1.0f, 0.85f);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(done == 1
                            ? "All three. Whatever makes these will have to make something else."
                            : "All three again. That is " + done + ".")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        }

        // The physical ward is now authoritatively resolved. Commit progression here,
        // after all internal Eldritch/Gauntlet stages have returned but before any
        // post-resolution table/chain logic asks whether a threshold has been crossed.
        // Reward math above deliberately used resolvedForLoot (the pre-resolution count),
        // so crossing a milestone cannot retroactively increase this ward's payout.
        int totalBeforeResolution = runData == null ? resolvedForLoot : runData.totalBeaten(player.getUUID());
        int totalAfterResolution = totalBeforeResolution;
        if (runData != null && lockSeed != 0L)
            totalAfterResolution = runData.bumpTotal(player.getUUID());

        if (hybridOutcome == 1) WardAdvancements.hybridCleared(player);

        if (runData != null && !wasUnsigned && lockSeed != 0L) {
            MasterSignature sig = MasterSignature.ofSeed(lockSeed);
            int delta = (mistakes == 0 && performance >= 0.82f) ? 2 : 1;
            int relationNow = runData.addRelation(player.getUUID(), sig, delta);
            WardHistory.discoverMaster(player, sig, relationNow);
        }
        if (cthulhu || game == MinigameType.CTHULHUS_GAME) WardHistory.discoverAnomaly(player, "cthulhu_completed");

        // This physical ward now remembers the player who resolved it.
        prepareChestMemory(persistent, player.getUUID());
        CompoundTag successMemory = chestMemory(persistent, player.getUUID());
        successMemory.putInt("wins", Math.min(99, successMemory.getInt("wins") + 1));
        int memoryStreak = successMemory.getInt("streak");
        successMemory.putInt("streak", memoryStreak > 0 ? Math.min(12, memoryStreak + 1) : 1);
        saveChestMemory(persistent, player.getUUID(), successMemory);
        SavantMemory.onChestMemory(player, successMemory.getInt("wins"), successMemory.getInt("losses"),
                successMemory.getInt("streak"), true);
        if (runData != null) {
            int threatAtSuccess = ChestThreat.bound(player, container, runData);
            SavantMemory.onWardSuccess(player, game, performanceGrade);
            OccultChainEvents.onChestResolved(player, runData, threatAtSuccess, shardOvercharge, true);
            if (threatAtSuccess >= ChestThreat.Tier.PROFANE.ordinal())
                PlayerImprint.add(player, runData, PlayerImprint.Trace.GREEDY, 1, "resolved a profane-or-worse seal instead of reducing its risk");
        }

        // A chained ward is still one physical ward for streak purposes. The
        // old placement bumped the streak once per internal Eldritch/Gauntlet
        // screen, so a three-stage ward silently counted as three consecutive
        // chest wins and inflated its own future streak rewards.
        if (runData != null) streak = runData.bumpStreak(player.getUUID(), dimId);

        // History counts completed physical wards, not each internal phase of an
        // Eldritch/Gauntlet chain. Keeping this after the intermediate returns
        // also keeps the old-ward loot preview and final payout in sync.
        persistent.putInt(TAG_SCAR_BREAKS,
                Math.min(12, persistent.getInt(TAG_SCAR_BREAKS) + 1));
        if (runData != null) DeathResonance.relieve(player, runData, 1, "a physical ward was resolved");

        if (runData != null && runData.attention(player.getUUID()) > 0) {
            AttentionSystem.Stage stageBeforeWin = AttentionSystem.current(runData, player.getUUID());
            int relief = AttentionSystem.reliefForResolution(stageBeforeWin, performanceGrade);
            int left = Math.max(0, runData.attention(player.getUUID()) - relief);
            if (relief > 0) AttentionSystem.set(player, runData, left,
                    performanceGrade == RewardBreakdown.PerformanceGrade.PERFECT ? "a perfect ward resolution broke the pattern"
                            : performanceGrade == RewardBreakdown.PerformanceGrade.CLEAN ? "a clean ward resolution reduced the pressure"
                            : "a scraped win shook only early observation");

            if (left <= 0) {
                runData.setReleased(player.getUUID());
                serverLevel.playSound(null, pos, WardSounds.WARD_BREAK.get(),
                        SoundSource.BLOCKS, 1.0f, 0.7f);
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                                "It stops. Your chests are your own again, and nothing "
                                        + "you open will be signed by it.")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
            } else {
                // Winning is not a reprieve while this is going on.
                if (RNG.nextFloat() < WardConfig.attentionHordeChance * AttentionSystem.hordeScale(stageBeforeWin)) {
                    Guardians.spawn(serverLevel, pos, dimId,
                            persistent.getInt(TAG_VALUE), player, true, ChestThreat.bound(player, container, runData));
                    dev.marrowseal.wardbound.WardHud.message(player, Component.literal("It sends more.")
                            .withStyle(ChatFormatting.DARK_RED), false);
                }
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(left + " left of its attention.")
                        .withStyle(ChatFormatting.DARK_AQUA), true);
            }
        }

        if (persistent.getBoolean(TAG_UNSIGNED)) {
            persistent.remove(TAG_UNSIGNED);
            if (runData != null) {
                int n = runData.bumpUnsigned(player.getUUID());
                if (n == WardConfig.gauntletAfter) {
                    dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                                    "That is " + n + " of them. The next one will not be one lock.")
                            .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC), false);
                }
            }
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "It opens. Nothing signed it, and nothing claims it now.")
                    .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC), false);
        }

        if (game == MinigameType.CTHULHUS_GAME) {
            ItemStack idol = new ItemStack(dev.marrowseal.wardbound.item.WardItems.CTHULHU_IDOL.get());
            if (!player.addItem(idol)) player.drop(idol, false);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "Cthulhu's Game yields a still-living idol.")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
            serverLevel.playSound(null, pos, WardSounds.EYE_CHOOSE.get(), SoundSource.BLOCKS, 0.95f, 0.66f);
        }

        // Success relics are one-shot loot-table hints consumed by WardLootModifier.
        // Perfect ordinary work may chip a splinter; a perfect Eldritch finale has
        // a much stronger chance to leave an Eldritch shard.
        if (performanceGrade == RewardBreakdown.PerformanceGrade.PERFECT) {
            if (eldritchFinal && RNG.nextFloat() < WardConfig.eldritchRelicChance) persistent.putInt(TAG_RELIC_GRADE, 2);
            else if (ordinaryAtSuccess && RNG.nextFloat() < WardConfig.perfectRelicChance) persistent.putInt(TAG_RELIC_GRADE, 1);
        }

        // A completed ordinary ward advances the three-round House Measure before
        // a post-ward hand is generated. If Verdict earns a Cut, that leverage is
        // therefore visible immediately on the hand produced by the same resolution.
        if (runData != null && ordinaryAtSuccess)
            WardMeasureSystem.recordSuccess(player, runData, liveResultState, performanceGrade);

        BargainDeal bargainDeal = BargainDeal.empty();
        float bargainProgression = CardBalance.bargainProgression(totalAfterResolution);
        boolean canBargain = WardConfig.forbiddenBargainsEnabled
                && runData != null
                && ordinaryAtSuccess
                && totalAfterResolution >= WardConfig.normalCardsAfterBeaten
                && depth == 0
                && stagesLeft == 0
                && !eldritchFinal
                && !cthulhu
                && !wasUnsigned
                && chainLinkAtSuccess == 0
                && total < WardConfig.maxLootMultiplier - 0.01f
                && RNG.nextFloat() < WardConfig.forbiddenBargainChance * bargainProgression
                        * (performanceGrade == RewardBreakdown.PerformanceGrade.PERFECT ? 1.35f
                        : performanceGrade == RewardBreakdown.PerformanceGrade.CLEAN ? 1.0f : 0.65f);
        if (canBargain) {
            bargainDeal = chooseBargainDeal(runData, player.getUUID(), lockSeed, false);
            bargainDeal = applyQueuedDeckModifiers(runData, player.getUUID(), bargainDeal, lockSeed, false);
            bargainDeal = appendAnomalySlot(runData, player.getUUID(), bargainDeal, lockSeed ^ 0xA110A1L, false);
            bargainDeal = appendHandUtilitySlots(runData, player.getUUID(), bargainDeal, lockSeed ^ 0x51DECA7EL, false);
        }
        List<ForbiddenBargain> bargains = bargainDeal.cards();
        boolean bargaining = !bargains.isEmpty();

        // Forbidden bargains and the ordinary re-seal offer never stack. The
        // former is a rare debt; the latter is the familiar greed loop.
        boolean tempting = !bargaining
                && !specialEncounterAtSuccess
                && WardConfig.temptEnabled && depth < WardConfig.temptMaxDepth
                && RNG.nextFloat() < WardConfig.temptChance;
        if (bargaining) {
            persistent.remove(TAG_LOCKED);
            int[] bargainIds = bargains.stream().mapToInt(b -> b.id).toArray();
            persistent.putIntArray(TAG_BARGAIN_OFFERS, bargainIds);
            runData.rememberCardOffers(player.getUUID(), bargainIds);
            CardEcology.onHandDealt(runData, player.getUUID(), bargains);
            persistent.putLong(TAG_BARGAIN_SEED, lockSeed);
            persistent.putInt(TAG_BARGAIN_MODE, bargainDeal.mode());
            persistent.putInt(TAG_BARGAIN_REFRESHED, 0);
            // Keep this ward's reward ceiling stable even though progression has
            // already advanced for post-resolution unlock checks. A card signed
            // now may add loot, but it cannot retroactively make the just-cleared
            // ward use the next progression band's cap.
            persistent.putFloat(TAG_BARGAIN_CAP, progressionCap);
            container.setChanged();
            releaseWardAttempt(container, player.getUUID());
            runData.clear(player.getUUID(), dimId, pos);
        } else if (tempting) {
            // Held open. unlockAndClear strips TAG_VALUE, and a re-seal needs the
            // container to still know what it is worth, so teardown waits until
            // the player actually declines.
            persistent.remove(TAG_LOCKED);
            persistent.putBoolean(TAG_TEMPT, true);
            container.setChanged();
            releaseWardAttempt(container, player.getUUID());
            if (runData != null) runData.clear(player.getUUID(), dimId, pos);
        } else {
            unlockAndClear(serverLevel, pos, container, player.getServer(), dimId);
        }

        serverLevel.playSound(null, pos, WardSounds.WARD_BREAK.get(), SoundSource.BLOCKS,
                0.75f, Math.min(1.4f, 0.98f + streak * 0.035f));

        // Knowing a house's work is knowledge about the house, so this is counted
        // per maker rather than per chest or per dimension.
        if (runData != null && lockSeed != 0L) {
            String house = Sealmakers.house(lockSeed);
            int known = runData.bumpFamiliarity(player.getUUID(), house);
            // A beaten link moves the trail on; anything else may start one.
            if (persistent.getInt(TAG_CHAIN_LINK) > 0) {
                persistent.remove(TAG_CHAIN_LINK);
                Chain.advance(player, runData, dimId, pos);
            } else if (!bargaining && !cthulhu) {
                // A new chain and a forbidden debt never begin on the same win.
                // The singular Cthulhu encounter also ends cleanly instead of
                // immediately turning into another progression prompt.
                Chain.maybeBegin(player, runData, dimId, pos, lockSeed);
            }

            // Progression-gated post-Master content must also unlock when the ward threshold is
            // crossed during this session, not only on login or on the Master victory tick.
            dev.marrowseal.wardbound.boss.MaestroProgression.unlockIfReady(player);
            WardAdvancements.progression(player, totalAfterResolution);
            WardAdvancements.makerKnown(player, known);
            announceTier(player, tierOf(totalBeforeResolution), tierOf(totalAfterResolution));
            announceWardProgressionUnlock(player, totalBeforeResolution, totalAfterResolution);
            String watcher = runData.watcher(player.getUUID());

            if (known == 3 || known == 7 || known == WardConfig.masterSignatureKnowAfter) {
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                                "You are starting to know " + house + "'s work.")
                        .withStyle(ChatFormatting.GRAY), false);
            }
            if (house.equals(watcher)) {
                // Clean work earns most. Leaning on the heart earns nothing:
                // the hand is judging you, not your inventory.
                int earned = switch (performanceGrade) {
                    case PERFECT -> 3;
                    case CLEAN -> 2;
                    case SCRAPED -> 1;
                    default -> 0;
                };
                if (performanceGrade == RewardBreakdown.PerformanceGrade.CLEAN) earned += WardConfig.cleanWinRegardBonus;
                if (performanceGrade == RewardBreakdown.PerformanceGrade.PERFECT) earned += WardConfig.perfectWinRegardBonus;
                if (charm == Charm.ICHORHEART) earned = 0;
                runData.addRegard(player.getUUID(), earned);
                judge(player, runData, house, known);
            }

            // Said twice in a playthrough and never again. The point lands once,
            // and a mod that keeps telling you it is being mysterious is not.
            if (house.equals(watcher) && (totalAfterResolution == 60 || totalAfterResolution == 140)) {
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(totalAfterResolution == 60
                                ? "You have opened a great many of " + house + "'s seals. "
                                        + "You do not recall this many being made."
                                : "Every hand tires. " + house + "'s does not.")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
            }
        }

        dev.marrowseal.wardbound.WardHistory.setLastWardSummary(player, true, game.id,
                String.format("Loot x%.2f · %d/%d lives · %d mistakes%s",
                        total, Math.max(0, livesLeft), Math.max(1, maxLives), Math.max(0, mistakes),
                        performanceGrade == RewardBreakdown.PerformanceGrade.NONE ? "" : " · " + performanceGrade.display(game)));

        String note = lockSeed == 0L
                ? String.format("%s defeated. Loot x%.2f", capitalise(game.id), total)
                : String.format("%s resolved. Loot x%.2f", Sealmakers.sealName(lockSeed), total);
        if (streak > 1) note += String.format("   \u00b7   run of %d (+%d%% next)",
                streak, Math.round((streakBonus(streak) - 1f) * 100f));
        if (performanceGrade != RewardBreakdown.PerformanceGrade.NONE) note += "   ·   " + performanceGrade.display(game);
        if (eldritchFinal) note += "   ·   ELDRITCH";
        if (reward.capped || cappedByExtras) note += "   ·   capped";
        dev.marrowseal.wardbound.WardHud.message(player, Component.literal(note).withStyle(ChatFormatting.GREEN), true);
        if (performanceGrade == RewardBreakdown.PerformanceGrade.PERFECT) {
            serverLevel.playSound(null, pos, WardSounds.MASTER_MOTIF.get(), SoundSource.BLOCKS, 0.78f, 1.08f);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "The ward breaks cleanly, as if it had been waiting for that exact hand.")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC), false);
        }
        if (performanceGrade == RewardBreakdown.PerformanceGrade.PERFECT) {
            WardHistory.recordSpecial(player, "PERFECT", performanceGrade.display(game) + " // " + capitalise(game.id));
        } else if (performanceGrade == RewardBreakdown.PerformanceGrade.SCRAPED) {
            WardHistory.recordSpecial(player, "WARD", "SCRAPED // " + capitalise(game.id) + " · success without margin");
        }

        if (eldritchFinal) queueWinResidue(serverLevel, pos, 3);
        else if (possessed) queueWinResidue(serverLevel, pos, 2);
        else if (performanceGrade == RewardBreakdown.PerformanceGrade.PERFECT) queueWinResidue(serverLevel, pos, 1);
        else queueWinResidue(serverLevel, pos, 0);

        float resolvedDifficulty = settings.difficulty * WardConfig.tuning(game).difficultyScale
                * Math.max(streakDifficulty(priorRun), 1f + Math.max(0, depth) * WardConfig.temptDifficultyPerDepth)
                * tierDifficulty(tier)
                * (1f + chainLinkAtSuccess * WardConfig.chainDifficultyPerLink)
                * (1f + spiteAtSuccess * WardConfig.spiteDifficulty)
                * (contempt ? WardConfig.contemptDifficulty : 1f)
                * (rivalry ? WardConfig.masterRivalryDifficulty : 1f)
                * (1f + mutation * WardConfig.wardMutationDifficultyPerLevel)
                * (eldritchActive ? (eldritchFinal ? 1.12f : 1.06f) : 1f)
                * (possessed ? 1.05f : 1f);
        WardTelemetry.record(dimId, game, true, resolvedDifficulty, livesLeft, maxLives, performance,
                timeRemainingRatio, elapsedSeconds, mistakes, latencyMs, modifiers,
                wasUnsigned, chainLinkAtSuccess, spiteAtSuccess, depth, performanceGrade.name(), total);

        if (tempting) {
            float next = temptBonus(depth + 1) / temptBonus(depth);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(String.format(
                            "The ward can be wound tighter. Sneak-click to re-seal for x%.2f more, "
                                    + "or open it and keep what you have.", next))
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        } else if (bargaining) {
            if (bargainDeal.mode() == BARGAIN_DEATH) {
                serverLevel.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 0.32f, 0.48f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(serverLevel, new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_DEATH, 1.1f);
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                                "The cards arrive smelling of wet earth. One of them already knows how you die.")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), false);
            } else if (bargainDeal.mode() == BARGAIN_CURSE) {
                serverLevel.playSound(null, pos, WardSounds.WARD_FAIL.get(), SoundSource.BLOCKS, 0.58f, 0.54f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(serverLevel, new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_CURSE, 0.9f);
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                                "The hand blackens. There is no refusal written into this deal.")
                        .withStyle(ChatFormatting.DARK_RED), false);
            } else if (bargainDeal.mode() == BARGAIN_EPIC) {
                serverLevel.playSound(null, pos, WardSounds.MASTER_MOTIF.get(), SoundSource.BLOCKS, 0.92f, 0.72f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(serverLevel, new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_MASTER, 0.95f);
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                                "One card is placed apart from the hand. Its ink is older than the rest.")
                        .withStyle(ChatFormatting.GOLD), false);
            } else if (bargainDeal.mode() == BARGAIN_CONTRACT) {
                serverLevel.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.55f, 0.88f);
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                                "A contract hand opens. None of these cards pay until their count is complete.")
                        .withStyle(ChatFormatting.BLUE), false);
            } else if (bargainDeal.mode() == BARGAIN_RITUAL) {
                serverLevel.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.48f, 0.72f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(serverLevel, new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_RITUAL, 0.85f);
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                                "The cards ask for work, not blood. The payment is written underneath.")
                        .withStyle(ChatFormatting.DARK_AQUA), false);
            } else if (bargainDeal.mode() == BARGAIN_COVENANT) {
                serverLevel.playSound(null, pos, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 0.42f, 0.62f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(serverLevel, new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_COVENANT, 0.9f);
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                                "A covenant hand is dealt. The requirements are worse because the payments are better.")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), false);
            } else {
                ForbiddenBargain privateCard = bargains.stream().filter(ForbiddenBargain::isMaster).findFirst().orElse(null);
                if (privateCard != null) {
                    MasterSignature sig = MasterSignature.ofSeed(lockSeed);
                    float pitch = sig == MasterSignature.CROOKED ? 0.82f : sig == MasterSignature.VEILED ? 0.98f : 1.14f;
                    serverLevel.playSound(null, pos, WardSounds.MASTER_MOTIF.get(), SoundSource.BLOCKS, 0.78f, pitch);
                    int masterFx = switch(sig){
                        case CROOKED -> dev.marrowseal.wardbound.net.WardLodestoneFxPacket.MASTER_CROOKED;
                        case VEILED -> dev.marrowseal.wardbound.net.WardLodestoneFxPacket.MASTER_VEILED;
                        case EXACTING -> dev.marrowseal.wardbound.net.WardLodestoneFxPacket.MASTER_EXACTING;
                    };
                    dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(serverLevel, new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), masterFx, sig == MasterSignature.EXACTING ? 1.0f : 0.82f);
                    String house = Sealmakers.house(lockSeed);
                    String privateLine = switch (sig) {
                        case CROOKED -> house + " bends the margin and slides a private law beneath the hand.";
                        case VEILED -> house + " has already placed a card on the table. You do not remember it being dealt.";
                        case EXACTING -> house + " sets one card apart, squared perfectly to the edge.";
                    };
                    dev.marrowseal.wardbound.WardHud.message(player, Component.literal(privateLine)
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC), false);
                }
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                                "Something remains between you and the open chest. Choose a card, or refuse it.")
                        .withStyle(ChatFormatting.DARK_PURPLE), false);
            }
            openBargainScreen(player, pos, container);
        }
    }

    private static final int BARGAIN_NORMAL = 0;
    /** Branch values at/above this are reserved for The Red Pen target selection. */
    public static final int RED_PEN_TARGET_BASE = 1000;
    /** Client action id for spending one banked Cut to redraw a single ordinary-hand offer. */
    public static final int MULLIGAN_ACTION_ID = -2;
    private static final int BARGAIN_EPIC = 1;
    private static final int BARGAIN_CURSE = 2;
    private static final int BARGAIN_UNIQUE = 3;
    private static final int BARGAIN_DEATH = 4;
    private static final int BARGAIN_CONTRACT = 5;
    private static final int BARGAIN_RITUAL = 6;
    private static final int BARGAIN_COVENANT = 7;

    private enum BargainArchetype { BALANCED, WORLD, WAGER, RECOVERY }

    /**
     * Deals are generated in two stages: first the rare deck class is rolled,
     * then a coherent hand is assembled from that class. High-tier cards never
     * leak into ordinary hands, so a normal four-card hand cannot become three
     * curses plus an epic merely because the enum order happened to line up.
     */
    private static BargainDeal chooseBargainDeal(LockData data, UUID player, long seed, boolean afterRefresh) {
        int totalBeaten = data.totalBeaten(player);
        boolean watcherEligible = WardConfig.watcherEnabled && totalBeaten >= WardConfig.watcherAfter;
        Random random = new Random(seed ^ (afterRefresh ? 0x72E13A5BL : 0x5DEECE66DL));
        MasterSignature signature = MasterSignature.ofSeed(seed);

        // These are conditional probabilities after the base bargain roll has
        // already succeeded. Death/Unique/Epic are deliberately much rarer than
        // the alpha prototype; Refresh mostly raises curse/death pressure.
        int resolved = data.totalBeaten(player);
        List<ForbiddenBargain> availableDeathCards = resolved >= WardConfig.deathCardsAfterBeaten
                ? cardsOfKind(data, player, watcherEligible, ForbiddenBargain.Kind.DEATH) : List.of();
        boolean deathHandEligible = !availableDeathCards.isEmpty();
        int deathPity = data.uniqueInt(player, "death_hand_pity");
        if (!afterRefresh && deathHandEligible) {
            deathPity = Math.min(CardBalance.DEATH_PITY_GUARANTEE, deathPity + 1);
            data.setUniqueInt(player, "death_hand_pity", deathPity);
        }
        float deathChance = deathHandEligible
                ? Math.min(1.0f, CardBalance.deathHandChance(deathPity, afterRefresh)
                        + DeathResonance.deathHandBonus(data, player, afterRefresh)) : 0f;
        float curseChance  = resolved >= WardConfig.curseCardsAfterBeaten ? (afterRefresh ? 0.095f : 0.0180f) : 0f;
        float epicChance   = resolved >= WardConfig.epicCardsAfterBeaten ? (afterRefresh ? 0.008f : 0.0090f) : 0f;
        float uniqueChance = resolved >= WardConfig.uniqueCardsAfterBeaten ? (afterRefresh ? 0.0035f : 0.0045f) : 0f;

        // Queued deck laws bend the next table without bypassing shelf unlocks.
        if (data.uniqueInt(player, "deck_curse_damper") > 0) curseChance *= 0.22f;
        if (data.uniqueInt(player, "deck_curse_lull") > 0) curseChance *= 0.35f;
        if (data.uniqueInt(player, "deck_curse_bait") > 0 && curseChance > 0f)
            curseChance = Math.min(0.55f, curseChance * 2.8f + 0.035f);
        if (data.uniqueInt(player, "deck_rare_ink") > 0) {
            epicChance *= 2.25f;
            uniqueChance *= 2.10f;
        }
        int covenantStain = data.uniqueInt(player, "covenant_stain");
        float covenantChance = resolved >= WardConfig.covenantCardsAfterBeaten
                ? (afterRefresh ? 0.0050f : 0.0080f) + Math.min(0.012f, covenantStain * 0.0015f) : 0f;
        if (data.uniqueInt(player, "deck_rare_ink") > 0) covenantChance *= 1.75f;
        float ritualChance = resolved >= WardConfig.ritualCardsAfterBeaten ? (afterRefresh ? 0.026f : 0.040f) : 0f;
        float contractChance = resolved >= WardConfig.contractCardsAfterBeaten ? (afterRefresh ? 0.040f : 0.065f) : 0f;
        float roll = random.nextFloat();

        if (roll < deathChance) {
            List<ForbiddenBargain> death = new ArrayList<>(availableDeathCards);
            shuffleFreshFirst(death, data, player, random);
            if (!death.isEmpty()) {
                data.setUniqueInt(player, "death_hand_pity", 0);
                int count = death.size() >= 4 && random.nextFloat() < 0.22f ? 4 : Math.min(3, death.size());
                if (death.size() == 1) count = 1;
                return new BargainDeal(new ArrayList<>(death.subList(0, count)), BARGAIN_DEATH);
            }
            roll = 1.0f;
        }
        roll -= deathChance;

        if (roll < curseChance) {
            List<ForbiddenBargain> curses = cardsOfKind(data, player, watcherEligible, ForbiddenBargain.Kind.CURSE);
            shuffleFreshFirst(curses, data, player, random);
            if (curses.size() >= 3) {
                int count = curses.size() >= 4 && random.nextFloat() < 0.35f ? 4 : 3;
                return new BargainDeal(new ArrayList<>(curses.subList(0, Math.min(count, curses.size()))), BARGAIN_CURSE);
            }
            roll = 1.0f;
        }
        roll -= curseChance;

        if (roll < epicChance) {
            List<ForbiddenBargain> epics = cardsOfKind(data, player, watcherEligible, ForbiddenBargain.Kind.EPIC);
            shuffleFreshFirst(epics, data, player, random);
            if (!epics.isEmpty()) return new BargainDeal(List.of(epics.get(0)), BARGAIN_EPIC);
            roll = 1.0f;
        }
        roll -= epicChance;

        if (roll < uniqueChance) {
            List<ForbiddenBargain> uniques = cardsOfKind(data, player, watcherEligible, ForbiddenBargain.Kind.UNIQUE);
            shuffleFreshFirst(uniques, data, player, random);
            if (!uniques.isEmpty()) return new BargainDeal(List.of(uniques.get(0)), BARGAIN_UNIQUE);
            roll = 1.0f;
        }
        roll -= uniqueChance;

        if (roll < covenantChance) {
            List<ForbiddenBargain> pool = cardsOfKind(data, player, watcherEligible, ForbiddenBargain.Kind.COVENANT);
            shuffleFreshFirst(pool, data, player, random);
            if (!pool.isEmpty()) return new BargainDeal(new ArrayList<>(pool.subList(0, Math.min(pool.size(), random.nextFloat() < 0.30f ? 3 : 2))), BARGAIN_COVENANT);
            roll = 1.0f;
        }
        roll -= covenantChance;
        if (roll < ritualChance) {
            List<ForbiddenBargain> pool = cardsOfKind(data, player, watcherEligible, ForbiddenBargain.Kind.RITUAL);
            shuffleFreshFirst(pool, data, player, random);
            if (!pool.isEmpty()) return new BargainDeal(new ArrayList<>(pool.subList(0, Math.min(pool.size(), 3))), BARGAIN_RITUAL);
            roll = 1.0f;
        }
        roll -= ritualChance;
        if (roll < contractChance) {
            List<ForbiddenBargain> pool = cardsOfKind(data, player, watcherEligible, ForbiddenBargain.Kind.CONTRACT);
            shuffleFreshFirst(pool, data, player, random);
            if (!pool.isEmpty()) return new BargainDeal(new ArrayList<>(pool.subList(0, Math.min(pool.size(), 3))), BARGAIN_CONTRACT);
            roll = 1.0f;
        }

        List<ForbiddenBargain> remedies = new ArrayList<>();
        List<ForbiddenBargain> wagers = new ArrayList<>();
        List<ForbiddenBargain> burdens = new ArrayList<>();
        List<ForbiddenBargain> world = new ArrayList<>();
        ForbiddenBargain master = null;
        for (ForbiddenBargain b : ForbiddenBargain.values()) {
            if (CardEchoSystem.isPrimer(b) || b == ForbiddenBargain.THE_RED_PEN) continue;
            if (!b.available(data, player, watcherEligible)) continue;
            if (b.kind == ForbiddenBargain.Kind.CURSE || b.kind == ForbiddenBargain.Kind.EPIC
                    || b.kind == ForbiddenBargain.Kind.UNIQUE || b.kind == ForbiddenBargain.Kind.DEATH
                    || b.kind == ForbiddenBargain.Kind.CONTRACT || b.kind == ForbiddenBargain.Kind.RITUAL
                    || b.kind == ForbiddenBargain.Kind.COVENANT || b.kind == ForbiddenBargain.Kind.REFRESH) continue;
            if (b.kind == ForbiddenBargain.Kind.MASTER) {
                if (b.matchesSignature(signature)) master = b;
                continue;
            }
            if (b.kind == ForbiddenBargain.Kind.REMEDY) remedies.add(b);
            else if (b.kind == ForbiddenBargain.Kind.WAGER) wagers.add(b);
            else burdens.add(b);
            if (b.isWorldPlay() && b.kind != ForbiddenBargain.Kind.REMEDY) world.add(b);
        }
        shuffleFreshFirst(remedies, data, player, random);
        shuffleFreshFirst(wagers, data, player, random);
        shuffleFreshFirst(burdens, data, player, random);
        shuffleFreshFirst(world, data, player, random);

        float countRoll = random.nextFloat();
        int wanted = countRoll < 0.10f ? 4 : countRoll < 0.45f ? 3 : 2;
        wanted = Math.max(1, Math.min(wanted, WardConfig.forbiddenBargainMaxOffers));

        float archetypeRoll = random.nextFloat();
        float recoveryCut = (data.hasAnyBargainDebt(player) || FreshCardEffects.hasLesserBurden(data, player) || SecondWaveCardEffects.hasLesserBurden(data, player) || ThirdWaveCardEffects.hasLesserBurden(data, player) || FifthWaveCardEffects.hasLesserBurden(data, player) || ApothicCardEffects.hasLesserBurden(data, player)) ? 0.31f : 0.18f;
        BargainArchetype archetype;
        if (!remedies.isEmpty() && archetypeRoll < recoveryCut) archetype = BargainArchetype.RECOVERY;
        else if (archetypeRoll < 0.53f) archetype = BargainArchetype.WORLD;
        else if (archetypeRoll < 0.78f) archetype = BargainArchetype.WAGER;
        else archetype = BargainArchetype.BALANCED;

        List<ForbiddenBargain> out = new ArrayList<>();
        if (archetype == BargainArchetype.RECOVERY && !remedies.isEmpty()) out.add(remedies.remove(0));
        else if (!remedies.isEmpty() && random.nextFloat() < 0.28f) out.add(remedies.remove(0));

        // A private law is an event, not a routine rarity slot. Relationship
        // raises the chance, but never enough to make it common.
        int relation = data.relation(player, signature);
        float masterChance = Math.max(0.03f, Math.min(0.15f, 0.055f + relation * 0.0025f));
        if (resolved >= WardConfig.masterCardsAfterBeaten && master != null && out.size() < wanted && random.nextFloat() < masterChance) out.add(master);

        if (!afterRefresh && out.size() < wanted && ForbiddenBargain.REFRESH_HAND.available(data, player, watcherEligible)
                && random.nextFloat() < 0.055f) out.add(ForbiddenBargain.REFRESH_HAND);

        List<ForbiddenBargain> preferred = new ArrayList<>();
        if (archetype == BargainArchetype.WORLD) preferred.addAll(world);
        else if (archetype == BargainArchetype.WAGER || archetype == BargainArchetype.RECOVERY) preferred.addAll(wagers);
        else {
            preferred.addAll(wagers);
            preferred.addAll(burdens);
        }
        shuffleFreshFirst(preferred, data, player, random);
        addCoherentCards(out, preferred, wanted);

        List<ForbiddenBargain> fallback = new ArrayList<>();
        fallback.addAll(wagers);
        fallback.addAll(burdens);
        fallback.addAll(remedies);
        shuffleFreshFirst(fallback, data, player, random);
        addCoherentCards(out, fallback, wanted);
        return new BargainDeal(out, BARGAIN_NORMAL);
    }

    private static List<ForbiddenBargain> cardsOfKind(LockData data, UUID player, boolean watcherEligible,
                                                       ForbiddenBargain.Kind kind) {
        List<ForbiddenBargain> out = new ArrayList<>();
        for (ForbiddenBargain b : ForbiddenBargain.values()) {
            if (AnomalyCardSystem.isAnomaly(b) || CardEchoSystem.isPrimer(b) || b == ForbiddenBargain.THE_RED_PEN) continue;
            if (b.kind == kind && b.available(data, player, watcherEligible)) out.add(b);
        }
        return out;
    }

    /** Recently exposed cards remain possible, but they are only consumed after fresh alternatives. */
    private static void shuffleFreshFirst(List<ForbiddenBargain> pool, LockData data, UUID player, Random random) {
        if (pool == null || pool.size() < 2) return;
        List<ForbiddenBargain> fresh = new ArrayList<>();
        List<ForbiddenBargain> recent = new ArrayList<>();
        for (ForbiddenBargain card : pool) (data.wasRecentlyOffered(player, card.id) ? recent : fresh).add(card);
        CardEcology.weightedShuffle(fresh, data, player, random);
        CardEcology.weightedShuffle(recent, data, player, random);
        pool.clear();
        pool.addAll(fresh);
        pool.addAll(recent);
    }

    /** Ordinary hands cap debt/scar density so one theme cannot swallow a deal. */
    private static void addCoherentCards(List<ForbiddenBargain> out, List<ForbiddenBargain> pool, int wanted) {
        int debt = 0, scar = 0;
        for (ForbiddenBargain existing : out) {
            if (existing.kind == ForbiddenBargain.Kind.DEBT) debt++;
            if (existing.kind == ForbiddenBargain.Kind.SCAR) scar++;
        }
        java.util.EnumSet<ExpandedProgressionCardEffects.Family> expandedFamilies = java.util.EnumSet.noneOf(ExpandedProgressionCardEffects.Family.class);
        int expandedCount = 0;
        int expandedCap = wanted >= 4 ? 2 : 1;
        for (ForbiddenBargain existing : out) {
            ExpandedProgressionCardEffects.Family family = ExpandedProgressionCardEffects.family(existing);
            if (family != null) { expandedFamilies.add(family); expandedCount++; }
        }
        for (ForbiddenBargain b : pool) {
            if (out.size() >= wanted) break;
            if (out.contains(b)) continue;
            if (b.kind == ForbiddenBargain.Kind.DEBT && debt >= 1) continue;
            if (b.kind == ForbiddenBargain.Kind.SCAR && scar >= 1) continue;
            ExpandedProgressionCardEffects.Family family = ExpandedProgressionCardEffects.family(b);
            if (family != null && (expandedCount >= expandedCap || expandedFamilies.contains(family))) continue;
            out.add(b);
            if (family != null) { expandedFamilies.add(family); expandedCount++; }
            if (b.kind == ForbiddenBargain.Kind.DEBT) debt++;
            if (b.kind == ForbiddenBargain.Kind.SCAR) scar++;
        }
    }

    private static void resetRefuseStreak(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        LockData.get(player.getServer()).setUniqueInt(player.getUUID(), "card_refuse_streak", 0);
    }

    private static void recordRefuseStreak(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        int streak = data.uniqueInt(id, "card_refuse_streak") + 1;
        data.setUniqueInt(id, "card_refuse_streak", streak);
        if (streak < 50) {
            if (streak == 10) {
                WardHud.message(player, Component.literal("Somewhere, a hand pauses before dealing again.")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
            } else if (streak == 25) {
                WardHud.message(player, Component.literal("The margins have begun to remember every card you leave unsigned.")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
            } else if (streak == 40) {
                WardHud.message(player, Component.literal("A hand somewhere has stopped offering patience.")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), false);
            }
            return;
        }

        data.setUniqueInt(id, "card_refuse_streak", 0);
        data.setUniqueInt(id, "refuse_punishment_stage", 1); // judgement spoken; death follows after a short beat
        data.setUniqueLong(id, "refuse_judgement_at", player.level().getGameTime() + 40L);
        CardMaster master = CardMaster.PALE_GAMBLER;
        String speaker = master.displayName(data, id);
        WardHud.message(player, Component.literal(speaker + ": Do you think you are a hero?")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
        WardHistory.recordSpecial(player, "DANGER", speaker + ": Do you think you are a hero?");
        player.level().playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.80f, 0.52f);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY(0.6), player.getZ(),
                    36, 0.55, 0.85, 0.55, 0.03);
        }
    }

    /** Opens one field-earned card later, outside combat. The offer lives on the player, not a chest. */
    public static boolean openLooseCard(ServerPlayer player, ItemStack stack) {
        if (player == null || player.getServer() == null || !(stack.getItem() instanceof SealedCardItem)) return false;
        CompoundTag pd = loosePlayerData(player);
        if (pd.contains(LOOSE_OFFERS)) {
            sendLooseBargainScreen(player);
            return false;
        }

        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        if (data.totalBeaten(id) < WardConfig.fieldCardAfterBeaten) {
            WardHud.message(player, Component.literal("The seal stays blank. You have not yet lived enough wards for it to answer.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
            return false;
        }
        String dealerId = stack.getOrCreateTag().getString(SealedCardItem.TAG_DEALER);
        CardMaster dealer = dealerId.isBlank() ? CardMaster.choose(data, id, player.getRandom()) : CardMaster.byId(dealerId);
        int openedBefore = data.uniqueInt(id, "field_cards_opened");
        if (openedBefore < dealer.entersPoolAfter) dealer = CardMaster.choose(data, id, player.getRandom());
        long seed = player.level().getGameTime() * 97L + player.getId() * 31L + stack.hashCode();
        BargainDeal deal = chooseLooseCardDeal(data, id, seed, dealer);
        deal = appendAnomalySlot(data, id, deal, seed ^ 0x10A5EEDL, true);
        deal = appendHandUtilitySlots(data, id, deal, seed ^ 0x71A4B11L, true);
        if (deal.cards().isEmpty()) return false;

        // Rivalry interference is a queued event, not a fee for merely trying to
        // open a Sealed Card. Consume it only when the resulting hand actually
        // contains the rivalry's requested shelf. This matters before, for example,
        // the Notary's Contract shelf unlocks: an ordinary fallback hand must not
        // silently erase a counter-offer the player never got to see.
        MasterStory.Interference pendingInterference = MasterStory.peekInterference(data, id, dealer);
        boolean interferenceRepresented = pendingInterference != null
                && deal.cards().stream().anyMatch(c -> c.kind == pendingInterference.kind());
        if (interferenceRepresented) MasterStory.consumeInterference(data, id, dealer);

        if (data.uniqueInt(id, "dealer_fourth") > 0)
            data.setUniqueInt(id, "dealer_fourth", Math.max(0, data.uniqueInt(id, "dealer_fourth") - 1));

        // Progression advances only after a real offer was successfully built.
        // Otherwise a late-game player with no available field cards could spam
        // an unconsumed token to reveal card masters for free.
        int opened = data.uniqueInt(id, "field_cards_opened") + 1;
        data.setUniqueInt(id, "field_cards_opened", opened);
        data.setUniqueInt(id, "dealer_audiences_" + dealer.id, data.uniqueInt(id, "dealer_audiences_" + dealer.id) + 1);
        MasterStory.evaluate(player, data, dealer);
        data.rememberCardOffers(id, deal.cards().stream().mapToInt(c -> c.id).toArray());
        CardEcology.onHandDealt(data, id, deal.cards());
        CardMaster.updateReveals(master -> {
            WardHistory.discoverDealer(player, master);
            WardHud.message(player, Component.literal(master.revealLine())
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
            dev.marrowseal.wardbound.item.CthulhuEyeItem.speakInsight(player, "dealer_reveal_" + master.id,
                    "So the hand has supplied a name. " + master.title + ". Names do not make dealers safer; they merely make them addressable.", 20L * 90L);
            player.level().playSound(null, player.blockPosition(), WardSounds.MASTER_MOTIF.get(), SoundSource.PLAYERS, 0.62f, 0.72f);
        }, data, id);
        pd.putIntArray(LOOSE_OFFERS, deal.cards().stream().mapToInt(c -> c.id).toArray());
        pd.putInt(LOOSE_MODE, deal.mode());
        pd.putLong(LOOSE_SEED, dealer.visualSeed());
        pd.putLong(LOOSE_POS, player.blockPosition().asLong());
        pd.putString(LOOSE_DEALER, dealer.id);
        sendLooseBargainScreen(player);
        return true;
    }

    private static BargainDeal chooseLooseCardDeal(LockData data, UUID player, long seed, CardMaster dealer) {
        boolean watcherEligible = WardConfig.watcherEnabled && data.totalBeaten(player) >= WardConfig.watcherAfter;
        Random random = new Random(seed ^ dealer.id.hashCode());
        float roll = random.nextFloat();
        int resolved = data.totalBeaten(player);
        MasterStory.Interference interference = MasterStory.peekInterference(data, player, dealer);
        ForbiddenBargain.Kind targetKind = interference != null && isLooseKindUnlocked(interference.kind(), resolved)
                ? interference.kind() : chooseLooseTargetKind(dealer, data, player, resolved, roll);
        List<ForbiddenBargain> fresh = new ArrayList<>();
        List<ForbiddenBargain> stale = new ArrayList<>();

        for (ForbiddenBargain b : ForbiddenBargain.values()) {
            if (AnomalyCardSystem.isAnomaly(b) || CardEchoSystem.isPrimer(b) || b == ForbiddenBargain.THE_RED_PEN) continue;
            if (!b.available(data, player, watcherEligible) || b.kind == ForbiddenBargain.Kind.REFRESH) continue;
            if (!isLooseKindUnlocked(b.kind, resolved)) continue;
            if (b.kind == ForbiddenBargain.Kind.MASTER) continue;
            if (targetKind != null) {
                if (b.kind != targetKind) continue;
            } else {
                if (b.kind == ForbiddenBargain.Kind.EPIC || b.kind == ForbiddenBargain.Kind.UNIQUE
                        || b.kind == ForbiddenBargain.Kind.CURSE || b.kind == ForbiddenBargain.Kind.DEATH) continue;
                if (dealer == CardMaster.ASHEN_CURATOR && dealer.grudge(data, player) > 0
                        && b.kind == ForbiddenBargain.Kind.REMEDY && random.nextFloat() < 0.70f) continue;
                if (!dealer.prefers(b) && random.nextFloat() >= 0.24f) continue;
            }
            (data.wasRecentlyOffered(player, b.id) ? stale : fresh).add(b);
        }

        if (fresh.isEmpty() && stale.isEmpty() && targetKind != null) {
            // A rare shelf with no legal card falls back to the dealer's ordinary unlocked shelves.
            for (ForbiddenBargain b : ForbiddenBargain.values()) {
                if (AnomalyCardSystem.isAnomaly(b) || CardEchoSystem.isPrimer(b) || b == ForbiddenBargain.THE_RED_PEN) continue;
                if (!b.available(data, player, watcherEligible) || b.kind == ForbiddenBargain.Kind.MASTER
                        || b.kind == ForbiddenBargain.Kind.REFRESH || !isLooseKindUnlocked(b.kind, resolved)
                        || b.kind == ForbiddenBargain.Kind.EPIC || b.kind == ForbiddenBargain.Kind.UNIQUE
                        || b.kind == ForbiddenBargain.Kind.CURSE || b.kind == ForbiddenBargain.Kind.DEATH) continue;
                if (!dealer.prefers(b) && random.nextFloat() >= 0.30f) continue;
                (data.wasRecentlyOffered(player, b.id) ? stale : fresh).add(b);
            }
        }
        if (fresh.isEmpty() && stale.isEmpty()) return BargainDeal.empty();
        CardEcology.weightedShuffle(fresh, data, player, random);
        CardEcology.weightedShuffle(stale, data, player, random);

        int wanted = dealer.targetHandSize(data, player, random);
        if (data.uniqueInt(player, "dealer_fourth") > 0) wanted++;
        if (progressionExtraOffer(data, player)) wanted++;
        if (targetKind == ForbiddenBargain.Kind.CURSE || targetKind == ForbiddenBargain.Kind.EPIC
                || targetKind == ForbiddenBargain.Kind.UNIQUE || targetKind == ForbiddenBargain.Kind.DEATH) wanted = Math.max(2, wanted);
        wanted = Math.max(1, Math.min(4, wanted));

        List<ForbiddenBargain> offers = new ArrayList<>();
        addDistinct(offers, fresh, wanted);
        addDistinct(offers, stale, wanted);

        // Rivalry is now a mixed hand rather than a silent shelf replacement. The invading
        // master pushes one clause in; the current dealer tries to keep one clause of its own.
        if (interference != null) {
            ForbiddenBargain dealerReply = pickFreshCard(data, player, watcherEligible, resolved, random, offers, dealer::prefers);
            if (dealerReply != null) insertDealerCounterpart(offers, dealerReply, Math.max(2, wanted));
            CardMaster invader = interference.source();
            if (interference.heat() >= 6) {
                ForbiddenBargain rivalClause = pickFreshCard(data, player, watcherEligible, resolved, random, offers, invader::prefers);
                if (rivalClause != null) insertDealerCounterpart(offers, rivalClause, Math.max(3, wanted));
            }
            if (interference.heat() >= 10 && MasterStory.chapter(data, player, invader) >= 6) {
                ForbiddenBargain rivalSignature = invader.signatureCard();
                if (rivalSignature.available(data, player, watcherEligible) && !offers.contains(rivalSignature) && random.nextFloat() < 0.30f)
                    insertDealerCounterpart(offers, rivalSignature, Math.max(3, wanted));
            }
        }

        // The Curator recognizes burden. Once interested, it tries to expose a remedy alongside its preferred damage.
        if (dealer == CardMaster.ASHEN_CURATOR && dealer.relation(data, player) >= 3 && (data.hasAnyBargainDebt(player) || FreshCardEffects.hasLesserBurden(data, player) || SecondWaveCardEffects.hasLesserBurden(data, player) || ThirdWaveCardEffects.hasLesserBurden(data, player) || FifthWaveCardEffects.hasLesserBurden(data, player) || ApothicCardEffects.hasLesserBurden(data, player))) {
            ForbiddenBargain remedy = pickFreshCard(data, player, watcherEligible, resolved, random, offers,
                    b -> b.kind == ForbiddenBargain.Kind.REMEDY);
            if (remedy != null) insertDealerCounterpart(offers, remedy, wanted);
        }

        // The Notary prefers an actionable obligation when the player's ledger still has room for one.
        if (dealer == CardMaster.MOURNING_NOTARY && dealer.relation(data, player) >= 3 && data.activeObjectiveCount(player) < 3) {
            ForbiddenBargain objective = pickFreshCard(data, player, watcherEligible, resolved, random, offers,
                    ForbiddenBargain::isObjectiveCard);
            if (objective != null && (targetKind == null || targetKind == ForbiddenBargain.Kind.CONTRACT
                    || targetKind == ForbiddenBargain.Kind.RITUAL || targetKind == ForbiddenBargain.Kind.COVENANT))
                insertDealerCounterpart(offers, objective, wanted);
        }

        // The Gambler likes a dangerous card to have a tempting, spendable alternative rather than a second copy of the same mood.
        boolean risky = targetKind == ForbiddenBargain.Kind.CURSE || targetKind == ForbiddenBargain.Kind.EPIC
                || targetKind == ForbiddenBargain.Kind.UNIQUE || targetKind == ForbiddenBargain.Kind.DEATH;
        if (dealer == CardMaster.PALE_GAMBLER && risky && offers.size() >= 1) {
            ForbiddenBargain wager = pickFreshCard(data, player, watcherEligible, resolved, random, offers,
                    b -> b.kind == ForbiddenBargain.Kind.WAGER);
            if (wager != null) insertDealerCounterpart(offers, wager, Math.max(2, wanted));
        }
        if (risky && offers.size() < 2) {
            ForbiddenBargain alternative = pickFreshCard(data, player, watcherEligible, resolved, random, offers,
                    b -> b.kind == ForbiddenBargain.Kind.WAGER || b.kind == ForbiddenBargain.Kind.REMEDY
                            || b.kind == ForbiddenBargain.Kind.DEBT || b.kind == ForbiddenBargain.Kind.SCAR);
            if (alternative != null) insertDealerCounterpart(offers, alternative, 2);
        }

        ForbiddenBargain signature = dealer.signatureCard();
        if (signature.available(data, player, watcherEligible) && random.nextFloat() < dealer.signatureChance(data, player)
                && !offers.contains(signature)) {
            if (offers.size() < 4) offers.add(signature);
            else offers.set(offers.size() - 1, signature);
        }

        if (offers.isEmpty()) return BargainDeal.empty();
        return new BargainDeal(offers, BARGAIN_NORMAL);
    }

    private static void addDistinct(List<ForbiddenBargain> out, List<ForbiddenBargain> pool, int wanted) {
        for (ForbiddenBargain candidate : pool) {
            if (out.size() >= wanted) return;
            if (!out.contains(candidate)) out.add(candidate);
        }
    }

    private static void insertDealerCounterpart(List<ForbiddenBargain> offers, ForbiddenBargain card, int wanted) {
        if (card == null || offers.contains(card)) return;
        if (offers.size() < Math.min(4, wanted)) offers.add(card);
        else if (!offers.isEmpty()) offers.set(offers.size() - 1, card);
    }

    private static ForbiddenBargain pickFreshCard(LockData data, UUID player, boolean watcherEligible, int resolved,
                                                   Random random, List<ForbiddenBargain> exclude,
                                                   java.util.function.Predicate<ForbiddenBargain> predicate) {
        List<ForbiddenBargain> fresh = new ArrayList<>();
        List<ForbiddenBargain> stale = new ArrayList<>();
        for (ForbiddenBargain b : ForbiddenBargain.values()) {
            if (AnomalyCardSystem.isAnomaly(b) || CardEchoSystem.isPrimer(b) || b == ForbiddenBargain.THE_RED_PEN) continue;
            if (exclude.contains(b) || !b.available(data, player, watcherEligible) || !isLooseKindUnlocked(b.kind, resolved)
                    || b.kind == ForbiddenBargain.Kind.MASTER || b.kind == ForbiddenBargain.Kind.REFRESH || !predicate.test(b)) continue;
            (data.wasRecentlyOffered(player, b.id) ? stale : fresh).add(b);
        }
        List<ForbiddenBargain> pool = fresh.isEmpty() ? stale : fresh;
        return CardEcology.weightedPick(pool, data, player, random);
    }

    private static ForbiddenBargain.Kind chooseLooseTargetKind(CardMaster dealer, LockData data, UUID player, int resolved, float roll) {
        int relation = dealer.relation(data, player);
        float favor = Math.max(0.78f, Math.min(1.28f, 1.0f + relation * 0.018f));
        int grudge = dealer.grudge(data, player);
        // Death laws are never dealt by a physical Sealed Card. Keep their
        // probability completely outside the loose-card roll rather than merely
        // suppressing the return branch, otherwise the unused slice leaks into
        // the next rarity band.
        float death = 0f;
        float unique = resolved >= WardConfig.uniqueCardsAfterBeaten ? switch (dealer) {
            case ASHEN_CURATOR -> 0.0025f; case MOURNING_NOTARY -> 0.0025f; case PALE_GAMBLER -> 0.0120f * favor;
        } : 0f;
        float epic = resolved >= WardConfig.epicCardsAfterBeaten ? switch (dealer) {
            case ASHEN_CURATOR -> 0.0075f; case MOURNING_NOTARY -> 0.0080f; case PALE_GAMBLER -> 0.0350f * favor;
        } : 0f;
        float curse = resolved >= WardConfig.curseCardsAfterBeaten ? switch (dealer) {
            case ASHEN_CURATOR -> 0.070f * favor + 0.025f * grudge;
            case MOURNING_NOTARY -> 0.025f + 0.010f * grudge;
            case PALE_GAMBLER -> 0.100f * favor + 0.030f * grudge;
        } : 0f;
        float covenant = resolved >= WardConfig.covenantCardsAfterBeaten ? switch (dealer) {
            case ASHEN_CURATOR -> 0.090f * favor;
            case MOURNING_NOTARY -> 0.110f * favor + 0.025f * grudge;
            case PALE_GAMBLER -> 0.070f + 0.020f * grudge;
        } : 0f;
        float ritual = resolved >= WardConfig.ritualCardsAfterBeaten ? switch (dealer) {
            case ASHEN_CURATOR -> 0.110f;
            case MOURNING_NOTARY -> 0.240f * favor + 0.030f * grudge;
            case PALE_GAMBLER -> 0.060f;
        } : 0f;
        float contract = resolved >= WardConfig.contractCardsAfterBeaten ? switch (dealer) {
            case ASHEN_CURATOR -> 0.200f;
            case MOURNING_NOTARY -> 0.300f * favor + 0.035f * grudge;
            case PALE_GAMBLER -> 0.090f;
        } : 0f;

        // Attention changes the table's appetite without bypassing unlock gates.
        AttentionSystem.Stage attentionStage = AttentionSystem.current(data, player);
        if (attentionStage == AttentionSystem.Stage.HUNTED) {
            if (death > 0f) death += 0.0008f;
            if (curse > 0f) curse += 0.012f;
            if (covenant > 0f) covenant += 0.008f;
        } else if (attentionStage == AttentionSystem.Stage.KNOWN) {
            if (death > 0f) death += 0.0016f;
            if (curse > 0f) curse += 0.025f;
            if (covenant > 0f) covenant += 0.015f;
        }

        // Even a favored/angry dealer must occasionally expose an ordinary hand.
        // Cap the aggregate special-shelf probability without distorting the truly rare tiers.
        float fixedRare = death + unique + epic + curse;
        float objectives = covenant + ritual + contract;
        float totalSpecial = fixedRare + objectives;
        if (totalSpecial > 0.92f && objectives > 0.0001f) {
            float scale = Math.max(0f, (0.92f - fixedRare) / objectives);
            covenant *= scale;
            ritual *= scale;
            contract *= scale;
        }
        float at = death;
        if (roll < at) return ForbiddenBargain.Kind.DEATH;
        at += unique; if (roll < at) return ForbiddenBargain.Kind.UNIQUE;
        at += epic; if (roll < at) return ForbiddenBargain.Kind.EPIC;
        at += curse; if (roll < at) return ForbiddenBargain.Kind.CURSE;
        at += covenant; if (roll < at) return ForbiddenBargain.Kind.COVENANT;
        at += ritual; if (roll < at) return ForbiddenBargain.Kind.RITUAL;
        at += contract; if (roll < at) return ForbiddenBargain.Kind.CONTRACT;
        return null;
    }

    private static boolean isLooseKindUnlocked(ForbiddenBargain.Kind kind, int resolved) {
        return switch (kind) {
            case CONTRACT -> resolved >= WardConfig.contractCardsAfterBeaten;
            case RITUAL -> resolved >= WardConfig.ritualCardsAfterBeaten;
            case COVENANT -> resolved >= WardConfig.covenantCardsAfterBeaten;
            case CURSE -> resolved >= WardConfig.curseCardsAfterBeaten;
            case EPIC -> resolved >= WardConfig.epicCardsAfterBeaten;
            case UNIQUE -> resolved >= WardConfig.uniqueCardsAfterBeaten;
            case DEATH -> false;
            default -> true;
        };
    }

    /** Sometimes deals one unreadable +1 Anomaly outside the ordinary shelf composition. */
    private static BargainDeal appendAnomalySlot(LockData data, UUID player, BargainDeal deal, long seed, boolean loose) {
        if (data == null || player == null || deal == null || deal.cards().isEmpty()) return deal;
        if (deal.mode() == BARGAIN_DEATH || deal.mode() == BARGAIN_CURSE) return deal;
        if (deal.cards().size() >= 5) return deal;
        boolean guaranteed = data.uniqueInt(player, "anomaly_guarantee") > 0;
        boolean lured = data.uniqueInt(player, "anomaly_lure") > 0;
        int cooldown = data.uniqueInt(player, "anomaly_deck_cooldown");
        if (!guaranteed && cooldown > 0) {
            data.setUniqueInt(player, "anomaly_deck_cooldown", cooldown - 1);
            if (lured) AnomalyCardSystem.consumeLureAttempt(data, player);
            return deal;
        }
        Random random = new Random(seed ^ 0x4E4F4D414C59L);
        float chance = AnomalyCardSystem.chance(data, player, loose);
        if (random.nextFloat() >= chance) {
            if (lured) AnomalyCardSystem.consumeLureAttempt(data, player);
            return deal;
        }
        ForbiddenBargain anomaly = AnomalyCardSystem.pick(data, player, random);
        if (anomaly == null || deal.cards().contains(anomaly)) return deal;
        List<ForbiddenBargain> out = new ArrayList<>(deal.cards());
        out.add(anomaly);
        AnomalyCardSystem.consumeAppearanceQueue(data, player);
        data.setUniqueInt(player, "anomalies_dealt", data.uniqueInt(player, "anomalies_dealt") + 1);
        data.setUniqueInt(player, "anomaly_deck_cooldown", 2);
        return new BargainDeal(out, deal.mode());
    }

    /**
     * Adds the two non-pool hand utilities.  Neither is allowed to displace a
     * real law globally: they only occupy a slot in this concrete hand.
     * Red Pen is intentionally rarer in binding Curse/Death decks.
     */
    private static BargainDeal appendHandUtilitySlots(LockData data, UUID player, BargainDeal deal, long seed, boolean loose) {
        if (data == null || player == null || deal == null || deal.cards().isEmpty()) return deal;
        List<ForbiddenBargain> cards = new ArrayList<>(deal.cards());
        Random random = new Random(seed ^ 0x7F4A7C15D1B54A32L);
        int resolved = data.totalBeaten(player);

        if (resolved >= ForbiddenBargain.THE_RED_PEN.minResolved && cards.size() < 5 && !cards.contains(ForbiddenBargain.THE_RED_PEN)) {
            float redPenChance = deal.mode() == BARGAIN_DEATH ? 0.012f : deal.mode() == BARGAIN_CURSE ? 0.028f : (loose ? 0.060f : 0.085f);
            if (random.nextFloat() < redPenChance) cards.add(ForbiddenBargain.THE_RED_PEN);
        }

        if (cards.size() < 5) {
            java.util.EnumSet<ForbiddenBargain.Kind> represented = java.util.EnumSet.noneOf(ForbiddenBargain.Kind.class);
            for (ForbiddenBargain card : cards) {
                if (card == null || CardEchoSystem.isPrimer(card) || card == ForbiddenBargain.THE_RED_PEN || card.kind == ForbiddenBargain.Kind.REFRESH) continue;
                represented.add(card.kind);
            }
            List<ForbiddenBargain> primers = new ArrayList<>();
            for (ForbiddenBargain.Kind kind : represented) {
                ForbiddenBargain primer = CardEchoSystem.primerForKind(kind);
                if (primer != null && resolved >= primer.minResolved && CardEchoSystem.available(data, player, primer)) primers.add(primer);
            }
            float echoChance = deal.mode() == BARGAIN_DEATH ? 0.018f : deal.mode() == BARGAIN_CURSE ? 0.032f : (loose ? 0.045f : 0.070f);
            if (!primers.isEmpty() && random.nextFloat() < echoChance) {
                java.util.Collections.shuffle(primers, random);
                cards.add(primers.get(0));
            }
        }

        return new BargainDeal(cards, deal.mode());
    }

    /** Every third real hand after the configured bonus-offer shelf receives one extra legal offer. */
    private static boolean progressionExtraOffer(LockData data, UUID player) {
        if (data == null || player == null || data.totalBeaten(player) < WardConfig.bonusFreshRollAfterBeaten) return false;
        int n = data.uniqueInt(player, "mid_progression_extra_hand") + 1;
        if (n >= 3) {
            data.setUniqueInt(player, "mid_progression_extra_hand", 0);
            return true;
        }
        data.setUniqueInt(player, "mid_progression_extra_hand", n);
        return false;
    }

    /** Applies one-shot hand-shaping laws after the shelf itself has been selected. */
    private static BargainDeal applyQueuedDeckModifiers(LockData data, UUID player, BargainDeal deal, long seed, boolean afterRefresh) {
        if (data == null || player == null || deal == null || deal.cards().isEmpty()) return deal;
        Random random = new Random(seed ^ 0x6A09E667F3BCC909L);
        List<ForbiddenBargain> cards = new ArrayList<>(deal.cards());
        int mode = deal.mode();
        boolean binding = mode == BARGAIN_DEATH || mode == BARGAIN_CURSE;
        int resolved = data.totalBeaten(player);
        boolean watcherEligible = WardConfig.watcherEnabled && resolved >= WardConfig.watcherAfter;

        if (!binding && data.uniqueInt(player, "deck_clean_margin") > 0) {
            cards.removeIf(c -> c.kind == ForbiddenBargain.Kind.DEBT || c.kind == ForbiddenBargain.Kind.SCAR || c.kind == ForbiddenBargain.Kind.CURSE);
            fillDeck(cards, data, player, watcherEligible, resolved, random, 3, true, false);
        }
        if (!binding && data.uniqueInt(player, "deck_black_margin") > 0) {
            cards.removeIf(c -> c.kind == ForbiddenBargain.Kind.REMEDY);
            int guard = 0;
            while (cards.size() < 3 && guard++ < 32) {
                ForbiddenBargain pick = pickDeckCard(data, player, watcherEligible, resolved, random, cards,
                        c -> c.kind != ForbiddenBargain.Kind.REMEDY && c.kind != ForbiddenBargain.Kind.CURSE
                                && c.kind != ForbiddenBargain.Kind.DEATH && deckQuality(c) >= 2);
                if (pick == null) break;
                cards.add(pick);
            }
        }
        if (!binding && data.uniqueInt(player, "deck_no_echoes") > 0) {
            cards.removeIf(c -> data.wasRecentlyOffered(player, c.id));
            int guard = 0;
            while (cards.size() < 3 && guard++ < 32) {
                ForbiddenBargain pick = pickDeckCard(data, player, watcherEligible, resolved, random, cards,
                        c -> !data.wasRecentlyOffered(player, c.id) && c.kind != ForbiddenBargain.Kind.CURSE
                                && c.kind != ForbiddenBargain.Kind.DEATH);
                if (pick == null) break;
                cards.add(pick);
            }
        }
        if (!binding && data.uniqueInt(player, "deck_remedy_witness") > 0 && (data.hasAnyBargainDebt(player) || FreshCardEffects.hasLesserBurden(data, player) || SecondWaveCardEffects.hasLesserBurden(data, player) || ThirdWaveCardEffects.hasLesserBurden(data, player) || FifthWaveCardEffects.hasLesserBurden(data, player) || ApothicCardEffects.hasLesserBurden(data, player))) {
            ForbiddenBargain remedy = pickDeckCard(data, player, watcherEligible, resolved, random, cards,
                    c -> c.kind == ForbiddenBargain.Kind.REMEDY);
            if (remedy != null && !cards.contains(remedy)) {
                if (cards.size() < 4) cards.add(remedy); else cards.set(cards.size()-1, remedy);
            }
        }
        if (!binding && data.uniqueInt(player, "deck_deep_shelf") > 0) {
            ForbiddenBargain premium = pickDeckCard(data, player, watcherEligible, resolved, random, cards,
                    c -> c.kind == ForbiddenBargain.Kind.EPIC || c.kind == ForbiddenBargain.Kind.UNIQUE);
            if (premium == null) premium = pickDeckCard(data, player, watcherEligible, resolved, random, cards,
                    c -> c.kind == ForbiddenBargain.Kind.WAGER && c.minResolved >= 100);
            if (premium != null && !cards.contains(premium)) {
                if (cards.size() < 4) cards.add(premium); else cards.set(cards.size()-1, premium);
            }
        }
        if (!binding && data.uniqueInt(player, "deck_rare_ink") > 0)
            upgradeExistingLowest(cards, data, player, watcherEligible, resolved, random);

        int plus = data.uniqueInt(player, "deck_plus_one") > 0 ? 1 : 0;
        plus += data.uniqueInt(player, "deck_plus_two") > 0 ? 2 : 0;
        if (data.uniqueInt(player, "deck_curse_bait") > 0) plus++;
        if (!afterRefresh && progressionExtraOffer(data, player)) plus++;
        if (plus > 0) {
            int target = Math.min(5, cards.size() + plus);
            if (mode == BARGAIN_CURSE) fillDeckOfKind(cards, data, player, watcherEligible, resolved, random, target, ForbiddenBargain.Kind.CURSE);
            else if (mode == BARGAIN_DEATH) fillDeckOfKind(cards, data, player, watcherEligible, resolved, random, target, ForbiddenBargain.Kind.DEATH);
            else fillDeck(cards, data, player, watcherEligible, resolved, random, target, false, false);
        }

        if (!binding && data.uniqueInt(player, "deck_narrow") > 0 && cards.size() > 1) {
            cards.sort(java.util.Comparator.comparingInt(ChestValuator::deckQuality).reversed());
            cards.remove(cards.size()-1); // the hand is genuinely one card narrower
            upgradeExistingLowest(cards, data, player, watcherEligible, resolved, random);
        }
        if (!binding && data.uniqueInt(player, "deck_golden_cut") > 0 && cards.size() > 1) {
            cards.sort(java.util.Comparator.comparingInt(ChestValuator::deckQuality).reversed());
            cards.remove(cards.size()-1);
            upgradeLowest(cards, data, player, watcherEligible, resolved, random);
        }

        // Consume only after an actual deck exists. Refresh counts as a deck too.
        consumeDeckFlag(data, player, "deck_plus_one", 1);
        consumeDeckFlag(data, player, "deck_plus_two", 1);
        consumeDeckFlag(data, player, "deck_narrow", 1);
        consumeDeckFlag(data, player, "deck_curse_damper", 1);
        consumeDeckFlag(data, player, "deck_curse_bait", 1);
        consumeDeckFlag(data, player, "deck_rare_ink", 1);
        consumeDeckFlag(data, player, "deck_clean_margin", 1);
        consumeDeckFlag(data, player, "deck_black_margin", 1);
        consumeDeckFlag(data, player, "deck_no_echoes", 1);
        consumeDeckFlag(data, player, "deck_remedy_witness", 1);
        consumeDeckFlag(data, player, "deck_golden_cut", 1);
        consumeDeckFlag(data, player, "deck_deep_shelf", 1);
        consumeDeckFlag(data, player, "deck_curse_lull", 1);

        // De-duplicate and keep the client-readable maximum bounded.
        List<ForbiddenBargain> unique = new ArrayList<>();
        for (ForbiddenBargain card : cards) if (card != null && !unique.contains(card) && unique.size() < 5) unique.add(card);
        return unique.isEmpty() ? deal : new BargainDeal(unique, mode);
    }

    private static void consumeDeckFlag(LockData data, UUID id, String key, int amount) {
        int n = data.uniqueInt(id, key);
        if (n > 0) data.setUniqueInt(id, key, Math.max(0, n - amount));
    }

    private static void fillDeck(List<ForbiddenBargain> cards, LockData data, UUID id, boolean watcherEligible,
                                 int resolved, Random random, int target, boolean clean, boolean qualityBias) {
        int guard = 0;
        while (cards.size() < target && guard++ < 48) {
            ForbiddenBargain pick = pickDeckCard(data, id, watcherEligible, resolved, random, cards, c -> {
                if (c.kind == ForbiddenBargain.Kind.MASTER || c.kind == ForbiddenBargain.Kind.REFRESH
                        || c.kind == ForbiddenBargain.Kind.DEATH || c.kind == ForbiddenBargain.Kind.CURSE) return false;
                if (clean && (c.kind == ForbiddenBargain.Kind.DEBT || c.kind == ForbiddenBargain.Kind.SCAR || c.kind == ForbiddenBargain.Kind.CURSE)) return false;
                return !qualityBias || deckQuality(c) >= 2;
            });
            if (pick == null) break;
            cards.add(pick);
        }
    }

    private static void fillDeckOfKind(List<ForbiddenBargain> cards, LockData data, UUID id, boolean watcherEligible,
                                       int resolved, Random random, int target, ForbiddenBargain.Kind kind) {
        int guard = 0;
        while (cards.size() < target && guard++ < 32) {
            ForbiddenBargain pick = pickDeckCard(data,id,watcherEligible,resolved,random,cards,c -> c.kind == kind);
            if (pick == null) break;
            cards.add(pick);
        }
    }

    private static ForbiddenBargain pickDeckCard(LockData data, UUID id, boolean watcherEligible, int resolved,
                                                  Random random, List<ForbiddenBargain> exclude,
                                                  java.util.function.Predicate<ForbiddenBargain> predicate) {
        List<ForbiddenBargain> pool = new ArrayList<>();
        for (ForbiddenBargain c : ForbiddenBargain.values()) {
            if (AnomalyCardSystem.isAnomaly(c) || CardEchoSystem.isPrimer(c) || c == ForbiddenBargain.THE_RED_PEN) continue;
            if (c.kind == ForbiddenBargain.Kind.MASTER || c.kind == ForbiddenBargain.Kind.REFRESH || c.kind == ForbiddenBargain.Kind.DEATH) continue;
            if (exclude.contains(c) || !c.available(data,id,watcherEligible)
                    || (!isLooseKindUnlocked(c.kind,resolved) && c.kind != ForbiddenBargain.Kind.WAGER
                        && c.kind != ForbiddenBargain.Kind.DEBT && c.kind != ForbiddenBargain.Kind.SCAR
                        && c.kind != ForbiddenBargain.Kind.REMEDY)) continue;
            if (predicate.test(c)) pool.add(c);
        }
        if (pool.isEmpty()) return null;
        pool.sort(java.util.Comparator.comparingInt(ChestValuator::deckQuality).reversed());
        int window = Math.min(pool.size(), Math.max(1, pool.size()/2));
        return pool.get(random.nextInt(window));
    }

    private static void upgradeExistingLowest(List<ForbiddenBargain> cards, LockData data, UUID id, boolean watcherEligible, int resolved, Random random) {
        if (cards.isEmpty()) return;
        ForbiddenBargain better = pickDeckCard(data,id,watcherEligible,resolved,random,cards,c -> deckQuality(c) >= 3);
        if (better == null) return;
        int lowest = 0;
        for (int i=1;i<cards.size();i++) if (deckQuality(cards.get(i)) < deckQuality(cards.get(lowest))) lowest=i;
        if (deckQuality(better) > deckQuality(cards.get(lowest))) cards.set(lowest, better);
    }

    private static void upgradeLowest(List<ForbiddenBargain> cards, LockData data, UUID id, boolean watcherEligible, int resolved, Random random) {
        ForbiddenBargain better = pickDeckCard(data,id,watcherEligible,resolved,random,cards,c -> deckQuality(c) >= 3);
        if (better != null) cards.add(better);
        else fillDeck(cards,data,id,watcherEligible,resolved,random,Math.min(4,cards.size()+1),false,true);
    }

    private static int deckQuality(ForbiddenBargain c) {
        if (c == null) return 0;
        return switch (c.kind) {
            case DEATH -> 7; case EPIC -> 6; case UNIQUE -> 5; case COVENANT -> 4;
            case RITUAL, CONTRACT, MASTER -> 3; case WAGER, REMEDY -> 2; case SCAR, DEBT -> 1; case CURSE -> 0; case REFRESH -> 1;
        };
    }

    private static void sendLooseBargainScreen(ServerPlayer player) {
        CompoundTag pd = loosePlayerData(player);
        int[] offers = pd.getIntArray(LOOSE_OFFERS);
        if (offers.length == 0 || player.getServer() == null) return;
        LockData data = LockData.get(player.getServer());
        CardMaster dealer = CardMaster.byId(pd.getString(LOOSE_DEALER));
        String presenter = dealer.presenterLabel(data, player.getUUID());
        int[] ids = new int[offers.length];
        float[] rewards = new float[offers.length];
        int[] variants = new int[offers.length];
        int n = 0;
        for (int offered : offers) {
            ForbiddenBargain card = ForbiddenBargain.byId(offered);
            if (card == null) continue;
            WardHistory.discoverCard(player, card);
            ids[n] = offered;
            rewards[n] = 0f;
            variants[n] = CardEvolution.nextVariantLevel(data, player.getUUID(), card);
            n++;
        }
        if (n == 0) {
            // Old saves can retain offer ids that no longer exist after a card
            // registry/progression rewrite. Never let that stale array soft-lock
            // every future Sealed Card for this player. The consumed historical
            // card cannot be reconstructed safely, so retire only the dead hand.
            clearLooseCard(player);
            WardHud.message(player, Component.literal("The old sealed hand has faded; no readable law remains.")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
            return;
        }
        if (n != ids.length) {
            ids = java.util.Arrays.copyOf(ids, n);
            rewards = java.util.Arrays.copyOf(rewards, n);
            variants = java.util.Arrays.copyOf(variants, n);
            // Keep the server-side offer set identical to what the client can
            // actually see. This also migrates old hands containing removed ids.
            pd.putIntArray(LOOSE_OFFERS, ids);
        }
        BlockPos pos = BlockPos.of(pd.getLong(LOOSE_POS));
        int mode = pd.getInt(LOOSE_MODE);
        boolean forcedChoice = mode == BARGAIN_CURSE || mode == BARGAIN_DEATH;
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenBargainPacket(pos, ids, rewards, variants, 0f, pd.getLong(LOOSE_SEED), mode, forcedChoice, presenter,
                        mode == BARGAIN_NORMAL ? WardMeasureSystem.cuts(data, player.getUUID()) : 0));
    }

    private static void resolveLooseBargainChoice(ServerPlayer player, int bargainId, int branch) {
        if (player.getServer() == null) return;
        CompoundTag pd = loosePlayerData(player);
        int[] offered = pd.getIntArray(LOOSE_OFFERS);
        int mode = pd.getInt(LOOSE_MODE);
        LockData data = LockData.get(player.getServer());
        if (bargainId == MULLIGAN_ACTION_ID) {
            if (mode != BARGAIN_NORMAL) { sendLooseBargainScreen(player); return; }
            resolveLooseMulligan(player, data, offered, branch);
            return;
        }
        ForbiddenBargain card = ForbiddenBargain.byId(bargainId);
        CardMaster dealer = CardMaster.byId(pd.getString(LOOSE_DEALER));
        UUID id = player.getUUID();
        if (card != null && containsOffer(offered, card.id)) {
            if (card == ForbiddenBargain.THE_RED_PEN && branch >= RED_PEN_TARGET_BASE) {
                resolveLooseRedPen(player, data, offered, branch - RED_PEN_TARGET_BASE);
                return;
            }
            if (card == ForbiddenBargain.THE_RED_PEN) {
                sendLooseBargainScreen(player);
                return;
            }
            if (!CardBranches.validChoice(CardEvolution.nextVariantLevel(data, id, card), card, branch)) {
                sendLooseBargainScreen(player);
                return;
            }
            CardBranches.select(player, data, card, branch);
            resetRefuseStreak(player);
            WardHistory.acceptCard(player, card);
            prepareCardSignature(player, data, card, dealer);
            CardEchoSystem.beginSignature(data, id, card);
            boolean echoed = CardEchoSystem.consumeIfMatching(data, id, card);
            if (echoed) CardEchoSystem.strengthen(player, data, card);
            CardEchoSystem.ResolutionSnapshot echoSnapshot = echoed ? CardEchoSystem.snapshotResolution(player, data) : null;
            applyCardState(player, data, card);
            if (echoed) CardEchoSystem.finishResolution(player, data, card, echoSnapshot);
            data.setUniqueInt(id, "field_cards_signed", data.uniqueInt(id, "field_cards_signed") + 1);
            if (AnomalyCardSystem.isAnomaly(card)) {
                WardHistory.recordSpecial(player, "GLITCH", "An extra card in " + dealer.title + "'s hand resolved without belonging to the dealer.");
            } else {
                int beforeRelation = dealer.relation(data, id);
                int relation = dealer.addRelation(data, id, dealer.acceptanceRelationDelta(card));
                dealer.settleGrudge(data, id, card);
                data.setUniqueInt(id, "dealer_acceptances_" + dealer.id, data.uniqueInt(id, "dealer_acceptances_" + dealer.id) + 1);
                SavantMemory.onMasterEvent(player, dealer, true);
                MasterStory.onAccepted(player, data, dealer, card);
                String line = dealer.acceptanceLine(data, id, card, player.getRandom());
                WardHud.message(player, Component.literal(line).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
                WardHistory.recordSpecial(player, "WARD", line + " [relation " + (relation >= 0 ? "+" : "") + relation + "]");
                int beforeTier = relationTierForValue(beforeRelation);
                int afterTier = relationTierForValue(relation);
                if (afterTier > beforeTier)
                    dev.marrowseal.wardbound.item.CthulhuEyeItem.speakInsight(player, "dealer_relation_" + dealer.id + "_tier_" + afterTier,
                            dealer.title + " has altered the way it deals to you. The change is mechanical before it is personal.", 20L * 120L);
            }
            playCardAcceptanceFeedback(player, card);
        } else {
            // Curse and Death hands are binding regardless of where they were dealt.
            // The physical-container path already enforced this, but the player-owned
            // Sealed Card path used to treat -1/forged choices as a refusal and clear
            // the hand. Keep the offer alive and reopen it instead.
            if (mode == BARGAIN_CURSE || mode == BARGAIN_DEATH) {
                sendLooseBargainScreen(player);
                return;
            }
            int relation = dealer.addRelation(data, id, dealer.refusalRelationDelta(data, id));
            dealer.addGrudge(data, id, 1);
            data.setUniqueInt(id, "dealer_refusals_" + dealer.id, data.uniqueInt(id, "dealer_refusals_" + dealer.id) + 1);
            SavantMemory.onMasterEvent(player, dealer, false);
            MasterStory.evaluate(player, data, dealer);
            recordRefuseStreak(player);
            String line = dealer.refusalLine(data, id, player.getRandom());
            int grudge = dealer.grudge(data, id);
            if (grudge >= 2) dev.marrowseal.wardbound.item.CthulhuEyeItem.speakInsight(player,
                    "dealer_grudge_" + dealer.id + "_" + grudge,
                    dealer.title + " is no longer treating refusal as an isolated choice. The next hand will remember it.", 20L * 120L);
            WardHud.message(player, Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
            WardHistory.recordSpecial(player, "WARD", line + " [relation " + (relation >= 0 ? "+" : "") + relation + "]");
            if (mode == BARGAIN_NORMAL && WardMeasureSystem.bankFromFold(player, data))
                WardHud.message(player, Component.literal("FOLD // one Cut is held for a later ordinary hand · "
                                + WardMeasureSystem.cuts(data, id) + "/" + WardMeasureSystem.MAX_CUTS)
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC), false);
        }
        clearLooseCard(player);
    }

    /** Spend one banked Cut to replace exactly one visible offer in a loose/field hand. */
    private static void resolveLooseMulligan(ServerPlayer player, LockData data, int[] offered, int targetId) {
        if (player == null || data == null || offered == null || !containsOffer(offered, targetId)
                || WardMeasureSystem.cuts(data, player.getUUID()) <= 0) {
            sendLooseBargainScreen(player);
            return;
        }
        CompoundTag pd = loosePlayerData(player);
        long seed = pd.getLong(LOOSE_SEED) ^ ((long) targetId * 0x9E3779B97F4A7C15L)
                ^ ((long) WardMeasureSystem.spentCuts(data, player.getUUID()) * 0xC2B2AE3D27D4EB4FL);
        ForbiddenBargain replacement = pickMulliganReplacement(data, player.getUUID(), offered, targetId, seed);
        if (replacement == null || !WardMeasureSystem.consumeCut(player, data)) {
            sendLooseBargainScreen(player);
            return;
        }
        int[] redrawn = replaceOffer(offered, targetId, replacement.id);
        pd.putIntArray(LOOSE_OFFERS, redrawn);
        data.rememberCardOffers(player.getUUID(), redrawn);
        WardHistory.discoverCard(player, replacement);
        ForbiddenBargain old = ForbiddenBargain.byId(targetId);
        WardHistory.recordSpecial(player, "CARD", "Spent a Cut // "
                + (old == null ? "one offer" : old.title) + " redrawn as " + replacement.title);
        WardHud.message(player, Component.literal("CUT SPENT // " + replacement.title
                        + " enters this hand · " + WardMeasureSystem.cuts(data, player.getUUID()) + "/" + WardMeasureSystem.MAX_CUTS + " held")
                .withStyle(ChatFormatting.AQUA), false);
        sendLooseBargainScreen(player);
    }

    /** Container equivalent of {@link #resolveLooseMulligan}; it changes only this chest's stored offer array. */
    private static void resolveContainerMulligan(ServerPlayer player, ServerLevel level, BlockPos pos,
                                                 RandomizableContainerBlockEntity container, LockData data,
                                                 int[] offered, int targetId) {
        if (player == null || level == null || container == null || data == null || offered == null
                || !containsOffer(offered, targetId) || WardMeasureSystem.cuts(data, player.getUUID()) <= 0) {
            openBargainScreen(player, pos, container);
            return;
        }
        CompoundTag persistent = container.getPersistentData();
        long seed = persistent.getLong(TAG_BARGAIN_SEED) ^ ((long) targetId * 0x9E3779B97F4A7C15L)
                ^ ((long) WardMeasureSystem.spentCuts(data, player.getUUID()) * 0xC2B2AE3D27D4EB4FL);
        ForbiddenBargain replacement = pickMulliganReplacement(data, player.getUUID(), offered, targetId, seed);
        if (replacement == null || !WardMeasureSystem.consumeCut(player, data)) {
            openBargainScreen(player, pos, container);
            return;
        }
        int[] redrawn = replaceOffer(offered, targetId, replacement.id);
        persistent.putIntArray(TAG_BARGAIN_OFFERS, redrawn);
        data.rememberCardOffers(player.getUUID(), redrawn);
        container.setChanged();
        WardHistory.discoverCard(player, replacement);
        ForbiddenBargain old = ForbiddenBargain.byId(targetId);
        WardHistory.recordSpecial(player, "CARD", "Spent a Cut // "
                + (old == null ? "one offer" : old.title) + " redrawn as " + replacement.title);
        WardHud.message(player, Component.literal("CUT SPENT // " + replacement.title
                        + " enters this hand · " + WardMeasureSystem.cuts(data, player.getUUID()) + "/" + WardMeasureSystem.MAX_CUTS + " held")
                .withStyle(ChatFormatting.AQUA), false);
        openBargainScreen(player, pos, container);
    }

    /**
     * A Cut is a mulligan, not a rarity upgrade. It strongly prefers the removed card's family
     * and otherwise refuses Curse/Death/Master/Refresh/anomaly/primer utility shelves. The
     * replacement can never exceed the removed offer's deck-quality band (ordinary utility
     * cards are treated as band two), so banking Cuts cannot be used to fish upward forever.
     */
    private static ForbiddenBargain pickMulliganReplacement(LockData data, UUID id, int[] offered,
                                                             int targetId, long seed) {
        if (data == null || id == null) return null;
        ForbiddenBargain target = ForbiddenBargain.byId(targetId);
        int resolved = data.totalBeaten(id);
        boolean watcherEligible = WardConfig.watcherEnabled && resolved >= WardConfig.watcherAfter;
        Random random = new Random(seed);
        List<ForbiddenBargain> exclude = new ArrayList<>();
        for (int value : offered) {
            ForbiddenBargain b = ForbiddenBargain.byId(value);
            if (b != null) exclude.add(b);
        }
        int maxQuality = target == null ? 2 : Math.max(2, deckQuality(target));
        if (target != null && random.nextFloat() < 0.70f) {
            ForbiddenBargain same = pickFreshCard(data, id, watcherEligible, resolved, random, exclude,
                    b -> b.kind == target.kind && b.kind != ForbiddenBargain.Kind.CURSE
                            && b.kind != ForbiddenBargain.Kind.DEATH && deckQuality(b) <= maxQuality);
            if (same != null) return same;
        }
        return pickFreshCard(data, id, watcherEligible, resolved, random, exclude,
                b -> b.kind != ForbiddenBargain.Kind.CURSE && b.kind != ForbiddenBargain.Kind.DEATH
                        && deckQuality(b) <= maxQuality);
    }

    private static int[] replaceOffer(int[] offered, int targetId, int replacementId) {
        int[] out = offered == null ? new int[0] : java.util.Arrays.copyOf(offered, offered.length);
        for (int i = 0; i < out.length; i++) {
            if (out[i] == targetId) { out[i] = replacementId; break; }
        }
        return out;
    }

    private static void resolveLooseRedPen(ServerPlayer player, LockData data, int[] offered, int targetId) {
        if (player == null || data == null || offered == null || targetId < 0
                || targetId == ForbiddenBargain.THE_RED_PEN.id || !containsOffer(offered, targetId)) {
            sendLooseBargainScreen(player);
            return;
        }
        int[] kept = removeOffers(offered, ForbiddenBargain.THE_RED_PEN.id, targetId);
        UUID id = player.getUUID();
        data.setUniqueInt(id, "red_pen_uses", data.uniqueInt(id, "red_pen_uses") + 1);
        WardHistory.acceptCard(player, ForbiddenBargain.THE_RED_PEN);
        ForbiddenBargain crossed = ForbiddenBargain.byId(targetId);
        WardHistory.recordSpecial(player, "CARD", "The Red Pen struck " + (crossed == null ? "an unreadable card" : crossed.title) + " from one field hand.");
        WardHud.message(player, Component.literal("THE RED PEN // " + (crossed == null ? "one card" : crossed.title) + " is struck from this hand")
                .withStyle(ChatFormatting.RED), false);
        CompoundTag pd = loosePlayerData(player);
        if (kept.length == 0) {
            clearLooseCard(player);
            return;
        }
        pd.putIntArray(LOOSE_OFFERS, kept);
        sendLooseBargainScreen(player);
    }

    private static void resolveContainerRedPen(ServerPlayer player, ServerLevel level, BlockPos pos,
                                               RandomizableContainerBlockEntity container, LockData data,
                                               int[] offered, int targetId) {
        if (player == null || level == null || container == null || data == null || offered == null
                || targetId < 0 || targetId == ForbiddenBargain.THE_RED_PEN.id || !containsOffer(offered, targetId)) {
            openBargainScreen(player, pos, container);
            return;
        }
        int[] kept = removeOffers(offered, ForbiddenBargain.THE_RED_PEN.id, targetId);
        UUID id = player.getUUID();
        data.setUniqueInt(id, "red_pen_uses", data.uniqueInt(id, "red_pen_uses") + 1);
        WardHistory.acceptCard(player, ForbiddenBargain.THE_RED_PEN);
        ForbiddenBargain crossed = ForbiddenBargain.byId(targetId);
        WardHistory.recordSpecial(player, "CARD", "The Red Pen struck " + (crossed == null ? "an unreadable card" : crossed.title) + " from one chest hand.");
        WardHud.message(player, Component.literal("THE RED PEN // " + (crossed == null ? "one card" : crossed.title) + " is struck from this hand")
                .withStyle(ChatFormatting.RED), false);
        CompoundTag persistent = container.getPersistentData();
        if (kept.length == 0) {
            persistent.remove(TAG_BARGAIN_OFFERS);
            persistent.remove(TAG_BARGAIN_SEED);
            persistent.remove(TAG_BARGAIN_MODE);
            persistent.remove(TAG_BARGAIN_REFRESHED);
            persistent.remove(TAG_BARGAIN_CAP);
            container.setChanged();
            MinecraftServer server = player.getServer();
            if (server != null) unlockAndClear(level, pos, container, server, level.dimension().location().toString());
            WardHud.message(player, Component.literal("No law remains to sign. The hand closes cleanly.").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        persistent.putIntArray(TAG_BARGAIN_OFFERS, kept);
        container.setChanged();
        openBargainScreen(player, pos, container);
    }

    private static int[] removeOffers(int[] offered, int a, int b) {
        if (offered == null || offered.length == 0) return new int[0];
        int count = 0;
        for (int value : offered) if (value != a && value != b) count++;
        int[] out = new int[count];
        int i = 0;
        for (int value : offered) if (value != a && value != b) out[i++] = value;
        return out;
    }

    private static int relationTierForValue(int relation) {
        if (relation <= -8) return 0;
        if (relation <= -3) return 1;
        if (relation < 3) return 2;
        if (relation < 8) return 3;
        if (relation < 14) return 4;
        return 5;
    }

    private static void clearLooseCard(ServerPlayer player) {
        CompoundTag pd = loosePlayerData(player);
        pd.remove(LOOSE_OFFERS);
        pd.remove(LOOSE_MODE);
        pd.remove(LOOSE_SEED);
        pd.remove(LOOSE_POS);
        pd.remove(LOOSE_DEALER);
    }

    private record BargainDeal(List<ForbiddenBargain> cards, int mode) {
        static BargainDeal empty() { return new BargainDeal(List.of(), BARGAIN_NORMAL); }
    }

    private static boolean containsOffer(int[] offers, int id) {
        if (offers == null) return false;
        for (int offered : offers) if (offered == id) return true;
        return false;
    }

    /**
     * Compatibility escape hatch for a chest whose persisted card ids no longer
     * resolve after an update. Banked loot survives {@link #unlockAndClear}; only
     * the unreadable decision layer is retired so the physical chest cannot remain
     * permanently cancelled by {@link #onRightClick}.
     */
    private static void recoverBrokenContainerHand(ServerPlayer player, BlockPos pos,
                                                   RandomizableContainerBlockEntity container) {
        if (player == null || container == null || !(player.level() instanceof ServerLevel level)) return;
        CompoundTag persistent = container.getPersistentData();
        if (!isDecisionOwner(player, persistent)) return;
        persistent.remove(TAG_BARGAIN_OFFERS);
        persistent.remove(TAG_BARGAIN_SEED);
        persistent.remove(TAG_BARGAIN_MODE);
        persistent.remove(TAG_BARGAIN_REFRESHED);
        persistent.remove(TAG_BARGAIN_CAP);
        container.setChanged();
        MinecraftServer server = player.getServer();
        if (server != null) unlockAndClear(level, pos, container, server, level.dimension().location().toString());
        WardHud.message(player, Component.literal("The old card hand has become unreadable. The banked chest remains yours.")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
    }

    private static void openBargainScreen(ServerPlayer player, BlockPos pos,
                                          RandomizableContainerBlockEntity container) {
        CompoundTag persistent = container.getPersistentData();
        if (!isDecisionOwner(player, persistent)) {
            WardHud.message(player, Component.literal("The ink is addressed to another witness.")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }
        int[] storedIds = persistent.getIntArray(TAG_BARGAIN_OFFERS);
        if (storedIds.length == 0) {
            recoverBrokenContainerHand(player, pos, container);
            return;
        }
        List<Integer> ids = new ArrayList<>();
        List<Float> rewards = new ArrayList<>();
        for (int id : storedIds) {
            ForbiddenBargain b = ForbiddenBargain.byId(id);
            if (b == null || ids.contains(id)) continue;
            WardHistory.discoverCard(player, b);
            ids.add(id);
            float progCap = persistent.contains(TAG_BARGAIN_CAP)
                    ? persistent.getFloat(TAG_BARGAIN_CAP)
                    : progressionLootCap(LockData.get(player.getServer()).totalBeaten(player.getUUID()));
            rewards.add(Math.max(0f, Math.min(progCap, RewardBreakdown.clamp(persistent.getFloat(TAG_MULT) + b.rewardAdd()))
                    - persistent.getFloat(TAG_MULT)));
        }
        if (ids.isEmpty()) {
            recoverBrokenContainerHand(player, pos, container);
            return;
        }
        int[] offerIds = new int[ids.size()];
        float[] rewardAdds = new float[ids.size()];
        int[] variants = new int[ids.size()];
        LockData cardData = LockData.get(player.getServer());
        for (int i = 0; i < ids.size(); i++) {
            offerIds[i] = ids.get(i);
            rewardAdds[i] = rewards.get(i);
            ForbiddenBargain offeredCard = ForbiddenBargain.byId(offerIds[i]);
            variants[i] = CardEvolution.nextVariantLevel(cardData, player.getUUID(), offeredCard);
        }
        if (offerIds.length != storedIds.length) {
            // Drop removed/duplicate ids from the authoritative container state,
            // not only from the packet. Otherwise a forged stale client could
            // still reference a card the current UI never displayed.
            persistent.putIntArray(TAG_BARGAIN_OFFERS, offerIds);
            container.setChanged();
        }
        int mode = persistent.getInt(TAG_BARGAIN_MODE);
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenBargainPacket(pos, offerIds, rewardAdds, variants, persistent.getFloat(TAG_MULT),
                        persistent.getLong(TAG_BARGAIN_SEED), mode, mode == BARGAIN_CURSE || mode == BARGAIN_DEATH, "",
                        mode == BARGAIN_NORMAL ? WardMeasureSystem.cuts(cardData, player.getUUID()) : 0));
    }

    /** Server-authoritative resolution of the card screen. -1 means refuse. */
    public static void resolveBargainChoice(ServerPlayer player, BlockPos pos, int bargainId, int branch) {
        if (!(player.level() instanceof ServerLevel level)) return;
        CompoundTag playerData = loosePlayerData(player);
        if (playerData.contains(LOOSE_OFFERS) && playerData.getLong(LOOSE_POS) == pos.asLong()) {
            resolveLooseBargainChoice(player, bargainId, branch);
            return;
        }
        // Container bargains stay proximity-bound. Loose/field hands are player-owned and were
        // already authenticated above, so knockback while their non-pausing screen is open cannot
        // silently invalidate a legitimate selection.
        if (pos.distSqr(player.blockPosition()) > 100.0D) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RandomizableContainerBlockEntity container)) return;
        CompoundTag persistent = container.getPersistentData();
        if (!persistent.contains(TAG_BARGAIN_OFFERS)) return;
        if (!isDecisionOwner(player, persistent)) return;

        int[] offered = persistent.getIntArray(TAG_BARGAIN_OFFERS);
        int mode = persistent.getInt(TAG_BARGAIN_MODE);
        if (bargainId == MULLIGAN_ACTION_ID) {
            if (player.getServer() == null || mode != BARGAIN_NORMAL) { openBargainScreen(player, pos, container); return; }
            resolveContainerMulligan(player, level, pos, container, LockData.get(player.getServer()), offered, branch);
            return;
        }
        ForbiddenBargain bargain = ForbiddenBargain.byId(bargainId);
        if (bargain != null && containsOffer(offered, bargain.id)) {
            if (player.getServer() == null) return;
            LockData branchData = LockData.get(player.getServer());
            if (bargain == ForbiddenBargain.THE_RED_PEN && branch >= RED_PEN_TARGET_BASE) {
                resolveContainerRedPen(player, level, pos, container, branchData, offered, branch - RED_PEN_TARGET_BASE);
                return;
            }
            if (bargain == ForbiddenBargain.THE_RED_PEN) {
                openBargainScreen(player, pos, container);
                return;
            }
            if (!CardBranches.validChoice(CardEvolution.nextVariantLevel(branchData, player.getUUID(), bargain), bargain, branch)) {
                openBargainScreen(player, pos, container);
                return;
            }
            if (bargain == ForbiddenBargain.REFRESH_HAND && mode == BARGAIN_NORMAL) {
                resetRefuseStreak(player);
                refreshBargain(player, pos, container);
            } else {
                acceptBargain(player, pos, container, bargain, branch);
            }
            return;
        }

        // Curse decks are binding: even a forged/old client cannot refuse one.
        if (mode == BARGAIN_CURSE || mode == BARGAIN_DEATH) {
            openBargainScreen(player, pos, container);
            return;
        }

        // Refusing a maker's private law is remembered by that hand, but it is
        // deliberately a small change; declining a card is not the same as losing a seal.
        MinecraftServer relationServer = player.getServer();
        if (relationServer != null && mode == BARGAIN_NORMAL) {
            LockData relationData = LockData.get(relationServer);
            for (int offeredId : offered) {
                ForbiddenBargain offeredCard = ForbiddenBargain.byId(offeredId);
                if (offeredCard == null || !offeredCard.isMaster()) continue;
                MasterSignature sig = offeredCard == ForbiddenBargain.CROOKED_PRIVATE ? MasterSignature.CROOKED
                        : offeredCard == ForbiddenBargain.VEILED_PRIVATE ? MasterSignature.VEILED : MasterSignature.EXACTING;
                int relationNow = relationData.addRelation(player.getUUID(), sig, -1);
                WardHistory.discoverMaster(player, sig, relationNow);
            }
        }

        if (relationServer != null && mode == BARGAIN_NORMAL) {
            LockData foldData = LockData.get(relationServer);
            if (WardMeasureSystem.bankFromFold(player, foldData))
                WardHud.message(player, Component.literal("FOLD // one Cut is held for a later ordinary hand · "
                                + WardMeasureSystem.cuts(foldData, player.getUUID()) + "/" + WardMeasureSystem.MAX_CUTS)
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC), false);
        }
        recordRefuseStreak(player);

        persistent.remove(TAG_BARGAIN_OFFERS);
        persistent.remove(TAG_BARGAIN_SEED);
        persistent.remove(TAG_BARGAIN_MODE);
        persistent.remove(TAG_BARGAIN_REFRESHED);
        persistent.remove(TAG_BARGAIN_CAP);
        container.setChanged();
        MinecraftServer server = player.getServer();
        if (server != null) {
            String dimId = level.dimension().location().toString();
            unlockAndClear(level, pos, container, server, dimId);
        }
        dev.marrowseal.wardbound.WardHud.message(player, Component.literal("You keep what you earned and owe nothing.")
                .withStyle(ChatFormatting.GRAY), false);
    }

    private static void refreshBargain(ServerPlayer player, BlockPos pos,
                                       RandomizableContainerBlockEntity container) {
        if (player.getServer() == null) return;
        CompoundTag persistent = container.getPersistentData();
        int refreshed = persistent.getInt(TAG_BARGAIN_REFRESHED);
        if (refreshed > 0) {
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal("The hand will not redraw a third time.")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);
            openBargainScreen(player, pos, container);
            return;
        }
        LockData data = LockData.get(player.getServer());
        long seed = persistent.getLong(TAG_BARGAIN_SEED);
        BargainDeal deal = chooseBargainDeal(data, player.getUUID(), seed, true);
        deal = applyQueuedDeckModifiers(data, player.getUUID(), deal, seed ^ 0x51F7A11DL, true);
        deal = appendAnomalySlot(data, player.getUUID(), deal, seed ^ 0x0A0A1E5L, false);
        deal = appendHandUtilitySlots(data, player.getUUID(), deal, seed ^ 0x0EC4051L, false);
        if (deal.cards().isEmpty()) {
            openBargainScreen(player, pos, container);
            return;
        }
        int[] refreshedOffers = deal.cards().stream().mapToInt(b -> b.id).toArray();
        persistent.putIntArray(TAG_BARGAIN_OFFERS, refreshedOffers);
        data.rememberCardOffers(player.getUUID(), refreshedOffers);
        persistent.putInt(TAG_BARGAIN_MODE, deal.mode());
        persistent.putInt(TAG_BARGAIN_REFRESHED, 1);
        container.setChanged();
        player.level().playSound(null, pos, WardSounds.MASTER_MOTIF.get(), SoundSource.BLOCKS, 0.85f, 0.55f);
        dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                        deal.mode() == BARGAIN_DEATH
                                ? "The redraw returns from somewhere below the page. The death hand will not close empty."
                                : deal.mode() == BARGAIN_CURSE
                                ? "The discarded hand comes back blacker. This time, it will not be refused."
                                : "The old hand burns. A new hand is dealt.")
                .withStyle((deal.mode() == BARGAIN_CURSE || deal.mode() == BARGAIN_DEATH) ? ChatFormatting.DARK_RED : ChatFormatting.DARK_PURPLE), false);
        openBargainScreen(player, pos, container);
    }

    private static void acceptBargain(ServerPlayer player, BlockPos pos,
                                      RandomizableContainerBlockEntity container,
                                      ForbiddenBargain bargain, int branch) {
        MinecraftServer server = player.getServer();
        if (server == null || bargain == null) return;

        CompoundTag persistent = container.getPersistentData();
        float current = persistent.getFloat(TAG_MULT);
        // Absolute additions only. Even an already-rich chest gets the same
        // small nudge instead of compounding another percentage on top.
        float rewardAdd = bargain.rewardAdd();
        float wardCap = persistent.contains(TAG_BARGAIN_CAP)
                ? persistent.getFloat(TAG_BARGAIN_CAP)
                : progressionLootCap(LockData.get(server).totalBeaten(player.getUUID()));
        float banked = Math.min(wardCap, RewardBreakdown.clamp(current + rewardAdd));
        persistent.putFloat(TAG_MULT, banked);
        persistent.remove(TAG_BARGAIN_OFFERS);
        persistent.remove(TAG_BARGAIN_SEED);
        persistent.remove(TAG_BARGAIN_MODE);
        persistent.remove(TAG_BARGAIN_REFRESHED);
        persistent.remove(TAG_BARGAIN_CAP);
        container.setChanged();

        LockData data = LockData.get(server);
        UUID id = player.getUUID();
        resetRefuseStreak(player);
        CardBranches.select(player, data, bargain, branch);
        WardHistory.acceptCard(player, bargain);
        prepareCardSignature(player, data, bargain, null);
        CardEchoSystem.beginSignature(data, id, bargain);
        boolean echoed = CardEchoSystem.consumeIfMatching(data, id, bargain);
        if (echoed) CardEchoSystem.strengthen(player, data, bargain);
        CardEchoSystem.ResolutionSnapshot echoSnapshot = echoed ? CardEchoSystem.snapshotResolution(player, data) : null;
        applyCardState(player, data, bargain);
        if (echoed) CardEchoSystem.finishResolution(player, data, bargain, echoSnapshot);

        String dimId = player.level().dimension().location().toString();
        unlockAndClear((ServerLevel) player.level(), pos, container, server, dimId);
        player.level().playSound(null, pos, WardSounds.WARD_SEAL.get(),
                SoundSource.BLOCKS, 0.65f, 0.58f);
        String aftermath = switch (bargain.kind) {
            case REMEDY -> " Something in the old ink comes loose.";
            case CONTRACT -> " The contract begins counting immediately.";
            case RITUAL -> " The ritual has begun; the book will count the work.";
            case COVENANT -> " The covenant begins counting what you were willing to do.";
            case WAGER -> (bargain == ForbiddenBargain.LAST_CANDLE || bargain == ForbiddenBargain.LOADED_DICE
                    || bargain == ForbiddenBargain.MERCYS_DUE)
                    ? " Its terms wait for an ordinary ward."
                    : " Its terms are already in force.";
            case SCAR -> " The card leaves its mark on you.";
            case DEBT -> " The debt remains.";
            case MASTER -> " The maker writes a private law into your ordinary life.";
            case EPIC -> " The rule follows you beyond the ward.";
            case UNIQUE -> " Something with no maker follows you out into the world.";
            case CURSE -> " The curse follows you beyond the ward.";
            case DEATH -> " The page smells of cold earth. Something terrible has agreed to remember you.";
            case REFRESH -> "";
        };
        dev.marrowseal.wardbound.WardHud.message(player, Component.literal(String.format(
                        "%s is accepted. Loot x%.2f is banked.%s Click again to open.",
                        bargain.title, banked, aftermath))
                .withStyle(bargain.kind == ForbiddenBargain.Kind.REMEDY ? ChatFormatting.DARK_GREEN
                        : bargain.kind == ForbiddenBargain.Kind.CONTRACT ? ChatFormatting.BLUE
                        : bargain.kind == ForbiddenBargain.Kind.RITUAL ? ChatFormatting.DARK_AQUA
                        : bargain.kind == ForbiddenBargain.Kind.COVENANT ? ChatFormatting.DARK_RED
                        : bargain.kind == ForbiddenBargain.Kind.CURSE ? ChatFormatting.DARK_RED
                        : bargain.kind == ForbiddenBargain.Kind.EPIC ? ChatFormatting.GOLD
                        : bargain.kind == ForbiddenBargain.Kind.UNIQUE ? ChatFormatting.AQUA
                        : bargain.kind == ForbiddenBargain.Kind.DEATH ? ChatFormatting.DARK_RED
                        : ChatFormatting.DARK_PURPLE), false);
        playCardAcceptanceFeedback(player, bargain);
    }

    private static void savantReactToSignedCard(ServerPlayer player, ForbiddenBargain card) {
        if (card.kind == ForbiddenBargain.Kind.DEATH) {
            dev.marrowseal.wardbound.item.CthulhuEyeItem.speakInsight(player, "signed_death_" + card.id,
                    "That was a Death law. There is no ordinary remedy paragraph because it was not written for your convenience.", 20L * 120L);
        } else if (card.kind == ForbiddenBargain.Kind.COVENANT) {
            dev.marrowseal.wardbound.item.CthulhuEyeItem.speakInsight(player, "signed_covenant_" + card.id,
                    "A covenant is not difficult because the number is large. It is difficult because you must decide the number is worth becoming true.", 20L * 90L);
        } else if (card.kind == ForbiddenBargain.Kind.RITUAL) {
            dev.marrowseal.wardbound.item.CthulhuEyeItem.speakInsight(player, "signed_ritual_" + card.id,
                    "The ritual is environmental, not symbolic. Read the condition before you spend an afternoon performing the wrong gesture.", 20L * 90L);
        } else if (card == ForbiddenBargain.DROWNED_NAME) {
            dev.marrowseal.wardbound.item.CthulhuEyeItem.speakInsight(player, "signed_drowned_name",
                    "That name is useful underwater and embarrassing under an open sun. Location is now part of your anatomy.", 20L * 90L);
        } else if (card == ForbiddenBargain.BLACK_REPRIEVE) {
            dev.marrowseal.wardbound.item.CthulhuEyeItem.speakInsight(player, "signed_black_reprieve",
                    "The Reprieve does not prevent the wound. It merely makes the next few seconds after a bad one less negotiable.", 20L * 90L);
        } else if (card == ForbiddenBargain.HOUSE_OF_ASH) {
            dev.marrowseal.wardbound.item.CthulhuEyeItem.speakInsight(player, "signed_house_of_ash",
                    "You have made fire less persuasive. Do not infer that it has become harmless.", 20L * 90L);
        }
    }

    private static int prepareCardSignature(ServerPlayer player, LockData data, ForbiddenBargain card, CardMaster dealer) {
        int variant = CardEvolution.accept(player, data, card);
        UUID id = player.getUUID();
        // Echo primers are meta-cards.  They arm a future signature but must not
        // themselves count as a Death/Curse/objective law or poison the player's
        // imprint just because their enum Kind matches the family they prime.
        if (CardEchoSystem.isPrimer(card) || card == ForbiddenBargain.THE_RED_PEN) {
            // Deliberately no death resonance, hostile imprint or objective bind.
        } else if (card.isDeath()) {
            data.setUniqueLong(id, "last_death_signature_at", player.level().getGameTime());
            SavantMemory.rememberDeathCard(player, card);
            DeathResonance.add(player, data, 18, "another death-law entered the record");
            PlayerImprint.add(player, data, PlayerImprint.Trace.MARKED, 4, "signed a death-law");
        } else {
            savantReactToSignedCard(player, card);
            if (card.isCurse() || card.kind == ForbiddenBargain.Kind.COVENANT)
                PlayerImprint.add(player, data, PlayerImprint.Trace.MARKED, card.kind == ForbiddenBargain.Kind.COVENANT ? 3 : 2, "kept a hostile clause");
            if (card.isObjectiveCard() || dealer != null)
                PlayerImprint.add(player, data, PlayerImprint.Trace.BOUND, 2, "signed an obligation or dealer-authored clause");
        }
        CardLineage.onSigned(player, data, card, dealer);
        CardEcology.onSigned(player, data, card);
        OccultChainEvents.onCardSigned(player, data, dealer, card);
        return variant;
    }

    static void applyCardState(ServerPlayer player, LockData data, ForbiddenBargain bargain) {
        UUID id = player.getUUID();
        int variant = CardEvolution.activeVariant(data, id, bargain);
        switch (bargain) {
            case BORROWED_BREATH -> data.addBorrowedBreath(id);
            case IRON_DEBT -> data.addIronDebt(id);
            case WATCHING_MARK -> data.addWatchingMark(id);
            case CRIMSON_BALANCE -> data.setCrimsonBalance(id, true);
            case SEVERED_MEASURE -> { data.addHeartDebt(id, 1); data.addLoot25Charges(id, 2); }
            case LAST_CANDLE -> data.addLastCandle(id);
            case GLASS_NERVE -> data.setGlassNerve(id, true);
            case PALE_COVENANT -> data.setPaleCovenant(id, true);
            case OPEN_VEIN -> { data.setOpenVein(id, true); data.addHeartDebt(id, 1); }
            case THIN_BLOOD -> { data.setThinBlood(id, true); data.addLoot15Charges(id, 3); }
            case LOADED_DICE -> data.addLoadedDice(id, variant >= 3 ? 5 : variant >= 2 ? 4 : 3);
            case MERCYS_DUE -> data.addMercysDue(id, variant >= 3 ? 4 : variant >= 2 ? 3 : 2);
            case STILL_HEART -> data.setStillHeart(id, true);
            case RECONCILED_BLOOD -> data.setCrimsonBalance(id, false);
            case HEART_RETURNED -> {
                if (data.hasOpenVein(id)) data.setOpenVein(id, false);
                else if (data.hasBloodTithe(id)) data.setBloodTithe(id, false);
                data.returnHeart(id);
            }
            case NERVE_SETTLED -> data.setGlassNerve(id, false);
            case COVENANT_BROKEN -> data.setPaleCovenant(id, false);
            case HEART_AWAKENED -> data.setStillHeart(id, false);
            case DEBT_UNWRITTEN -> { if (!FreshCardEffects.clearOneLesserBurden(data, id) && !SecondWaveCardEffects.clearOneLesserBurden(data, id) && !ThirdWaveCardEffects.clearOneLesserBurden(data, id) && !FifthWaveCardEffects.clearOneLesserBurden(data, id) && !ApothicCardEffects.clearOneLesserBurden(data, id)) data.clearOnePendingDebt(id); }
            case THICKENED_BLOOD -> data.setThinBlood(id, false);
            case CROOKED_PRIVATE -> { data.setMasterPact(id, MasterSignature.CROOKED.ordinal()); data.addRelation(id, MasterSignature.CROOKED, 5); }
            case VEILED_PRIVATE -> { data.setMasterPact(id, MasterSignature.VEILED.ordinal()); data.addRelation(id, MasterSignature.VEILED, 5); }
            case EXACTING_PRIVATE -> { data.setMasterPact(id, MasterSignature.EXACTING.ordinal()); data.addRelation(id, MasterSignature.EXACTING, 5); }
            case BLACK_DIVIDEND -> data.setBlackHarvest(id, true);
            case SECOND_LEDGER -> data.setSecondEntry(id, true);
            case ABSOLUTION -> {
                data.clearNegativeBargains(id);
                FreshCardEffects.absolve(data, id);
                SecondWaveCardEffects.absolve(data, id);
                ThirdWaveCardEffects.absolve(data, id);
                FifthWaveCardEffects.absolve(data, id);
                ApothicCardEffects.absolve(data, id);
                data.setUnique(id, "bellglass_sight", false);
                data.setUnique(id, "thorn_ledger", false);
                data.setUnique(id, "dustbound_soles", false);
                data.setUnique(id, "lantern_blood", false);
            }
            case BLOOD_TITHE -> { data.addHeartDebt(id, 1); data.setBloodTithe(id, true); data.setCurseDay(id, "blood_tithe", player.level().getDayTime() / 24000L); }
            case DIMINISHED_SHARE -> data.addCurseLoot15Charges(id, 3);
            case FRAIL_HAND -> { data.setBrittlePilgrimage(id, true); data.setCurseDay(id, "brittle", player.level().getDayTime() / 24000L); }
            case ASHEN_TONGUE -> {
                data.setAshenTongue(id, true);
                data.setUniqueLong(id, "ashen_tongue_until", player.level().getGameTime() + 20L * 60L * 10L);
            }
            case REFRESH_HAND -> { }
            case MOONLIT_HUNT -> data.setUnique(id, "moonlit_hunt", true);
            case RED_MARCH -> data.setUnique(id, "red_march", true);
            case FIRST_SUPPER -> data.setUnique(id, "first_supper", true);
            case LAST_WITNESS -> data.setUnique(id, "last_witness", true);
            case EMBER_COUNT -> data.setUniqueInt(id, "ember_hits", variant >= 3 ? 24 : variant >= 2 ? 20 : variant == 1 ? 16 : 12);
            case ORE_WHISPER -> data.setUniqueLong(id, "ore_whisper_until", player.level().getGameTime() + 20L * 60L * (variant >= 3 ? 7L : variant >= 2 ? 6L : variant == 1 ? 5L : 4L));
            case BORROWED_MOMENTUM -> { data.setUniqueLong(id, "momentum_until", player.level().getGameTime() + 20L * (variant >= 3 ? 270L : variant >= 2 ? 240L : variant == 1 ? 210L : 180L)); data.setUniqueInt(id, "momentum_debt", 1); }
            case HUNTERS_DIVIDEND -> data.setUniqueInt(id, "hunter_dividend", variant >= 3 ? 16 : variant >= 2 ? 14 : variant == 1 ? 12 : 10);
            case BELLGLASS_SIGHT -> data.setUnique(id, "bellglass_sight", true);
            case COAL_KISS -> data.setUniqueInt(id, "coal_kiss_hits", variant >= 3 ? 28 : variant >= 2 ? 24 : variant == 1 ? 20 : 16);
            case THORN_LEDGER -> data.setUnique(id, "thorn_ledger", true);
            case HEARTHMARK -> data.setUniqueInt(id, "hearthmark_meals", variant >= 3 ? 14 : variant >= 2 ? 12 : variant == 1 ? 10 : 8);
            case SALT_CIRCLE -> data.setUniqueInt(id, "salt_circle", variant >= 3 ? 14 : variant >= 2 ? 12 : variant == 1 ? 10 : 8);
            case DUSTBOUND_SOLES -> data.setUnique(id, "dustbound_soles", true);
            case BLACK_COMPASS -> data.setUniqueLong(id, "black_compass_until", player.level().getGameTime() + 20L * 60L * (variant >= 3 ? 6L : variant >= 2 ? 5L : variant == 1 ? 4L : 3L));
            case LANTERN_BLOOD -> data.setUnique(id, "lantern_blood", true);
            case IRON_ECHO -> data.setUniqueInt(id, "iron_echo_hits", variant >= 3 ? 16 : variant >= 2 ? 14 : variant == 1 ? 12 : 10);
            case GRAVE_RATION -> data.setUniqueInt(id, "grave_ration", variant >= 3 ? 18 : variant >= 2 ? 16 : variant == 1 ? 14 : 12);
            case POCKET_ECLIPSE -> data.setUniqueLong(id, "pocket_eclipse_until", player.level().getGameTime() + 20L * 60L * (variant >= 3 ? 6L : variant >= 2 ? 5L : variant == 1 ? 4L : 3L));
            case PILGRIMS_LUCK -> data.setUniqueInt(id, "pilgrims_luck_blocks", variant >= 3 ? 56 : variant >= 2 ? 48 : variant == 1 ? 40 : 32);
            case MEMENTO_MORI -> { data.setUnique(id, "memento_mori", true); data.addDeathHeartDebt(id, 2); }
            case BLACK_SUN -> data.setUnique(id, "black_sun", true);
            case COFFIN_ROAD -> data.setUnique(id, "coffin_road", true);
            case GRAVE_BELL -> data.setUnique(id, "grave_bell", true);
            case SHATTER_BELLGLASS -> data.setUnique(id, "bellglass_sight", false);
            case PRUNE_THORNS -> data.setUnique(id, "thorn_ledger", false);
            case CUT_DUST_BINDING -> data.setUnique(id, "dustbound_soles", false);
            case SNUFF_LANTERN -> data.setUnique(id, "lantern_blood", false);
            case ROTTEN_LEDGER -> startObjective(player, data, id, "rotten_ledger");
            case BONE_TALLY -> startObjective(player, data, id, "bone_tally");
            case CREEPER_CLAUSE -> startObjective(player, data, id, "creeper_clause");
            case SILK_WARRANT -> startObjective(player, data, id, "silk_warrant");
            case ENDER_AUDIT -> startObjective(player, data, id, "ender_audit");
            case WITCH_LEDGER -> startObjective(player, data, id, "witch_ledger");
            case STONE_COMMUNION -> startObjective(player, data, id, "stone_communion");
            case VEIN_LITANY -> startObjective(player, data, id, "vein_litany");
            case WOODEN_CONFESSION -> startObjective(player, data, id, "wooden_confession");
            case EARTHEN_VIGIL -> startObjective(player, data, id, "earthen_vigil");
            case OBSIDIAN_PRAYER -> startObjective(player, data, id, "obsidian_prayer");
            case RED_CENSUS -> startObjective(player, data, id, "red_census");
            case IRON_SILENCE -> startObjective(player, data, id, "iron_silence");
            case INNOCENCE_TAX -> startObjective(player, data, id, "innocence_tax");
            case WITCHFIRE_TITHE -> startObjective(player, data, id, "witchfire_tithe");
            case VEIN_DRINKER -> data.setUniqueInt(id, "vein_drinker", variant >= 3 ? 14 : variant >= 2 ? 12 : variant == 1 ? 10 : 8);
            case QUICKSILVER_PRAYER -> { data.setUniqueLong(id, "quicksilver_until", player.level().getGameTime() + 20L * (variant >= 3 ? 210L : variant >= 2 ? 180L : variant == 1 ? 150L : 120L)); data.setUniqueInt(id, "quicksilver_debt", 1); }
            case FERRYMAN_LEDGER -> data.setUniqueInt(id, "ferryman_guard", variant >= 3 ? 8 : variant >= 2 ? 7 : variant == 1 ? 6 : 5);
            case CINDER_VOW -> data.setUnique(id, "cinder_vow", true);
            case SHIVERING_TITHE -> data.setUnique(id, "shivering_tithe", true);
            case THAW_THE_TITHE -> data.setUnique(id, "shivering_tithe", false);
            case GRIM_HARVEST -> startObjective(player, data, id, "grim_harvest");
            case SAND_LITURGY -> startObjective(player, data, id, "sand_liturgy");
            case OSSUARY_VOW -> startObjective(player, data, id, "ossuary_vow");
            case COLD_LEDGER -> data.setUniqueInt(id, "cold_ledger", 8);
            case HOLLOW_LANTERN -> data.setUnique(id, "hollow_lantern", true);
            case SNUFF_HOLLOW -> data.setUnique(id, "hollow_lantern", false);
            case PALE_RATION -> data.setUniqueInt(id, "pale_ration", 8);
            case WARD_OF_BONE -> data.setUnique(id, "bone_ward", true);
            case BREAK_BONE_WARD -> data.setUnique(id, "bone_ward", false);
            case SILENT_DIVIDEND -> data.setUniqueInt(id, "silent_dividend", 12);
            case DROWNED_NAME -> data.setUnique(id, "drowned_name", true);
            case BLACK_REPRIEVE -> data.setUnique(id, "black_reprieve", true);
            case HOUSE_OF_ASH -> data.setUnique(id, "house_of_ash", true);
            case BLOODWELL_REFLEX, LONG_HAND, HARVEST_SHARE, BALLISTIC_SCRIPT, FAR_LEDGER, CROWD_INTEREST, IRON_PULSE
                    -> ProgressionCardEffects.apply(player, data, bargain);
            case PINHOLE_DOCTRINE, POINT_BLANK_RECEIPT, HIGH_ARC_CLAUSE, UPWARD_INTEREST, FIRST_VOLLEY, LAST_BOLT,
                    SOLITARY_MARK, RUNNING_SIGHT, STILL_HAND, PIERCED_CROWD, RED_KNUCKLE, OPENING_CUT,
                    EXECUTIONERS_MARGIN, LONE_DUEL, PRESSED_BLADE, HIGH_GROUND, LOW_ROAD, RUNNING_HAND,
                    STILL_POINT, SECOND_WOUND, ASH_PLATE, LAST_PLATE, ARROW_LEDGER, CLOSE_SEAL,
                    CROWD_SHELTER, SOLITARY_WARD, FALLING_INK, FIRE_MARGIN, BLAST_RECEIPT, NAME_WITHOUT_RECOIL,
                    LONG_STRIDE, SPRINT_CLAUSE, EMPTY_ROAD, HUNTED_ROAD, OPEN_SKY_FOOTNOTE, DEEP_ROAD,
                    FEATHERED_DEBT, CLIMBERS_MARGIN, LONGER_HAND, DUELISTS_REACH, STONE_DIVIDEND, TIMBER_SHARE,
                    MASON_TITHE, FORTUNE_MARGIN, SWIFT_TOOL, HARVEST_MEMORY, GRAVE_DIVIDEND, SCAVENGERS_NAME,
                    CANDLEWORK, QUIET_LEDGER
                    -> ExpandedProgressionCardEffects.apply(player, data, bargain);
            case WRONG_DOOR, TEN_SECONDS_UNWRITTEN, VILLAGE_COLD_SHOULDER, IRON_ACCUSATION,
                    WARDENS_BLIND_SPOT, EMPTY_DEEP, ZOMBIE_ARMISTICE, BONE_TRUCE, CREEPER_COURTESY,
                    SPIDER_TREATY, ENDER_AMNESTY, WITCHS_PRIVILEGE, HOSTILE_CENSUS, FIRE_WITHOUT_FUEL,
                    WRONG_GRAVITY, HUNDRED_STEPS, RED_MINUTE, GLASS_SAINT, SHARED_PAIN, MEASURED_MERCY,
                    TIDAL_BODY, CHORUS_ERROR, RETURN_ADDRESS, LANTERN_BREAK, WHITE_NOISE, BLOOD_MONEY,
                    COIN_EDGE, CHANCE_ENGINE, BLACKOUT, AIR_BORROWED, BURNING_PACT, FROSTED_BLOOD,
                    NIGHT_PASS, DAY_PASS, HOLLOW_SKIN, GRAVE_SILENCE, BEAST_MARCH, GOLEM_ESCORT,
                    GOLEM_ENMITY, VILLAGE_EXILE, VILLAGE_PARDON, CREEPER_BLESSING, FALLING_CROWN,
                    STONE_SKIN, OPEN_SKY, DEEP_BREATH, FOURTH_CARD, FIFTH_CARD, NARROW_HAND,
                    CURSE_DAMPER, CURSE_BAIT, RARE_INK, CLEAN_MARGIN, BLACK_MARGIN, NO_ECHOES,
                    REMEDY_WITNESS, DEALERS_FOURTH, GOLDEN_CUT, DEEP_SHELF, CURSE_LULL,
                    REGISTRY_LOTTERY, EGG_WITH_NO_SHELL, SIXTY_FOURTH_STEP, STORM_RECEIPT,
                    BORROWED_FACE, MOB_EXCHANGE, BAD_RECEIPT, LUCKY_POCKET, STRANGE_INVITATION,
                    CERTAINLY_NOTHING, FOURTH_SHAPE, WHITE_THREAD, SCRIBBLED_RESPITE, PALE_MARGIN -> WildCardEffects.apply(player, data, bargain);
            case QUICKENED_PULSE, STONE_BREATH, SHARPENED_HOUR, CLEAR_EYES, FEATHER_RECEIPT, DIVERS_MARGIN, CINDER_RECEIPT, MINERS_CREDIT, HUNTERS_REBATE, PALE_BANDAGE,
                    BLACK_BREAD, FIRST_CUT_DOUBLED, FIRST_ARROW_DOUBLED, THIN_AIR, STANDING_ORDER, RUNNING_ORDER, OPEN_SKY_LEDGER, DEEP_INK, RED_HARVEST, GRAY_HARVEST,
                    SCAVENGER_CLAUSE, MASONS_LUCK, WOODSMANS_SHARE, FARMERS_MARGIN, IRON_STEP, GLASS_STEP, WARD_LANTERN, QUIET_MOUTH, LOUD_NAME, SECOND_WIND,
                    INKED_APPETITE, CHARCOAL_TONGUE, FROSTED_EDGE, TETHERED_BLOOD, CUT_TETHER, ASHEN_LUNGS, CLEAR_THE_LUNGS, HOLLOW_BONES, FILL_THE_BONES, CLOCKWORK_NERVE,
                    STILL_THE_NERVE, BLOODLESS_VICTORY, WOUNDED_PROFIT, PATIENT_BLADE, MOVING_TARGET, CROWDED_LEDGER, SOLITARY_LEDGER, GOLDEN_HUNGER, PAY_THE_HUNGER, ASH_CROWN,
                    QUENCH_THE_CROWN, LAST_COAL, PALE_RESERVOIR, MIRROR_LEDGER, HUNTERS_MOON, DEEP_SAINT, BLACK_PARDON, NINTH_LIFE, THE_LONG_NIGHT, GRAVE_WALKER
                    -> FreshCardEffects.apply(player, data, bargain);
            case SWIFT_MERCY, IRON_WAKE, MOONWATER_DRAFT, FURNACE_VEIN, QUIET_STEP, RED_HOUR, PALE_STEP, HUNGRY_STEEL, WATCHERS_DRAFT, STONE_CHOIR,
                    RUNNING_DEBT, BLACK_CURRENT, HOLLOW_LIGHT, LAST_MATCH, GRAVE_SALT, ASH_DIVIDEND, FIRST_BLOOD, LAST_ARROW, LONG_BREATH, DEEP_STEP,
                    SKY_STEP, HUNTERS_REST, HUNTERS_RUSH, RED_LEDGER, BLACK_LEDGER, CANDLE_TAX, IRON_HARVEST, STONE_HARVEST, GLASS_RUNNER, HEAVY_HAND,
                    THIN_ARMOR, CORPSE_LANTERN, WOLFS_DEBT, EMPTY_ROOM, CROWDED_ROOM, RAIN_CLERK, SUN_CLERK, MOON_CLERK, BLOOD_CLOCK, CLEAN_HANDS,
                    COLD_IRON, WARM_IRON, PALE_SKIN, COLOR_RETURNED, SALT_LUNGS, FRESH_AIR, EMBER_MORTGAGE, PAY_THE_EMBER, GRAVE_CREDIT, EMPTY_PULSE
                    -> SecondWaveCardEffects.apply(player, data, bargain);
            case KNEELING_FUSE, DROWN_THE_FUSE, THIRTEENTH_STEP, BREAK_THE_COUNT, STILLNESS_TAX, MOVE_THE_INK, BLACK_STATIC, SORT_THE_HAND, CHORUS_DEBT, CLOSE_THE_CHORUS,
                    LAST_FOOTPRINT, SECOND_GRAVITY, WITNESS_MARK, BLACKOUT_CLAUSE, BONE_MAGNET, BLOOD_CLOCK_HAND, STOLEN_COUNTENANCE, HOUSE_ALWAYS_WINS, RETURN_TO_SENDER, ASH_RECOIL,
                    AIRBORNE_LEDGER, QUIET_EXECUTION, RED_PURSUIT, CROW_TOLL, PROJECTILE_AMNESTY, LOANED_MOMENT, DEAD_MANS_MARGIN, CROOKED_PARALLAX, BELL_WITHOUT_SOUND, GRAVE_INTEREST,
                    BLOOD_TELEGRAM, WOUND_EXCHANGE, STILL_POINT_BLACK, BLIND_AUCTION, CANCEL_AUCTION, LAST_WARDEN, FURNACE_HEART, KING_IN_RAGS, IRON_IDOL, LAST_CARD_DRAWN,
                    NULL_SPRINT, WORLD_OWES_NOTHING, RED_ECHO, PALE_RECOIL, FIFTH_TOLL, HOLLOW_CROWN, AFTERIMAGE_DEBT, DEBT_OF_DISTANCE, FINAL_AUCTION, UNWRITTEN_REMAINDER
                    -> ThirdWaveCardEffects.apply(player, data, bargain);
            case THE_RED_PEN -> { }
            case DEBT_ECHO, WAGER_ECHO, SCAR_ECHO, REMEDY_ECHO, CONTRACT_ECHO, RITUAL_ECHO, COVENANT_ECHO, MASTER_ECHO, EPIC_ECHO, UNIQUE_ECHO, CURSE_ECHO, DEATH_ECHO
                    -> CardEchoSystem.arm(player, data, bargain);
            case MIRROR_WRIT, BORROWED_ANATOMY, NINTH_MARGIN, CORPSE_LEDGER, CHAIN_OF_CUSTODY
                    -> FourthWaveCardEffects.apply(player, data, bargain);
            case CLEAN_INTEREST, SECOND_ATTEMPT, THREE_CLEAN_LINES, HURRIED_OATH, WHITE_INK, BOUND_TESTIMONY, DOUBLE_MARGIN,
                    PERFECT_ORRERY, PERFECT_PROCESSION, BLACK_STUDY, CLOSE_THE_BOOK, ASHEN_REBUTTAL, SEVEN_PACES, STILL_WITNESS, HOLLOW_STEP,
                    RED_WAKE, IRON_AFTERTASTE, TIMBER_ECHO, RAIN_WRIT, CENSUS_OF_ONE, EMPTY_HAND_DOCTRINE, FULL_HAND_DOCTRINE, WOUND_CLOCK,
                    STOP_THE_CLOCK, HUNGER_OF_ORDER, BREAK_THE_ORDER, GRAVE_RECEIPT, FINAL_FOOTNOTE, MOON_ARCHIVE, SIXTH_WITNESS
                    -> FifthWaveCardEffects.apply(player, data, bargain);
            case RAZOR_DIVIDEND, SPLINTERED_PLATE, LONGBOW_TESTAMENT, QUICKDRAW_CLAUSE, CRIMSON_ODDS, FROST_WIT, CINDER_WIT, BLOOD_RETURN, PALE_RESERVE, DEEP_MINERS_LEDGER, SCHOLARS_TITHE, DODGERS_INK, PLATEBREAKER_SCRIPT, SUNDERERS_NOTE, OPENING_FEE, HUNTERS_SCRIPT, BLOODLETTERS_MARGIN, WARMTH_AGAINST_STEEL, WINTER_AGAINST_BONE, SWIFT_PICK_CLAUSE, SILVER_LEDGER, SECOND_HEART_ACCOUNTING, DOUBLED_EDGE, IMPOSSIBLE_AIM,
                    RUSTED_EDGE, CLEAN_THE_RUST, SPLIT_NERVE, MEND_THE_NERVE, HOLLOW_MARROW, FILL_THE_MARROW, BROKEN_SIGHT, RESET_THE_SIGHT, ASHEN_PICK, WASH_THE_PICK, OPEN_WOUND_LEDGER, CLOSE_THE_WOUND,
                    RAZOR_DOCTRINE, SUNDERED_CREED, GOLDEN_BALLISTICS, SECOND_STRING, WINTER_TONGUE, CINDER_TONGUE, RED_CATECHISM, LEARNED_GRAVE, STONES_MEMORY, PERFECT_DEFLECTION, VEILED_PROTECTION, HUNGER_FOR_THE_LIVING,
                    RED_LAW, NO_ARMOR_IS_SACRED, ARROW_OF_LAST_ACCOUNT, FROZEN_VERDICT, BURNING_VERDICT, CRIMSON_USURY, HOUSE_TAKES_EXPERIENCE, EMPTY_PLATE_DOCTRINE, BONEBREAKER_COVENANT, BLOOD_IN_EXCESS, EXECUTIONER_OF_FULL_HEALTH, HOUSE_HAS_NUMBERS,
                    GLASS_RAZOR, BALLISTIC_PRAYER, BLACK_ICE_LEDGER, ASHEN_VOLLEY, SURGEONS_MARGIN, GHOST_STEP_LEDGER, PLUNDERED_LESSON, IRON_ALGEBRA, BREAKERS_INTEREST, RED_RESERVOIR, COLD_ACCOUNTING, CINDER_ACCOUNTING, HIGH_VELOCITY_CLAUSE, SNIPERS_DEBT, QUICK_HAND_TAX, PALE_REFLEX, QUARRY_FEVER, EDUCATED_VIOLENCE, MERCYS_INTEREST, HEMORRHAGE_CLAUSE, DEFLECTORS_WAGE, PICK_AND_BLADE, PREDATORS_ARITHMETIC, HOUSE_ACCELERANT, PAPER_SKIN, BIND_THE_SKIN, FROZEN_MARROW, WARM_THE_MARROW, CINDERED_NERVE, COOL_THE_NERVE, GREEDY_PICK, BREAK_THE_GREED, SERRATED_MEMORY, FILE_THE_MEMORY, HUNGRY_QUIVER, FEED_THE_QUIVER, BLACK_LANCET, MERCILESS_GEOMETRY, WINTER_ENGINE, CINDER_ENGINE, VAMPIRE_LEDGER, MASTER_QUARRY, ABSENT_TARGET, PERFECT_TRAJECTORY, PROFANED_AEGIS, RED_INSTRUCTION, PATIENT_HAND, HOUSE_COMPOUND, GLASS_SOVEREIGN, TEETH_BEHIND_STEEL, TERMINAL_BALLISTICS, WINTER_OWNS_BLOOD, ASH_OWNS_BREATH, RED_RESERVOIR_LAW, FINAL_QUARRY, UNTOUCHABLE_DEBT, SHREDDERS_CROWN, OPENING_EXECUTION, LAST_QUIVER, HOUSE_REWRITES_BODY,
                    KILLING_MOMENTUM, FIRST_CUT_CLAUSE, LAST_BREATH_MARGIN, STILL_BALLISTICS, MOVING_TARGET_CLAUSE, QUARRY_TEMPER, FURNACE_ARITHMETIC, WINTER_PULSE, EMPTY_STOMACH_DOCTRINE, FULL_STOMACH_DIVIDEND, MOONSHOT_RECEIPT, SUNBURN_LEDGER, DUELISTS_EXCEPTION, MOB_INTEREST, BROKEN_SHIELD_PREMIUM, HEAVY_POCKETS_CLAUSE, CLEAN_HANDS_CLAUSE, PLATED_DEBT, BLOODHOUND_CALCULUS, HUNTERS_RHYTHM, EXECUTIONER_CLOCK, REDIRECTION_LAW, GLASS_CANNON_ARCHIVE, QUARRY_COMMUNION, EMBER_DEBT_COLLECTOR, RIME_COLLECTOR, LONE_PREDATOR, CROWD_AUDITOR, FULL_QUIVER_LAW, VULTURES_MARGIN, GLASS_BLOOD, THICKEN_GLASS_BLOOD, LEAD_FINGERS, UNCLASP_FINGERS, WHITE_HOT_MARROW, QUENCH_WHITE_HOT_MARROW, DEAD_WINTER_NERVE, WAKE_WINTER_NERVE, AUDITORS_HUNGER, CLOSE_THE_AUDIT, HOLLOW_AIM, CORRECT_THE_AIM, BORROWED_SKIN, RETURN_THE_SKIN, RED_QUARRY, COOL_THE_QUARRY, REDLINE_ANATOMY, PERFECT_MURDER_GEOMETRY, METEOR_QUIVER, HOUSE_BLOOD_BANK, IMPOSSIBLE_WINTER, CREMATION_CLAUSE, QUARRY_OF_FLESH, LAST_ACCOUNT, CRITICAL_LOTTERY, ECHO_CHAMBER, HOUSE_FAVORITE_SEVEN, LAST_ARROW_IN_QUIVER, MERCY_AFTER_MURDER, REVOLVING_DOOR, ZOMBIE_ONE_V_ONE, FREE_DIAMOND, ONE_SKELETON, QUICK_MINING_JOB, SHORT_WALK, FREE_HEALING, ONE_BABY_ZOMBIE, LUCKY_SEVEN, NOTHING_HAPPENS, SMALL_REWARD
                    -> ApothicCardEffects.apply(player, data, bargain);
            case ANOMALY_GILDED, ANOMALY_FERAL, ANOMALY_HOLLOW, ANOMALY_STATIC, ANOMALY_MIRROR, ANOMALY_BLACK
                    -> AnomalyCardSystem.resolve(player, data, bargain);
        }
    }

    private static void startObjective(ServerPlayer player, LockData data, UUID id, String key) {
        data.setUnique(id, "obj_" + key, true);
        data.setUniqueInt(id, "obj_" + key + "_progress", 0);
        data.setUniqueInt(id, "obj_" + key + "_missteps", 0);
        data.setUniqueInt(id, "obj_" + key + "_streak", 0);
        data.setUniqueInt(id, "obj_" + key + "_best_streak", 0);
        long now = player == null ? 0L : player.level().getGameTime();
        data.setUniqueLong(id, "obj_" + key + "_started_at", now);
        data.setUniqueLong(id, "obj_" + key + "_last_progress", Long.MIN_VALUE);
    }

    private static void playCardAcceptanceFeedback(ServerPlayer player, ForbiddenBargain bargain) {
        if (!(player.level() instanceof ServerLevel level) || bargain == null) return;
        double x = player.getX(), y = player.getY() + 1.0, z = player.getZ();
        switch (bargain.kind) {
            case DEBT -> {
                level.playSound(null, player.blockPosition(), WardSounds.MARK.get(), SoundSource.PLAYERS, 0.42f, 0.86f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_DEBT, 0.72f);
            }
            case WAGER -> {
                level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.38f, 0.72f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_WAGER, 0.78f);
            }
            case SCAR -> {
                level.playSound(null, player.blockPosition(), WardSounds.MARK_BAD.get(), SoundSource.PLAYERS, 0.45f, 0.72f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_SCAR, 0.80f);
            }
            case REFRESH -> {
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.42f, 1.38f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_REFRESH, 0.74f);
            }
            case REMEDY -> {
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.55f, 1.22f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.REMEDY, 0.85f);
            }
            case CONTRACT -> {
                level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.52f, 0.92f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.CONTRACT, 0.76f);
            }
            case RITUAL -> {
                level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.46f, 0.76f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_RITUAL, 0.78f);
            }
            case COVENANT -> {
                level.playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 0.48f, 0.58f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_COVENANT, 0.85f);
            }
            case MASTER -> {
                level.playSound(null, player.blockPosition(), WardSounds.MASTER_MOTIF.get(), SoundSource.PLAYERS, 0.75f, 0.88f);
                int masterFx = switch (bargain) {
                    case CROOKED_PRIVATE -> dev.marrowseal.wardbound.net.WardLodestoneFxPacket.MASTER_CROOKED;
                    case VEILED_PRIVATE -> dev.marrowseal.wardbound.net.WardLodestoneFxPacket.MASTER_VEILED;
                    case EXACTING_PRIVATE -> dev.marrowseal.wardbound.net.WardLodestoneFxPacket.MASTER_EXACTING;
                    default -> dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_MASTER;
                };
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y, z), masterFx, 1.0f);
            }
            case EPIC -> {
                level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.65f, 0.72f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_EPIC, 0.94f);
            }
            case UNIQUE -> {
                level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.68f, 0.92f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_UNIQUE, 1.0f);
            }
            case CURSE -> {
                level.playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.32f, 0.58f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_CURSE, 0.9f);
            }
            case DEATH -> {
                level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.34f, 0.52f);
                level.playSound(null, player.blockPosition(), SoundEvents.SOUL_SAND_BREAK, SoundSource.PLAYERS, 0.65f, 0.60f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_DEATH, 1.05f);
            }
            default -> {
                level.playSound(null, player.blockPosition(), WardSounds.MARK.get(), SoundSource.PLAYERS, 0.42f, 0.92f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_MASTER, 0.68f);
            }
        }
        if (player.getServer() != null && CardEvolution.evolvable(bargain)) {
            int revision = CardEvolution.activeVariant(LockData.get(player.getServer()), player.getUUID(), bargain);
            if (revision == 1) {
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 0.38f, 1.34f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y + 0.08, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.WARD_SUCCESS, 0.55f);
            } else if (revision == 2) {
                level.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.34f, 1.18f);
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.28f, 0.82f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y + 0.08, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_RITUAL, 0.62f);
            } else if (revision >= CardEvolution.MAX_REVISION) {
                level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.42f, 0.66f);
                level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.48f, 1.32f);
                dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(level, new net.minecraft.world.phys.Vec3(x, y + 0.08, z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_MASTER, 0.88f);
            }
        }
        dev.marrowseal.wardbound.WardHud.message(player,
                Component.literal("CARD SIGNED // " + bargain.title).withStyle(ChatFormatting.ITALIC), false);
        dev.marrowseal.wardbound.WardHud.message(player,
                Component.literal(CardBranches.activeDescription(LockData.get(player.getServer()), player.getUUID(), bargain)).withStyle(ChatFormatting.GRAY), false);
    }

    /** The lock a re-sealed container ran on its previous pass, or null. */
    private static MinigameType lastGame(RandomizableContainerBlockEntity container) {
        CompoundTag tag = container.getPersistentData();
        if (!tag.contains(TAG_LAST_GAME)) return null;
        return MinigameType.byOrdinal(tag.getInt(TAG_LAST_GAME));
    }

    /**
     * How much harder a container has become from being re-sealed.
     *
     * <p>Greed has to cost something other than exposure, or the correct play is
     * always to push to the cap and the decision stops being one.
     */
    private static float depthDifficulty(RandomizableContainerBlockEntity container) {
        int depth = container.getPersistentData().getInt(TAG_DEPTH);
        return 1f + Math.max(0, depth) * WardConfig.temptDifficultyPerDepth;
    }

    /** What the loot is worth after {@code depth} re-seals. Compounding, so greed accelerates. */
    public static float temptBonus(int depth) {
        return (float) Math.pow(WardConfig.temptMultiplier, Math.max(0, depth));
    }

    /**
     * Re-seals a container the player has already beaten, at a harder setting.
     *
     * <p>Only reachable by sneaking on a container that is sitting in the tempt
     * state, so it can never happen by accident.
     */
    private static void reseal(ServerPlayer player, BlockPos pos,
                              RandomizableContainerBlockEntity container,
                              ItemStack interactionStack) {
        CompoundTag persistent = container.getPersistentData();
        int depth = persistent.getInt(TAG_DEPTH) + 1;

        persistent.putInt(TAG_DEPTH, depth);
        persistent.putBoolean(TAG_SCAR_RESEALED, true);
        persistent.remove(TAG_TEMPT);
        // Possession belongs to the lock that was just beaten, not every later
        // greed re-seal of the same physical chest.
        persistent.remove(TAG_POSSESSED);
        persistent.putBoolean(TAG_LOCKED, true);
        persistent.remove(TAG_MULT);
        persistent.remove(TAG_WHO);
        container.setChanged();

        MinecraftServer server = player.getServer();
        if (server != null) {
            String dimId = player.level().dimension().location().toString();
            LockData data = LockData.get(server);
            data.clear(player.getUUID(), dimId, pos);
            DimSettings settings = WardConfig.forDimension(dimId);
            data.put(player.getUUID(), dimId, pos,
                    newState(player, settings, pos, container, interactionStack));
            PlayerImprint.add(player, data, PlayerImprint.Trace.GREEDY, 2, "re-sealed a resolved ward for a larger share");
        }

        player.level().playSound(null, pos, WardSounds.WARD_SEAL.get(), SoundSource.BLOCKS,
                0.9f, Math.max(0.7f, 1.0f - depth * 0.08f));
        dev.marrowseal.wardbound.WardHud.message(player, Component.literal(String.format(
                        "Sealed again, tighter. Depth %d \u00b7 x%.2f if you take it, "
                                + "the ordinary share if you do not.",
                        depth, temptBonus(depth)))
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);

        openMinigame(player, pos, container);
    }

    /**
     * Delivers the hand's verdict once it has seen enough of your work.
     *
     * <p>Both outcomes are permanent and neither is simply better. Favour is a
     * standing gift: one more mistake on every lock in the world, forever.
     * Contempt is not a punishment so much as a change of relationship - the
     * hand starts making its seals for you specifically, and they are harder and
     * worth considerably more. Some players will want that one.
     */
    private static void judge(ServerPlayer player, LockData data, String house, int known) {
        if (known < WardConfig.verdictAfter || !data.verdict(player.getUUID()).isEmpty()) return;

        boolean favour = data.regard(player.getUUID()) >= WardConfig.verdictThreshold;
        data.setVerdict(player.getUUID(), favour ? "favour" : "contempt");

        player.level().playSound(null, player.blockPosition(),
                (favour ? WardSounds.WARD_BREAK : WardSounds.WARD_SEAL).get(),
                SoundSource.PLAYERS, 0.9f, favour ? 1.15f : 0.65f);

        dev.marrowseal.wardbound.WardHud.message(player, Component.literal(favour
                        ? house + " has watched you work and found nothing to correct. "
                                + "Their seals will not be the last thing you fail."
                        : house + " has watched you work. They are not impressed, "
                                + "and they have begun making seals with you in mind.")
                .withStyle(favour ? ChatFormatting.GOLD : ChatFormatting.DARK_PURPLE), false);
    }

    /**
     * What the Cyclopean Lens is for: reading the ward before commitment.
     *
     * <p>Everything in this mod is entered blind. You commit a charm, and only
     * then find out whether the lock was one you are good at. The lens is the
     * answer to that, and it is deliberately information rather than power: it
     * does not make a single lock easier, it makes the decision before the lock
     * a real one.
     */
    private static void readWard(ServerPlayer player, RandomizableContainerBlockEntity container,
                                 DimSettings settings, ItemStack interactionStack) {
        if (RelicItem.fromInteractionStack(interactionStack, RelicItem.Kind.LENS).isEmpty()) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;
        String dimId = player.level().dimension().location().toString();
        LockData data = LockData.get(server);
        if (!data.has(player.getUUID(), dimId, container.getBlockPos())) return;

        CompoundTag live = data.getOrCreate(player.getUUID(), dimId, container.getBlockPos());
        long lockSeed = live.getLong("seed");
        MinigameType game;
        if (live.contains("game")) {
            game = MinigameType.byOrdinal(live.getInt("game"));
        } else {
            game = directedGame(player, settings, lockSeed, lastGame(container));
            live.putInt("game", game.ordinal());
            data.put(player.getUUID(), dimId, container.getBlockPos(), live);
        }

        // Reading a ward and then deciding whether it is worth your time is
        // exactly the behaviour a sealmaker would hold against you, so it costs
        // regard. Not forbidden - priced. Cherry-picking the easy locks is a
        // legitimate way to play; it simply is not a way to earn anyone's favour.
        if (Sealmakers.house(lockSeed).equals(data.watcher(player.getUUID()))) {
            data.addRegard(player.getUUID(), -1);
        }

        dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                        "The eye reads it: " + Sealmakers.sealName(lockSeed) + ", "
                                + capitalise(game.id) + ", by " + Sealmakers.maker(lockSeed) + ".")
                .withStyle(ChatFormatting.AQUA), false);
        player.level().playSound(null, container.getBlockPos(), WardSounds.HEARTBEAT.get(),
                SoundSource.BLOCKS, 0.4f, 1.45f);

        // Rare, and never explained. The eye tells you what the guild would tell
        // you; every so often it also tells you that something under that name
        // is looking back. It costs nothing extra and changes nothing about the
        // lock - it is only ever information you did not ask for.
        if (RNG.nextFloat() < WardConfig.lensTrueNameChance) {
            String hidden = Sealmakers.trueName(lockSeed);
            MutableComponent glitch = Component.literal("Something else answers to ");
            for (int i = 0; i < hidden.length(); i++) {
                char ch = hidden.charAt(i);
                MutableComponent piece = Component.literal(String.valueOf(ch));
                if (ch != '\'' && ch != '-' && RNG.nextInt(3) == 0) {
                    piece = piece.withStyle(ChatFormatting.OBFUSCATED);
                }
                glitch.append(piece);
            }
            glitch.append(Component.literal("."));
            dev.marrowseal.wardbound.WardHud.message(player, glitch.withStyle(ChatFormatting.DARK_GRAY), false);
        }
    }

    /**
     * How much harder every ward in the world has become, from how many you have
     * broken.
     *
     * <p>The mod had two difficulty curves and both were local: a run of wins in
     * one dimension, and a chest re-sealed by the player's own greed. Neither
     * survives a death or a night's sleep, so a save that is two hundred seals
     * deep played exactly like a save that was ten. This is the long one. It is
     * gentle per step and it never resets.
     *
     * <p>Loot rises faster than difficulty on purpose: the late game should be
     * harder AND better paid, or the correct play is to stop opening chests.
     */
    public static int tierOf(int totalBeaten) {
        if (totalBeaten < WardConfig.tierStep) return 0;
        return Math.min(WardConfig.tierMax, totalBeaten / WardConfig.tierStep);
    }

    public static float tierDifficulty(int tier) {
        return 1f + tier * WardConfig.tierDifficultyStep;
    }

    public static float tierLoot(int tier) {
        return 1f + tier * WardConfig.tierLootStep;
    }

    /**
     * Long-form reward ceiling for large modpacks where structure chests are plentiful.
     * It limits only positive multipliers; penalties remain untouched. Extreme rewards
     * therefore unlock as a consequence of experience rather than appearing in chest one.
     */
    public static float progressionLootCap(int totalBeaten) {
        if (!WardConfig.progressionLootCapsEnabled) return Math.max(1f, WardConfig.maxLootMultiplier);
        float cap = totalBeaten < 40 ? 1.05f
                : totalBeaten < 90 ? 1.08f
                : totalBeaten < 180 ? 1.12f
                : totalBeaten < 300 ? 1.18f
                : totalBeaten < 430 ? 1.25f
                : totalBeaten < 600 ? 1.35f
                : totalBeaten < 760 ? 1.45f
                : totalBeaten < 900 ? 1.55f
                : totalBeaten < 1100 ? 1.70f
                : totalBeaten < 1300 ? 1.90f
                : totalBeaten < 1500 ? 2.15f
                : totalBeaten < 1800 ? 2.40f
                : totalBeaten < 2200 ? 2.75f
                : Math.max(1f, WardConfig.maxLootMultiplier);
        return Math.min(Math.max(1f, WardConfig.maxLootMultiplier), cap);
    }

    public static float progressionLootLimit(float multiplier, int totalBeaten) {
        if (multiplier <= 1.0f) return multiplier;
        return Math.min(multiplier, progressionLootCap(totalBeaten));
    }

    /** What the world calls you at each tier. Said once, when you reach it. */
    private static final String[] TIER_NAMES = {
            "unremarked", "noticed", "marked", "known", "attended to", "expected",
            "watched", "named", "reckoned with", "counted", "recognized",
            "spoken of", "owed", "remembered", "measured", "accounted for",
            "written down", "kept", "answered for", "beyond counting",
            "no longer a stranger",
    };

    private static void announceTier(ServerPlayer player, int before, int after) {
        if (after <= before || after >= TIER_NAMES.length) return;
        dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                        "The wards are getting harder. You are " + TIER_NAMES[after] + " now.")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
    }

    private static void announceWardProgressionUnlock(ServerPlayer player, int before, int after) {
        if (after <= before) return;
        milestone(player, before, after, WardConfig.normalCardsAfterBeaten,
                "A card table has finally taken notice of your work.", ChatFormatting.DARK_PURPLE);
        milestone(player, before, after, WardConfig.afflictionAfterBeaten,
                "Some seals have started carrying conditions of their own.", ChatFormatting.GRAY);
        milestone(player, before, after, WardConfig.fieldCardAfterBeaten,
                "Something has begun leaving sealed cards among ordinary deaths.", ChatFormatting.DARK_PURPLE);
        milestone(player, before, after, WardConfig.watcherAfter,
                "One maker's handwriting has begun finding you more often than coincidence should allow.", ChatFormatting.DARK_AQUA);
        milestone(player, before, after, WardConfig.chainAfterBeaten,
                "Some broken seals now point toward another made by the same hand.", ChatFormatting.DARK_AQUA);
        milestone(player, before, after, WardConfig.masterCardsAfterBeaten,
                "Some hands now know enough about you to write a law personally.", ChatFormatting.YELLOW);
        milestone(player, before, after, WardConfig.contractCardsAfterBeaten,
                "The table has begun writing obligations that remain after the chest is closed.", ChatFormatting.GRAY);
        milestone(player, before, after, WardConfig.possessedAfterBeaten,
                "Some seals will move before you touch them now.", ChatFormatting.DARK_PURPLE);
        milestone(player, before, after, WardConfig.curseCardsAfterBeaten,
                "The card table has learned how to close around a choice.", ChatFormatting.DARK_RED);
        milestone(player, before, after, WardConfig.emberAfterBeaten,
                "Some Nether spoils can now carry a slower, older ember.", ChatFormatting.GOLD);
        milestone(player, before, after, WardMeasureSystem.UNLOCK_AFTER,
                "The three hands can now measure your history, not only the ward in front of you.", ChatFormatting.DARK_AQUA);
        milestone(player, before, after, WardConfig.bonusFreshRollAfterBeaten,
                "Longer practice has begun opening an occasional extra place at the table.", ChatFormatting.AQUA);
        milestone(player, before, after, WardConfig.ritualCardsAfterBeaten,
                "Some obligations now demand a ritual instead of a single act.", ChatFormatting.DARK_PURPLE);
        milestone(player, before, after, WardConfig.unsignedAfterBeaten,
                "Not every seal you meet from here will carry a maker's name.", ChatFormatting.DARK_PURPLE);
        milestone(player, before, after, WardConfig.mutationTier1AfterBeaten,
                "Old wards can now keep their first physical mutations; practiced disciplines may also surface expert and corrupted forms.", ChatFormatting.GRAY);
        milestone(player, before, after, WardConfig.epicCardsAfterBeaten,
                "The table has begun admitting laws that reach beyond the next ward.", ChatFormatting.GOLD);
        milestone(player, before, after, WardConfig.deceptionUnlockAfter,
                "Some mastered minigames can now lie about the rules they appear to present.", ChatFormatting.DARK_PURPLE);
        milestone(player, before, after, WardConfig.covenantCardsAfterBeaten,
                "The table can now bind several acts beneath one covenant.", ChatFormatting.DARK_RED);
        milestone(player, before, after, WardConfig.hybridUnlockAfter,
                "Mastered ward mechanisms can now be spliced into hybrid rounds.", ChatFormatting.DARK_AQUA);
        milestone(player, before, after, WardConfig.mutationTier2AfterBeaten,
                "A sufficiently scarred ward can now become something deeper.", ChatFormatting.DARK_PURPLE);
        milestone(player, before, after, WardConfig.uniqueCardsAfterBeaten,
                "Some cards no longer appear to have a maker at all.", ChatFormatting.AQUA);
        milestone(player, before, after, WardConfig.eldritchAfterBeaten,
                "The mechanisms have had enough examples to begin rewriting themselves.", ChatFormatting.DARK_PURPLE);
        milestone(player, before, after, WardConfig.eyeAfterBeaten,
                "Something observant can now answer from the End when the right relic is found.", ChatFormatting.DARK_AQUA);
        milestone(player, before, after, WardConfig.mutationTier3AfterBeaten,
                "The oldest wards can now remember enough to become truly wrong.", ChatFormatting.DARK_RED);
        milestone(player, before, after, WardConfig.deathCardsAfterBeaten,
                "A colder hand has found a place at the card table.", ChatFormatting.DARK_RED);

        // Individual shelves now continue growing after their headline unlock.
        // These deliberately describe a change in circulation without naming the
        // exact card, preserving discovery while making long progression legible.
        milestone(player, before, after, 40,
                "The margins have begun circulating clauses that were not present in the first hands.", ChatFormatting.GRAY);
        milestone(player, before, after, 99,
                "Some newer cards now arrive with a wound and its counter-clause written separately.", ChatFormatting.DARK_AQUA);
        milestone(player, before, after, 162,
                "The table has learned to price ordinary acts like meals and kills as longer accounts.", ChatFormatting.GRAY);
        milestone(player, before, after, 260,
                "A harder family of persistent marks has entered circulation.", ChatFormatting.DARK_PURPLE);
        milestone(player, before, after, 440,
                "The later wagers are beginning to reward cadence instead of simple completion.", ChatFormatting.AQUA);
        milestone(player, before, after, 655,
                "A world-law with no interest in dry land has appeared somewhere in the deck.", ChatFormatting.DARK_AQUA);
        milestone(player, before, after, 764,
                "The table can now write protection that begins after the wound.", ChatFormatting.GOLD);
        milestone(player, before, after, 864,
                "Fire has acquired a second legal interpretation in the margins.", ChatFormatting.DARK_RED);
        milestone(player, before, after, 967,
                "The Death shelf has not finished growing.", ChatFormatting.DARK_RED);
        milestone(player, before, after, 1056,
                "A bell has finally entered the coldest part of the deck.", ChatFormatting.DARK_RED);

        milestone(player, before, after, WardConfig.cthulhuAfterBeaten,
                "Something older has had time to notice the pattern.", ChatFormatting.DARK_AQUA);
    }

    private static void milestone(ServerPlayer player, int before, int after, int threshold,
                                  String text, ChatFormatting color) {
        if (threshold <= 0 || before >= threshold || after < threshold) return;
        WardHud.message(player, Component.literal(text).withStyle(color, ChatFormatting.ITALIC), false);
    }

    /**
     * Opens a lock straight away for testing.
     *
     * <p>Deliberately not wired to anything: no chest, no charm, no streak, no
     * standing. It is a rig for looking at a game, not a way to farm one.
     */
    private static int debugPlay(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                 float difficulty, int value)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "game");
        MinigameType game = MinigameType.byId(id, null);
        if (game == null) {
            ctx.getSource().sendFailure(Component.literal("No lock called '" + id + "'."));
            return 0;
        }

        DimSettings settings = WardConfig.forDimension(
                player.level().dimension().location().toString());
        OpenMinigamePacket pkt = new OpenMinigamePacket(
                player.blockPosition(), value, game.ordinal(),
                settings.lives, settings.lives,
                player.level().getGameTime() * 31L + player.getId(),
                0, difficulty * WardConfig.tuning(game).difficultyScale, 1.0f,
                player.level().dimension().location().toString(), 0, 0, 0, false, 0,
                WardConfig.tuning(game).timeScale, WardConfig.tuning(game).speedScale,
                WardConfig.tuning(game).inputWindowScale, 0, 0,
                false, 0, 0,
                false, false, 0,
                0, false, false,
                false, 0f, 0f, 0, 1f, false, 0, 0, 0, MinigameType.DRUM.ordinal(), MinigameType.DRUM.ordinal());
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
        return 1;
    }


    private static int debugCthulhu(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                    float difficulty, int value)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DimSettings settings = WardConfig.forDimension(
                player.level().dimension().location().toString());
        LockData debugData = LockData.get(player.getServer());
        int debugExamTier = MinigameMastery.examTier(debugData, player.getUUID());
        MinigameType debugWeak = MinigameMastery.weakestDiscipline(debugData, player.getUUID());
        MinigameType debugStrong = MinigameMastery.strongestDiscipline(debugData, player.getUUID());
        OpenMinigamePacket pkt = new OpenMinigamePacket(
                player.blockPosition(), value, MinigameType.CTHULHUS_GAME.ordinal(),
                settings.lives, settings.lives,
                player.level().getGameTime() * 71L + player.getId(),
                0,
                difficulty * WardConfig.tuning(MinigameType.CTHULHUS_GAME).difficultyScale * WardConfig.cthulhuWardDifficulty,
                Math.min(WardConfig.maxLootMultiplier,
                        RewardBreakdown.preview(MinigameType.CTHULHUS_GAME, settings, 0, Charm.NONE, 0,
                                0, 0, 0, false, false, false) * WardConfig.cthulhuWardLoot),
                player.level().dimension().location().toString(),
                0, 0, 0, false, 0,
                WardConfig.tuning(MinigameType.CTHULHUS_GAME).timeScale,
                WardConfig.tuning(MinigameType.CTHULHUS_GAME).speedScale,
                WardConfig.tuning(MinigameType.CTHULHUS_GAME).inputWindowScale,
                0, 0,
                false, 0, 0,
                false, false, 0,
                0, false, false,
                false, 0f, 0f, 0, 1f, false, 0, debugExamTier, 0, debugWeak.ordinal(), debugStrong.ordinal());
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
        return 1;
    }

    private static int debugEldritch(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                     float difficulty, int value, int stage, int total)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "game");
        MinigameType game = MinigameType.byId(id, null);
        if (game == null) {
            ctx.getSource().sendFailure(Component.literal("No lock called '" + id + "'."));
            return 0;
        }
        total = Math.max(2, total);
        stage = Math.max(1, Math.min(stage, total));
        DimSettings settings = WardConfig.forDimension(
                player.level().dimension().location().toString());
        OpenMinigamePacket pkt = new OpenMinigamePacket(
                player.blockPosition(), value, game.ordinal(),
                settings.lives, settings.lives,
                player.level().getGameTime() * 53L + player.getId(),
                0, difficulty * WardConfig.tuning(game).difficultyScale,
                Math.min(WardConfig.maxLootMultiplier,
                        (stage == total ? WardConfig.eldritchWardLoot : 1.0f)),
                player.level().dimension().location().toString(), 0, 0, 0, false, 0,
                WardConfig.tuning(game).timeScale, WardConfig.tuning(game).speedScale,
                WardConfig.tuning(game).inputWindowScale, 0, 0,
                true, stage, total,
                false, false, 0,
                0, false, false,
                false, 0f, 0f, 0, 1f, false, 0, 0, 0, MinigameType.DRUM.ordinal(), MinigameType.DRUM.ordinal());
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
        return 1;
    }

    private static int debugPossessed(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                      float difficulty, int value)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "game");
        MinigameType game = MinigameType.byId(id, null);
        if (game == null) return 0;
        DimSettings settings = WardConfig.forDimension(player.level().dimension().location().toString());
        OpenMinigamePacket pkt = new OpenMinigamePacket(
                player.blockPosition(), value, game.ordinal(), settings.lives, settings.lives,
                player.level().getGameTime() * 71L + player.getId(), 0,
                difficulty * WardConfig.tuning(game).difficultyScale * 1.05f,
                Math.min(WardConfig.maxLootMultiplier, WardConfig.possessedWardLoot),
                player.level().dimension().location().toString(), 0, 0, 0, false, 0,
                WardConfig.tuning(game).timeScale, WardConfig.tuning(game).speedScale,
                WardConfig.tuning(game).inputWindowScale, 0, 0,
                false, 0, 0, true, false, 0,
                0, false, false,
                false, 0f, 0f, 0, 1f, false, 0, 0, 0, MinigameType.DRUM.ordinal(), MinigameType.DRUM.ordinal());
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
        return 1;
    }

    private static BlockPos debugCardPreviewPos(ServerPlayer player) {
        BlockPos debugPos = player.blockPosition().above();
        for (int dy = 1; dy <= 6; dy++) {
            BlockPos candidate = player.blockPosition().above(dy);
            BlockEntity candidateBe = player.level().getBlockEntity(candidate);
            if (!(candidateBe instanceof RandomizableContainerBlockEntity)) {
                debugPos = candidate;
                break;
            }
        }
        return debugPos;
    }

    private static int debugDealModeFor(ForbiddenBargain card) {
        if (card == null) return BARGAIN_NORMAL;
        return switch (card.kind) {
            case EPIC -> BARGAIN_EPIC;
            case CURSE -> BARGAIN_CURSE;
            case UNIQUE -> BARGAIN_UNIQUE;
            case DEATH -> BARGAIN_DEATH;
            case CONTRACT -> BARGAIN_CONTRACT;
            case RITUAL -> BARGAIN_RITUAL;
            case COVENANT -> BARGAIN_COVENANT;
            default -> BARGAIN_NORMAL;
        };
    }

    private static long debugMakerSeed(ServerPlayer player, ForbiddenBargain card) {
        if (card == ForbiddenBargain.CROOKED_PRIVATE) return CardMaster.PALE_GAMBLER.visualSeed();
        if (card == ForbiddenBargain.VEILED_PRIVATE) return CardMaster.ASHEN_CURATOR.visualSeed();
        if (card == ForbiddenBargain.EXACTING_PRIVATE) return CardMaster.MOURNING_NOTARY.visualSeed();
        return player.level().getGameTime() * 37L + player.getId();
    }

    private static int openCardPreview(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                       java.util.List<ForbiddenBargain> source, int variant, String label)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (source == null || source.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No cards matched that preview."));
            return 0;
        }
        int shown = Math.min(4, source.size());
        int[] ids = new int[shown];
        float[] rewards = new float[shown];
        int[] variants = new int[shown];
        float banked = 1.75f;
        for (int i = 0; i < shown; i++) {
            ForbiddenBargain card = source.get(i);
            ids[i] = card.id;
            rewards[i] = Math.max(0f, RewardBreakdown.clamp(banked + card.rewardAdd()) - banked);
            variants[i] = Math.max(0, Math.min(CardEvolution.MAX_REVISION, variant));
        }
        ForbiddenBargain first = source.get(0);
        int mode = debugDealModeFor(first);
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenBargainPacket(debugCardPreviewPos(player), ids, rewards, variants, banked,
                        debugMakerSeed(player, first), mode, false, label == null ? "PROGRESSION DEBUG" : label));
        return 1;
    }

    private static int debugCardsPage(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int page)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        java.util.List<ForbiddenBargain> all = java.util.Arrays.stream(ForbiddenBargain.values())
                .filter(c -> c.kind != ForbiddenBargain.Kind.REFRESH)
                .sorted(java.util.Comparator.comparingInt(c -> c.id))
                .toList();
        int pages = Math.max(1, (all.size() + 3) / 4);
        int clean = Math.max(1, Math.min(pages, page));
        int from = (clean - 1) * 4;
        int to = Math.min(all.size(), from + 4);
        ctx.getSource().sendSuccess(() -> Component.literal("CARD CATALOG // page " + clean + "/" + pages
                + " // progression gates ignored").withStyle(ChatFormatting.DARK_AQUA), false);
        return openCardPreview(ctx, all.subList(from, to), 0, "CARD CATALOG // " + clean + "/" + pages);
    }

    private static ForbiddenBargain.Kind debugKind(String raw) {
        if (raw == null) return null;
        try { return ForbiddenBargain.Kind.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private static int debugCardsKind(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                      String rawKind, int page)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ForbiddenBargain.Kind kind = debugKind(rawKind);
        if (kind == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown card kind. Use debt, wager, scar, remedy, contract, ritual, covenant, master, epic, unique, curse or death."));
            return 0;
        }
        java.util.List<ForbiddenBargain> all = java.util.Arrays.stream(ForbiddenBargain.values())
                .filter(c -> c.kind == kind)
                .sorted(java.util.Comparator.comparingInt((ForbiddenBargain c) -> c.minResolved).thenComparingInt(c -> c.id))
                .toList();
        if (all.isEmpty()) return 0;
        int pages = Math.max(1, (all.size() + 3) / 4);
        int clean = Math.max(1, Math.min(pages, page));
        int from = (clean - 1) * 4;
        int to = Math.min(all.size(), from + 4);
        ctx.getSource().sendSuccess(() -> Component.literal("CARD KIND // " + kind.name() + " // page " + clean + "/" + pages
                + " // progression gates ignored").withStyle(ChatFormatting.DARK_AQUA), false);
        return openCardPreview(ctx, all.subList(from, to), 0, "DEBUG // " + kind.name());
    }

    private static int debugCardId(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int id, int variant)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ForbiddenBargain card = ForbiddenBargain.byId(id);
        if (card == null) {
            ctx.getSource().sendFailure(Component.literal("No ForbiddenBargain card with id " + id + "."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("CARD ID " + card.id + " // " + card.title
                + " // " + card.kind + " // emergence " + card.minResolved + " wards // revision " + variant)
                .withStyle(ChatFormatting.AQUA), false);
        return openCardPreview(ctx, java.util.List.of(card), variant, "CARD ID // " + card.id);
    }

    private static boolean debugShelfUnlocked(ForbiddenBargain card, int wards) {
        if (card == null || wards < card.minResolved) return false;
        // Anomalies are deliberately a parallel hidden progression. They are typed UNIQUE for
        // presentation, but their 140/240/360/520/760/1050 shelves must not inherit the normal
        // 600-ward Unique gate in the debug progression report.
        if (AnomalyCardSystem.isAnomaly(card)) return wards >= card.minResolved;
        if (wards < WardConfig.normalCardsAfterBeaten) return false;
        return switch (card.kind) {
            case MASTER -> wards >= WardConfig.masterCardsAfterBeaten;
            case CONTRACT -> wards >= WardConfig.contractCardsAfterBeaten;
            case RITUAL -> wards >= WardConfig.ritualCardsAfterBeaten;
            case COVENANT -> wards >= WardConfig.covenantCardsAfterBeaten;
            case CURSE -> wards >= WardConfig.curseCardsAfterBeaten;
            case EPIC -> wards >= WardConfig.epicCardsAfterBeaten;
            case UNIQUE -> wards >= WardConfig.uniqueCardsAfterBeaten;
            case DEATH -> wards >= WardConfig.deathCardsAfterBeaten;
            default -> true;
        };
    }

    private static int debugProgressionSummary(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LockData data = LockData.get(ctx.getSource().getServer());
        UUID id = player.getUUID();
        int wards = data.totalBeaten(id);
        int maxEmergence = 0, unlocked = 0;
        for (ForbiddenBargain card : ForbiddenBargain.values()) {
            maxEmergence = Math.max(maxEmergence, card.minResolved);
            if (debugShelfUnlocked(card, wards)) unlocked++;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("WARDBOUND PROGRESSION // DEBUG VIEW").withStyle(ChatFormatting.GOLD), false);
        int finalMaxEmergence = maxEmergence, finalUnlocked = unlocked;
        ctx.getSource().sendSuccess(() -> Component.literal("Wards resolved: " + wards + " // emergence shelf " + finalUnlocked + "/"
                + ForbiddenBargain.values().length + " cards // final shelf " + finalMaxEmergence + " wards"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Field cards opened: " + data.uniqueInt(id, "field_cards_opened")
                + " // field pity " + data.uniqueInt(id, "field_card_pity") + "/" + CardBalance.FIELD_PITY_GUARANTEE
                + " // death pity " + data.uniqueInt(id, "death_hand_pity") + "/" + CardBalance.DEATH_PITY_GUARANTEE), false);
        for (CardMaster master : CardMaster.values()) {
            int defeated = switch (master) {
                case ASHEN_CURATOR -> data.uniqueInt(id, "curator_defeated");
                case MOURNING_NOTARY -> data.uniqueInt(id, "notary_defeated");
                case PALE_GAMBLER -> data.uniqueInt(id, "gambler_defeated");
            };
            String line = master.title + " // known " + master.known(data, id) + " // story " + MasterStory.chapter(data, id, master)
                    + "/" + MasterStory.MAX_CHAPTER + " // favor " + master.relation(data, id)
                    + " // audiences " + data.uniqueInt(id, "dealer_audiences_" + master.id)
                    + " // accepted " + data.uniqueInt(id, "dealer_acceptances_" + master.id)
                    + " // defeated " + defeated;
            ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Chains // Black Table " + OccultChainEvents.stage(data, id, OccultChainEvents.ChainEvent.BLACK_TABLE)
                + "/5 // Ash Margin " + OccultChainEvents.stage(data, id, OccultChainEvents.ChainEvent.ASH_MARGIN)
                + "/5 // Witness Sequence " + OccultChainEvents.stage(data, id, OccultChainEvents.ChainEvent.WITNESS_SEQUENCE)
                + "/5 // objectives " + data.uniqueInt(id, "objectives_completed_total")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Silas // stage " + SilasProgression.stage(data, id)
                + " // fragments " + Integer.bitCount(dev.marrowseal.wardbound.champion.SilasLore.mask(data, id)) + "/"
                + dev.marrowseal.wardbound.champion.SilasLore.Fragment.values().length), false);
        ctx.getSource().sendSuccess(() -> Component.literal(dev.marrowseal.wardbound.ancientsmith.AncientSmithProgression.status(player)), false);
        return 1;
    }

    private static void debugMasterState(LockData data, UUID id, CardMaster master,
                                         int chapter, int relation, int audiences, int accepted) {
        data.setUnique(id, "dealer_known_" + master.id, chapter > 0 || audiences > 0 || accepted > 0);
        data.setUniqueInt(id, "dealer_story_" + master.id, Math.max(0, Math.min(MasterStory.MAX_CHAPTER, chapter)));
        data.setUniqueInt(id, "dealer_relation_" + master.id, Math.max(-12, Math.min(20, relation)));
        data.setUniqueInt(id, "dealer_audiences_" + master.id, Math.max(0, audiences));
        data.setUniqueInt(id, "dealer_acceptances_" + master.id, Math.max(0, accepted));
    }

    private static int debugProgressionPreset(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String raw)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LockData data = LockData.get(ctx.getSource().getServer());
        UUID id = player.getUUID();
        String preset = raw == null ? "fresh" : raw.toLowerCase(java.util.Locale.ROOT);
        int wards, opened, deathPity, objectives, chapter, relation, audiences, accepted, chain;
        switch (preset) {
            case "fresh" -> { wards = 0; opened = 0; deathPity = 0; objectives = 0; chapter = 0; relation = 0; audiences = 0; accepted = 0; chain = 0; }
            case "early" -> { wards = 120; opened = 15; deathPity = 0; objectives = 4; chapter = 2; relation = 5; audiences = 4; accepted = 2; chain = 0; }
            case "mid" -> { wards = 450; opened = 45; deathPity = 0; objectives = 12; chapter = 4; relation = 12; audiences = 12; accepted = 8; chain = 2; }
            case "late" -> { wards = 900; opened = 60; deathPity = 24; objectives = 28; chapter = 6; relation = 18; audiences = 20; accepted = 16; chain = 4; }
            case "endgame" -> { wards = 1500; opened = 90; deathPity = CardBalance.DEATH_PITY_GUARANTEE; objectives = 48; chapter = 7; relation = 20; audiences = 30; accepted = 22; chain = 5; }
            case "max", "cosmic" -> { wards = 2200; opened = 120; deathPity = CardBalance.DEATH_PITY_GUARANTEE; objectives = 64; chapter = 7; relation = 20; audiences = 40; accepted = 30; chain = 5; }
            default -> {
                ctx.getSource().sendFailure(Component.literal("Preset must be fresh, early, mid, late, endgame, cosmic or max."));
                return 0;
            }
        }
        data.setTotalBeaten(id, wards);
        data.setUniqueInt(id, "field_cards_opened", opened);
        int fieldPity = switch (preset) {
            case "early" -> 60;
            case "mid" -> 140;
            case "late" -> 300;
            case "endgame", "max", "cosmic" -> CardBalance.FIELD_PITY_GUARANTEE;
            default -> 0;
        };
        data.setUniqueInt(id, "field_card_pity", fieldPity);
        data.setUniqueInt(id, "death_hand_pity", deathPity);
        data.setUniqueInt(id, "objectives_completed_total", objectives);
        data.setUniqueInt(id, "occult_chain_black_table", chain);
        data.setUniqueInt(id, "occult_chain_ash_margin", chain);
        data.setUniqueInt(id, "occult_chain_witness_sequence", chain);
        for (CardMaster master : CardMaster.values()) debugMasterState(data, id, master, chapter, relation, audiences, accepted);
        ctx.getSource().sendSuccess(() -> Component.literal("Progression preset applied: " + preset.toUpperCase(java.util.Locale.ROOT)
                + " // use /wardbound progression summary to inspect it.").withStyle(ChatFormatting.GOLD), false);
        return debugProgressionSummary(ctx);
    }

    private static int debugProgressionWards(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int value)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LockData.get(ctx.getSource().getServer()).setTotalBeaten(player.getUUID(), value);
        ctx.getSource().sendSuccess(() -> Component.literal("Wards resolved set to " + value + ". Card emergence shelves update immediately."), false);
        return 1;
    }

    private static CardMaster debugMaster(String raw) {
        if (raw == null) return null;
        return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
            case "curator", "ashen_curator" -> CardMaster.ASHEN_CURATOR;
            case "notary", "mourning_notary" -> CardMaster.MOURNING_NOTARY;
            case "gambler", "pale_gambler" -> CardMaster.PALE_GAMBLER;
            default -> null;
        };
    }

    private static int debugProgressionMaster(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                              String field, int value)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LockData data = LockData.get(ctx.getSource().getServer());
        CardMaster master = debugMaster(StringArgumentType.getString(ctx, "master"));
        if (master == null) return 0;
        String key = switch (field) {
            case "chapter" -> "dealer_story_" + master.id;
            case "relation" -> "dealer_relation_" + master.id;
            case "audiences" -> "dealer_audiences_" + master.id;
            case "accepted" -> "dealer_acceptances_" + master.id;
            case "defeated" -> switch (master) {
                case ASHEN_CURATOR -> "curator_defeated";
                case MOURNING_NOTARY -> "notary_defeated";
                case PALE_GAMBLER -> "gambler_defeated";
            };
            default -> null;
        };
        if (key == null) return 0;
        data.setUniqueInt(player.getUUID(), key, value);
        if (value > 0) data.setUnique(player.getUUID(), "dealer_known_" + master.id, true);
        ctx.getSource().sendSuccess(() -> Component.literal(master.title + " // " + field + " = " + value), false);
        return 1;
    }

    private static OccultChainEvents.ChainEvent debugChain(String raw) {
        if (raw == null) return null;
        return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
            case "black_table", "black" -> OccultChainEvents.ChainEvent.BLACK_TABLE;
            case "ash_margin", "ash" -> OccultChainEvents.ChainEvent.ASH_MARGIN;
            case "witness_sequence", "witness" -> OccultChainEvents.ChainEvent.WITNESS_SEQUENCE;
            default -> null;
        };
    }

    private static int debugProgressionChain(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int stage)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LockData data = LockData.get(ctx.getSource().getServer());
        OccultChainEvents.ChainEvent chain = debugChain(StringArgumentType.getString(ctx, "chain"));
        if (chain == null) return 0;
        int clean = Math.max(0, Math.min(chain.maxStage, stage));
        data.setUniqueInt(player.getUUID(), "occult_chain_" + chain.id, clean);
        ctx.getSource().sendSuccess(() -> Component.literal(chain.title + " stage = " + clean + "/" + chain.maxStage), false);
        return 1;
    }

    /** Opens the bargain cards as a pure visual test. No container is tagged, so
     * choosing a card cannot create a debt or alter loot. */
    private static int debugCards(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                  String requestedStyle)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        long makerSeed = player.level().getGameTime() * 37L + player.getId();
        if (requestedStyle != null && !requestedStyle.isBlank()) {
            MasterSignature wanted = switch (requestedStyle.toLowerCase(java.util.Locale.ROOT)) {
                case "crooked" -> MasterSignature.CROOKED;
                case "veiled" -> MasterSignature.VEILED;
                case "exacting" -> MasterSignature.EXACTING;
                default -> null;
            };
            if (wanted == null) {
                ctx.getSource().sendFailure(Component.literal("Style must be crooked, veiled or exacting."));
                return 0;
            }
            for (long s = 1; s < 100000; s++) {
                if (MasterSignature.ofSeed(s) == wanted) { makerSeed = s; break; }
            }
        }
        LockData data = LockData.get(ctx.getSource().getServer());
        int oldDeathPity = data.uniqueInt(player.getUUID(), "death_hand_pity");
        BargainDeal debugDeal = chooseBargainDeal(data, player.getUUID(), makerSeed, false);
        // Debug previews are observational: they must not advance or reset live rarity pressure.
        data.setUniqueInt(player.getUUID(), "death_hand_pity", oldDeathPity);
        // This command is a visual/debug tool, not a progression reward. In older builds it
        // silently returned 0 for fresh players because every real card was filtered by
        // minResolved/availability. Always provide a representative harmless preview instead.
        if (debugDeal.cards().isEmpty()) {
            debugDeal = new BargainDeal(java.util.List.of(
                    ForbiddenBargain.WRONG_GRAVITY,
                    ForbiddenBargain.COLD_LEDGER,
                    ForbiddenBargain.CRIMSON_BALANCE), BARGAIN_NORMAL);
            ctx.getSource().sendSuccess(() -> Component.literal("No live cards are unlocked yet; showing a progression-free preview hand.")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }
        int shown = debugDeal.cards().size();
        int[] ids = new int[shown];
        float[] rewards = new float[shown];
        float banked = 1.75f;
        for (int i = 0; i < shown; i++) {
            ForbiddenBargain card = debugDeal.cards().get(i);
            ids[i] = card.id;
            rewards[i] = Math.max(0f, RewardBreakdown.clamp(banked + card.rewardAdd()) - banked);
        }
        // Use a nearby position that is definitely not a randomizable container. The
        // card screen still sends its normal choice packet, but resolution will no-op
        // against this harmless position. This keeps /wardbound cards purely visual
        // even if the player happens to be standing on a real bargain chest.
        BlockPos debugPos = player.blockPosition().above();
        for (int dy = 1; dy <= 6; dy++) {
            BlockPos candidate = player.blockPosition().above(dy);
            BlockEntity candidateBe = player.level().getBlockEntity(candidate);
            if (!(candidateBe instanceof RandomizableContainerBlockEntity)) {
                debugPos = candidate;
                break;
            }
        }
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenBargainPacket(debugPos, ids, rewards, banked, makerSeed, debugDeal.mode(),
                        debugDeal.mode() == BARGAIN_CURSE || debugDeal.mode() == BARGAIN_DEATH, ""));
        return 1;
    }

    /** The server handle, before the main one is in scope. */
    private static MinecraftServer watchServerEarly(ServerPlayer player) {
        return player.getServer();
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static void resolveFailure(ServerPlayer player, BlockPos pos, long resultSeed,
                                      int maxLives, float timeRemainingRatio, float elapsedSeconds,
                                      int mistakes, int latencyMs, String modifiers, int rapidMistakes, int hybridOutcome) {
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockEntity be = serverLevel.getBlockEntity(pos);
        if (!(be instanceof RandomizableContainerBlockEntity container)) return;

        String dimId = serverLevel.dimension().location().toString();
        DimSettings settings = WardConfig.forDimension(dimId);
        MinecraftServer server = player.getServer();
        if (server == null) return;
        LockData liveData = LockData.get(server);
        if (!liveData.has(player.getUUID(), dimId, pos)) return;
        CompoundTag liveFailureState = liveData.getOrCreate(player.getUUID(), dimId, pos);
        if (liveFailureState.getLong("seed") != resultSeed) return;
        if (!liveFailureState.getBoolean("attemptIssued")) return;
        maxLives = Math.max(1, liveFailureState.contains("issuedMaxLives")
                ? liveFailureState.getInt("issuedMaxLives") : liveFailureState.getInt("lives"));
        liveFailureState.putBoolean("attemptIssued", false);
        liveData.put(player.getUUID(), dimId, pos, liveFailureState);
        dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(serverLevel,
                new net.minecraft.world.phys.Vec3(pos.getX() + .5, pos.getY() + .72, pos.getZ() + .5),
                dev.marrowseal.wardbound.net.WardLodestoneFxPacket.WARD_FAIL, 1.0f);
        releaseWardAttempt(container, player.getUUID());
        CompoundTag failurePersistent = container.getPersistentData();
        // Snapshot the ward as it was played. Several failure branches mutate or
        // clear these tags before telemetry is written; telemetry must describe
        // the failed attempt, not the state after the punishment was applied.
        boolean unsignedAtFailure = failurePersistent.getBoolean(TAG_UNSIGNED);
        int depthAtFailure = failurePersistent.getInt(TAG_DEPTH);
        int chainAtFailure = failurePersistent.getInt(TAG_CHAIN_LINK);
        int spiteAtFailure = failurePersistent.getInt(TAG_SPITE);
        boolean possessedAtFailure = failurePersistent.getBoolean(TAG_POSSESSED);
        boolean cthulhuAtFailure = failurePersistent.getBoolean(TAG_CTHULHU);
        boolean ordinaryAtFailure = isOrdinaryAttempt(liveFailureState, failurePersistent);
        boolean splinterAtFailure = failurePersistent.getBoolean(TAG_SPLINTER_BRACE);
        boolean shardAtFailure = failurePersistent.getBoolean(TAG_SHARD_OVERCHARGE);
        boolean livingAtFailure = failurePersistent.getBoolean(TAG_LIVING);
        int threatAtFailure = ChestThreat.bound(player, container, liveData);
        int afflictionAtFailure = failurePersistent.getInt(TAG_AFFLICTION);
        boolean mercyAtFailure = false;
        int eldritchLeftAtFailure = failurePersistent.getInt(TAG_ELDRITCH);
        int mutationAtFailure = mutationLevel(container, liveData.totalBeaten(player.getUUID()));
        int streakAtFailure = server == null ? 0 : LockData.get(server).streak(player.getUUID(), dimId);
        int tierAtFailure = server == null ? 0 : tierOf(LockData.get(server).totalBeaten(player.getUUID()));
        boolean contemptAtFailure = false;
        boolean rivalryAtFailure = false;
        MinigameType gameAtFailure = settings.minigame;
        MinigameCorruption.Variant corruptionAtFailure = MinigameCorruption.variant(gameAtFailure, 0);

        prepareChestMemory(failurePersistent, player.getUUID());
        CompoundTag failureMemory = chestMemory(failurePersistent, player.getUUID());
        failureMemory.putInt("losses", Math.min(99, failureMemory.getInt("losses") + 1));
        int memoryStreak = failureMemory.getInt("streak");
        failureMemory.putInt("streak", memoryStreak < 0 ? Math.max(-12, memoryStreak - 1) : -1);
        saveChestMemory(failurePersistent, player.getUUID(), failureMemory);
        SavantMemory.onChestMemory(player, failureMemory.getInt("wins"), failureMemory.getInt("losses"),
                failureMemory.getInt("streak"), false);
        container.setChanged();

        if (server != null) {
            LockData ld = LockData.get(server);
            if (ld.has(player.getUUID(), dimId, pos)) {
                CompoundTag lostState = ld.getOrCreate(player.getUUID(), dimId, pos);
                long lost = lostState.getLong("seed");
                mercyAtFailure = lostState.getBoolean("mercy");
                MinigameType lostGame = lostState.contains("game")
                        ? MinigameType.byOrdinal(lostState.getInt("game"))
                        : directedGame(player, settings, lost, lastGame(container));
                gameAtFailure = lostGame;
                corruptionAtFailure = MinigameCorruption.variant(lostGame, lostState.getInt("corruptionVariant"));
                boolean watchingMarkProvenanceAllowed = !lostState.getBoolean("watchingMarkForced")
                        || lostState.getBoolean("watchingMarkBound");
                boolean watchedFailure = watchingMarkProvenanceAllowed
                        && Sealmakers.house(lost).equals(ld.watcher(player.getUUID()));
                contemptAtFailure = watchedFailure && "contempt".equals(ld.verdict(player.getUUID()));
                rivalryAtFailure = !unsignedAtFailure && rivalryActive(ld, player.getUUID(), lost);
                ld.recordGame(player.getUUID(), dimId, lostGame);
                ld.recordDirectorOutcome(player.getUUID(), dimId, false, mistakes, 0f);
                if (!container.getPersistentData().getBoolean(TAG_UNSIGNED) && lostState.getLong("seed") != 0L) {
                    MasterSignature sig = MasterSignature.ofSeed(lostState.getLong("seed"));
                    int relationNow = ld.addRelation(player.getUUID(), sig, -2);
                    WardHistory.discoverMaster(player, sig, relationNow);
                }
                if (watchedFailure) {
                    ld.addRegard(player.getUUID(), -2);
                }
            }
        }

        MinigameMastery.record(player, liveData, gameAtFailure, false, RewardBreakdown.PerformanceGrade.NONE,
                0f, timeRemainingRatio, mistakes, corruptionAtFailure);
        MinigameSkillProfile.record(player, liveData, gameAtFailure, false, 0f, timeRemainingRatio,
                mistakes, rapidMistakes, maxLives, hybridOutcome);
        if (ordinaryAtFailure)
            FifthWaveCardEffects.onWardResolved(player, liveData, gameAtFailure, false, RewardBreakdown.PerformanceGrade.NONE);
        if (hybridOutcome != 0)
            HybridRound.recordEncounter(player, liveData, HybridRound.byOrdinal(liveFailureState.getInt("hybridMode")));
        SavantMemory.onWardFailure(player, gameAtFailure);
        if (ordinaryAtFailure)
            WardMeasureSystem.recordFailure(player, liveData, liveFailureState);
        OccultChainEvents.onChestResolved(player, liveData, threatAtFailure, shardAtFailure, false);
        if (threatAtFailure >= ChestThreat.Tier.PROFANE.ordinal())
            PlayerImprint.add(player, liveData, PlayerImprint.Trace.MARKED, 1, "a profane seal recorded a failure");

        // Read the charm before any branch below wipes the state. It was already
        // paid for when the lock bound itself; all that is left to do with it
        // here is honour the loot share it bought.
        Charm charm = Charm.NONE;
        if (server != null && LockData.get(server).has(player.getUUID(), dimId, pos)) {
            charm = Charm.byId(LockData.get(server)
                    .getOrCreate(player.getUUID(), dimId, pos).getString("charm"));
        }
        // Even a forced chest keeps the heart's share of its contents.
        float salvage = Math.min(1.0f, WardConfig.failLootMultiplier * charm.lootBonus());
        // The Splinter is bound to the physical ward, not just its clean-win path.
        // A forced/salvaged chest therefore keeps the same +50% promise.
        if (splinterAtFailure) salvage = Math.min(1.0f, salvage * SPLINTER_LOOT);

        // ...unless the ward takes it with it. A failed lock that always still
        // paid out most of the chest made losing an inconvenience rather than a
        // loss, and the whole risk economy sits on losing meaning something.
        // Scales with how hard the ward was and how much it was holding, so the
        // chests worth being greedy about are the ones that can actually burn.
        int pressureAtFailure = ChestThreat.boundPressure(player, container, LockData.get(player.getServer()));
        float burnChance = WardConfig.failBurnChance
                * Mth.clamp(settings.difficulty, 0.5f, 2.5f)
                * Mth.clamp(container.getPersistentData().getInt(TAG_VALUE) / 70f, 0.35f, 1.6f)
                * (1.0f + pressureAtFailure * 0.0032f);
        burnChance = Math.min(0.92f, burnChance);
        if (RNG.nextFloat() < burnChance) {
            container.getPersistentData().putBoolean(TAG_SCAR_BURNED, true);
            float kept = WardConfig.failBurnFloor
                    + RNG.nextFloat() * (WardConfig.failBurnCeiling - WardConfig.failBurnFloor);
            salvage *= kept;
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(kept < 0.12f
                            ? "Whatever was in it is gone."
                            : "Most of what was in it is gone.")
                    .withStyle(ChatFormatting.RED), false);
        }

        // The ward answers. Independent of on_fail, because "what the container
        // does to its contents" and "what comes out of it at you" are separate
        // questions and a pack author should be able to set them separately.
        int value = container.getPersistentData().getInt(TAG_VALUE);
        float guardianRisk = Math.min(0.98f, WardConfig.guardianChance
                * ChestThreat.guardianScaleForPressure(pressureAtFailure)
                * PlayerImprint.guardianScale(liveData, player.getUUID()));
        if (RNG.nextFloat() < guardianRisk) {
            int woke = Guardians.spawn(serverLevel, pos, dimId, value, player, false, threatAtFailure);
            if (woke > 0) {
                serverLevel.playSound(null, pos, WardSounds.WARD_SEAL.get(),
                        SoundSource.BLOCKS, 1.0f, 0.62f);
                if (WardConfig.guardianSealSeconds > 0) {
                    container.getPersistentData().putLong(TAG_SEALED_UNTIL,
                            serverLevel.getGameTime() + WardConfig.guardianSealSeconds * 20L);
                }
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(woke == 1
                                ? "Something came out with the lock."
                                : woke + " things came out with the lock.")
                        .withStyle(ChatFormatting.DARK_RED), false);
            }
        }

        if (server != null) {
            LockData watch = LockData.get(server);
            int att = watch.attention(player.getUUID());
            if (att > 0) {
                AttentionSystem.Stage beforeLoss = AttentionSystem.current(watch, player.getUUID());
                AttentionSystem.set(player, watch,
                        Math.min(WardConfig.attentionCap, att + AttentionSystem.lossGain(beforeLoss, WardConfig.attentionPerLoss)),
                        "a failed ward fed it another example");
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal("It is closer than it was.")
                        .withStyle(ChatFormatting.DARK_RED), false);
            }
        }

        if (container.getPersistentData().contains(TAG_GAUNTLET)) {
            container.getPersistentData().remove(TAG_GAUNTLET);
            container.getPersistentData().remove(TAG_UNSIGNED);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "It closes on all three. You will have to find another.")
                    .withStyle(ChatFormatting.DARK_RED), false);
        }
        if (container.getPersistentData().contains(TAG_POSSESSED)) {
            container.getPersistentData().remove(TAG_POSSESSED);
        }
        if (container.getPersistentData().contains(TAG_CTHULHU)) {
            container.getPersistentData().remove(TAG_CTHULHU);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "The great eye closes. The singular ward is gone.")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);
        }
        if (container.getPersistentData().contains(TAG_ELDRITCH)) {
            container.getPersistentData().remove(TAG_ELDRITCH);
            container.getPersistentData().remove(TAG_ELDRITCH + "Total");
            container.getPersistentData().remove(TAG_ELDRITCH_USED);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "The mutation collapses in on itself. The ward will not continue.")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);
        }

        // The container itself keeps a grudge.
        //
        // This is the one situation the mod never had an answer for: losing a
        // lock cost nothing but the walk back, so you retried an identical lock
        // until the dice agreed. That is not a defeat, it is a delay. Now the
        // same seal closes tighter every time you come back and holds
        // proportionally more, so a chest you keep losing becomes a decision
        // instead of a chore, and walking away from one is a real option.
        CompoundTag grudge = container.getPersistentData();
        int spiteBefore = grudge.getInt(TAG_SPITE);
        int extraDebt = 0;
        if (server != null && spiteBefore < WardConfig.spiteMax) {
            LockData debts = LockData.get(server);
            if (debts.consumeIronDebt(player.getUUID())) {
                extraDebt = 1;
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                                "The iron debt is collected. This seal remembers the loss twice.")
                        .withStyle(ChatFormatting.DARK_PURPLE), false);
            }
        }
        int spiteNow = Math.min(WardConfig.spiteMax, spiteBefore + 1 + extraDebt);
        if (spiteNow > spiteBefore) {
            grudge.putInt(TAG_SPITE, spiteNow);
            grudge.putBoolean(TAG_SCAR_GRUDGE, true);
            container.setChanged();
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(spiteNow == 1
                            ? "The seal will remember this."
                            : "The seal remembers you. It has closed tighter, and it is holding more.")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);
        }

        if (chainAtFailure > 0 && server != null) {
            LockData chainData = LockData.get(server);
            chainData.setChain(player.getUUID(), null);
            container.getPersistentData().remove(TAG_CHAIN_LINK);
            container.setChanged();
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal("The trail goes cold here.")
                    .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC), false);
        }

        int lostRun = server == null ? 0 : LockData.get(server).breakStreak(player.getUUID(), dimId);
        if (lostRun > 1) {
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal("Run of " + lostRun + " broken.")
                    .withStyle(ChatFormatting.DARK_RED), false);
        }

        float failedDifficulty = settings.difficulty * WardConfig.tuning(gameAtFailure).difficultyScale
                * Math.max(streakDifficulty(streakAtFailure),
                        1f + Math.max(0, depthAtFailure) * WardConfig.temptDifficultyPerDepth)
                * tierDifficulty(tierAtFailure)
                * (1f + chainAtFailure * WardConfig.chainDifficultyPerLink)
                * (1f + spiteAtFailure * WardConfig.spiteDifficulty)
                * (contemptAtFailure ? WardConfig.contemptDifficulty : 1f)
                * (rivalryAtFailure ? WardConfig.masterRivalryDifficulty : 1f)
                * (1f + mutationAtFailure * WardConfig.wardMutationDifficultyPerLevel)
                * (eldritchLeftAtFailure > 0 ? (eldritchLeftAtFailure == 1 ? 1.12f : 1.06f) : 1f)
                * (possessedAtFailure ? 1.05f : 1f)
                * (livingAtFailure ? 1.08f : 1f)
                * (cthulhuAtFailure ? WardConfig.cthulhuWardDifficulty : 1f)
                * (mercyAtFailure ? 0.88f : 1f)
                * (afflictionAtFailure == 4 ? 1.06f : afflictionAtFailure == 3 ? 1.04f : 1f);
        WardTelemetry.record(dimId, gameAtFailure, false, failedDifficulty, 0, maxLives, 0f,
                timeRemainingRatio, elapsedSeconds, mistakes, latencyMs, modifiers,
                unsignedAtFailure, chainAtFailure, spiteAtFailure, depthAtFailure, "NONE", salvage);

        dev.marrowseal.wardbound.WardHistory.setLastWardSummary(player, false, gameAtFailure.id,
                String.format("Salvage x%.2f · %d mistakes · %s",
                        salvage, Math.max(0, mistakes), settings.onFail.name().replace('_', ' ')));

        switch (settings.onFail) {
            case EXPLODE -> {
                if (server != null) LockData.get(server).clearAllAt(dimId, pos);
                serverLevel.removeBlock(pos, false);
                serverLevel.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        2.6f, Level.ExplosionInteraction.BLOCK);
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal("The trap detonated \u2014 the container was blown to pieces.")
                        .withStyle(ChatFormatting.RED), true);
            }
            case SPAWN_GUARD -> {
                // Was one hardcoded mob per dimension, which made this branch the
                // same scene every time. Same pool as everything else now.
                Guardians.spawn(serverLevel, pos, dimId, container.getPersistentData().getInt(TAG_VALUE), player, false, threatAtFailure);
                container.getPersistentData().putFloat(TAG_MULT, salvage);
                container.getPersistentData().putUUID(TAG_WHO, player.getUUID());
                container.setChanged();
                unlockAndClear(serverLevel, pos, container, server, dimId);
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal("A guardian has awoken. The container opened, but its contents were diminished.")
                        .withStyle(ChatFormatting.RED), true);
            }
            case LOCK_FOREVER -> {
                CompoundTag persistent = container.getPersistentData();
                // A dead physical lock cannot carry a live player state or a
                // deferred reward. Leaving either behind let late packets or a
                // later state path touch a chest that can never open.
                clearTags(container);
                persistent.remove(TAG_MULT);
                persistent.remove(TAG_WHO);
                persistent.remove(TAG_RELIC_GRADE);
                persistent.remove(TAG_DOUBLE_LOOT);
                persistent.remove(TAG_SHARD_OVERCHARGE);
                persistent.remove(TAG_SPLINTER_BRACE);
                persistent.remove(TAG_ECHO_SHARD);
                persistent.putBoolean("WardDead", true);
                if (server != null) LockData.get(server).clearAllAt(dimId, pos);
                container.setChanged();
                serverLevel.playSound(null, pos, WardSounds.WARD_FAIL.get(), SoundSource.BLOCKS, 0.9f, 0.72f);
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal("The lock mechanism snapped. This container can never be opened again.")
                        .withStyle(ChatFormatting.DARK_RED), true);
            }
            default -> {
                container.getPersistentData().putFloat(TAG_MULT, salvage);
                container.getPersistentData().putUUID(TAG_WHO, player.getUUID());
                container.setChanged();
                unlockAndClear(serverLevel, pos, container, server, dimId);
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal("You forced the lock. The best of it is gone.")
                        .withStyle(ChatFormatting.YELLOW), true);
            }
        }
    }

    private static void unlockAndClear(ServerLevel level, BlockPos pos,
                                       RandomizableContainerBlockEntity container,
                                       MinecraftServer server, String dimId) {
        CompoundTag persistent = container.getPersistentData();
        persistent.remove(TAG_VALUE);
        persistent.remove(TAG_SCANNED);
        persistent.remove(TAG_LOCKED);
        persistent.remove(TAG_BARGAIN_OFFERS);
        persistent.remove(TAG_BARGAIN_SEED);
        persistent.remove(TAG_BARGAIN_MODE);
        persistent.remove(TAG_BARGAIN_REFRESHED);
        persistent.remove(TAG_BARGAIN_CAP);
        persistent.remove(TAG_TEMPT);
        persistent.remove(TAG_ACTIVE_WHO);
        persistent.remove(TAG_ACTIVE_UNTIL);
        // Active ward state must not leak into a later Attention/re-ward cycle.
        // Visual history has its own TAG_SCAR_* fields and deliberately survives.
        persistent.remove(TAG_DEPTH);
        persistent.remove(TAG_UNSIGNED);
        persistent.remove(TAG_GAUNTLET);
        persistent.remove(TAG_ELDRITCH);
        persistent.remove(TAG_ELDRITCH + "Total");
        persistent.remove(TAG_ELDRITCH_USED);
        persistent.remove(TAG_POSSESSED);
        persistent.remove(TAG_CTHULHU);
        persistent.remove(TAG_SPLINTER_BRACE);
        persistent.remove(TAG_ECHO_SHARD);
        if (persistent.getBoolean(TAG_SHARD_OVERCHARGE)) persistent.putBoolean(TAG_DOUBLE_LOOT, true);
        persistent.remove(TAG_SHARD_OVERCHARGE);
        persistent.remove(TAG_AFFLICTION);
        persistent.remove(TAG_LIVING);
        persistent.remove(TAG_SPITE);
        persistent.remove(TAG_SEALED_UNTIL);
        // TAG_MULT and TAG_WHO deliberately survive: the loot table has not rolled yet.
        container.setChanged();
        if (server != null) {
            LockData.get(server).clearAllAt(dimId, pos);
        }
        level.sendBlockUpdated(pos, container.getBlockState(), container.getBlockState(), 3);
    }

    // ------------------------------------------------------------------ events

    /**
     * A Sealed Card is consumed when its player-owned hand is successfully dealt.
     * If login, death/respawn or a dimension transfer tears the client screen down,
     * the hand must therefore be recoverable without spending a second physical card.
     * The authoritative offer array already lives in death-persistent player NBT;
     * these hooks merely re-present that existing decision.
     */
    private static void reopenPendingLooseHand(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        CompoundTag pd = loosePlayerData(player);
        if (!pd.contains(LOOSE_OFFERS, Tag.TAG_INT_ARRAY)) return;
        int[] offers = pd.getIntArray(LOOSE_OFFERS);
        if (offers.length == 0) {
            clearLooseCard(player);
            return;
        }
        // Queue behind the lifecycle event so the client connection/dimension has
        // finished installing before the screen packet is delivered.
        player.getServer().execute(() -> {
            if (player.connection != null) sendLooseBargainScreen(player);
        });
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) reopenPendingLooseHand(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) reopenPendingLooseHand(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) reopenPendingLooseHand(player);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        LevelAccessor la = event.getLevel();
        if (la.isClientSide() || !(la instanceof ServerLevel level)) return;
        ChunkAccess ca = event.getChunk();
        if (!(ca instanceof LevelChunk chunk)) return;
        if (chunk.getInhabitedTime() > 0L) return;         // player has been here already
        if (chunk.getAllStarts().isEmpty()) return;
        if (PENDING.size() > 400) return;

        for (StructureStart start : chunk.getAllStarts().values()) {
            if (!start.isValid()) continue;
            for (StructurePiece piece : start.getPieces()) {
                PENDING.add(new PendingScan(level, piece.getBoundingBox(), new int[]{5}));
            }
        }
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        String input = parse.getReader().getString().trim();
        if (!input.startsWith("place structure") && !input.startsWith("place jigsaw")
                && !input.startsWith("place template")) return;
        CommandSourceStack source = parse.getContext().getSource();
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());
        PENDING.add(new PendingScan(level,
                new BoundingBox(origin.getX() - 24, origin.getY() - 24, origin.getZ() - 24,
                        origin.getX() + 24, origin.getY() + 24, origin.getZ() + 24),
                new int[]{3}));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer currentServer = ServerLifecycleHooks.getCurrentServer();
        if (currentServer != null) {
            LockData persistent = LockData.get(currentServer);
            for (ServerPlayer p : currentServer.getPlayerList().getPlayers()) {
                int stage = persistent.uniqueInt(p.getUUID(), "refuse_punishment_stage");
                if (stage == 1 && p.isAlive()) {
                    long judgementAt = persistent.uniqueLong(p.getUUID(), "refuse_judgement_at");
                    if (judgementAt != Long.MIN_VALUE && p.level().getGameTime() >= judgementAt) {
                        persistent.setUniqueLong(p.getUUID(), "refuse_judgement_at", Long.MIN_VALUE);
                        persistent.setUniqueInt(p.getUUID(), "refuse_punishment_stage", 3); // killed; boon waits for return
                        p.kill();
                    }
                } else if (stage == 3 && p.isAlive()) {
                    persistent.setUniqueInt(p.getUUID(), "refuse_punishment_stage", 2);
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 240, 19, false, true, true));
                    WardHud.message(p, Component.literal("???: Very well. Be strong, then.")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), false);
                } else if (stage == 2 && p.isAlive()) {
                    MobEffectInstance strength = p.getEffect(MobEffects.DAMAGE_BOOST);
                    if (strength == null || strength.getAmplifier() < 19 || strength.getDuration() < 80) {
                        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 240, 19, false, true, true));
                    }
                }
            }
        }
        if (!RESIDUES.isEmpty()) {
            List<WinResidue> faded = new ArrayList<>();
            for (WinResidue r : RESIDUES) {
                int left = --r.ticks()[0];
                if (left == 30 || left == 15) {
                    double x = r.pos().getX() + 0.5, y = r.pos().getY() + 0.78, z = r.pos().getZ() + 0.5;
                    int kind = switch (r.kind()) {
                        case 3 -> dev.marrowseal.wardbound.net.WardLodestoneFxPacket.RELIC_ELDRITCH;
                        case 2 -> dev.marrowseal.wardbound.net.WardLodestoneFxPacket.RELIC_ECHO;
                        case 1 -> dev.marrowseal.wardbound.net.WardLodestoneFxPacket.WARD_PERFECT;
                        default -> dev.marrowseal.wardbound.net.WardLodestoneFxPacket.WARD_SUCCESS;
                    };
                    float scale = r.kind() == 1 ? .28f : r.kind() >= 2 ? .25f : .22f;
                    dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(r.level(),
                            new net.minecraft.world.phys.Vec3(x, y, z), kind, scale);
                }
                if (left <= 0) faded.add(r);
            }
            RESIDUES.removeAll(faded);
        }
        if (PENDING.isEmpty()) return;
        List<PendingScan> done = new ArrayList<>();
        int processedScans = 0;
        for (PendingScan scan : PENDING) {
            if (--scan.delay()[0] > 0) continue;
            if (processedScans >= MAX_PENDING_SCANS_PER_TICK) break;
            try {
                scanBox(scan.level(), scan.box());
            } catch (Exception e) {
                Wardbound.LOG.debug("[Wardbound] scan failed: {}", e.toString());
            }
            done.add(scan);
            processedScans++;
        }
        PENDING.removeAll(done);
    }

    private static void bindPendingShardAttunement(ServerPlayer player,
                                                    RandomizableContainerBlockEntity container) {
        CompoundTag persistent = container.getPersistentData();
        if (persistent.getBoolean(TAG_SPLINTER_BRACE) || persistent.getBoolean(TAG_SHARD_OVERCHARGE)
                || persistent.getBoolean(TAG_ECHO_SHARD)) return;

        if (player.hasEffect(WardEffects.WARD_SPLINTER_ATTUNEMENT.get())) {
            persistent.putBoolean(TAG_SPLINTER_BRACE, true);
            if (player.getServer() != null) PlayerImprint.add(player, LockData.get(player.getServer()), PlayerImprint.Trace.CAUTIOUS, 2, "braced a ward with a Splinter");
            player.removeEffect(WardEffects.WARD_SPLINTER_ATTUNEMENT.get());
            container.setChanged();
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "The pale ward-sign leaves you and settles into this seal: +50% loot.")
                    .withStyle(ChatFormatting.AQUA), false);
            return;
        }
        if (player.hasEffect(WardEffects.ELDRITCH_SHARD_ATTUNEMENT.get())) {
            persistent.putBoolean(TAG_SHARD_OVERCHARGE, true);
            if (player.getServer() != null) {
                LockData memory = LockData.get(player.getServer());
                PlayerImprint.add(player, memory, PlayerImprint.Trace.GREEDY, 2, "overcharged a ward for doubled contents");
                PlayerImprint.add(player, memory, PlayerImprint.Trace.MARKED, 1, "spent an Eldritch Shard into a physical seal");
            }
            player.removeEffect(WardEffects.ELDRITCH_SHARD_ATTUNEMENT.get());
            container.setChanged();
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "Your shadow tears free of the shard's mark and enters the seal. Its final contents will repeat.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            return;
        }
        if (player.hasEffect(WardEffects.ECHO_SHARD_ATTUNEMENT.get())) {
            persistent.putBoolean(TAG_ECHO_SHARD, true);
            if (player.getServer() != null) PlayerImprint.add(player, LockData.get(player.getServer()), PlayerImprint.Trace.BOUND, 1, "asked an Echo Shard to bias the ward's memory");
            player.removeEffect(WardEffects.ECHO_SHARD_ATTUNEMENT.get());
            container.setChanged();
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            "The Echo Shard leaves your thoughts and sinks into the seal.")
                    .withStyle(ChatFormatting.BLUE), false);
        }
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RandomizableContainerBlockEntity container)) return;

        ItemStack heldShard = player.getItemInHand(event.getHand());
        if (heldShard.getItem() instanceof RelicDropItem relicDrop) {
            relicDrop.activate(player, heldShard);
            event.setCanceled(true);
            event.setUseItem(Event.Result.DENY);
            return;
        }

        CompoundTag persistent = container.getPersistentData();

        if (persistent.getBoolean("WardDead")) {
            event.setCanceled(true);
            event.setUseItem(Event.Result.DENY);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal("This container's lock is broken and can no longer be opened.")
                    .withStyle(ChatFormatting.DARK_RED), true);
            return;
        }

        // Beaten containers sulk. Without this the player loses a lock, walks
        // straight past whatever just spawned, and takes the salvage anyway,
        // which makes the guardians decoration rather than a consequence.
        if (persistent.contains(TAG_SEALED_UNTIL)) {
            long until = persistent.getLong(TAG_SEALED_UNTIL);
            long now = level.getGameTime();
            if (now < until) {
                event.setCanceled(true);
                event.setUseItem(Event.Result.DENY);
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                                "The container is still shut. " + ((until - now) / 20L + 1) + "s")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            persistent.remove(TAG_SEALED_UNTIL);
            container.setChanged();
        }

        // A successful ward may still be waiting for a card answer, a re-seal
        // decision, or the vanilla loot-table roll itself. These states must own
        // the next interaction BEFORE lazy structure scanning can see the still-
        // unopened loot table and mistake this same chest for a brand-new ward.
        if (persistent.contains(TAG_BARGAIN_OFFERS)) {
            event.setCanceled(true);
            event.setUseItem(Event.Result.DENY);
            openBargainScreen(player, pos, container);
            return;
        }
        if (persistent.getBoolean(TAG_TEMPT)) {
            if (!isDecisionOwner(player, persistent)) {
                event.setCanceled(true);
                event.setUseItem(Event.Result.DENY);
                WardHud.message(player, Component.literal("This re-seal was offered to another witness.")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
                return;
            }
            if (player.isShiftKeyDown()) {
                event.setCanceled(true);
                event.setUseItem(Event.Result.DENY);
                reseal(player, pos, container, heldShard);
            } else {
                persistent.remove(TAG_TEMPT);
                MinecraftServer pendingServer = player.getServer();
                if (pendingServer != null) {
                    String pendingDim = level.dimension().location().toString();
                    unlockAndClear(level, pos, container, pendingServer, pendingDim);
                }
            }
            return;
        }
        boolean resolvedAwaitingLoot = !persistent.getBoolean(TAG_LOCKED) && (persistent.contains(TAG_MULT)
                || persistent.hasUUID(TAG_WHO)
                || persistent.contains(TAG_RELIC_GRADE)
                || persistent.getBoolean(TAG_DOUBLE_LOOT));
        if (resolvedAwaitingLoot) {
            // Do not cancel: vanilla must open the container now. WardLootModifier
            // consumes these one-shot reward tags when the loot table rolls.
            return;
        }


        // A container standing where the chain pointed is the next link, whether
        // or not it would otherwise have warded. This is the whole trick: the
        // mod decides warding at the moment of touching, so a trail can be
        // promised without anything having been placed on it.
        if (watchServerEarly(player) != null) {
            LockData cd = LockData.get(watchServerEarly(player));
            CompoundTag chain = cd.chain(player.getUUID());
            if (Chain.isTarget(chain, level.dimension().location().toString(), pos)
                    && !persistent.getBoolean(TAG_LOCKED)
                    && persistent.getInt(TAG_CHAIN_LINK) == 0) {
                int link = chain.getInt("link");
                persistent.putInt(TAG_VALUE, Math.max(persistent.getInt(TAG_VALUE), 55 + link * 6));
                persistent.putBoolean(TAG_SCANNED, true);
                persistent.putBoolean(TAG_LOCKED, true);
                persistent.putInt(TAG_CHAIN_LINK, link);
                if (link >= chain.getInt("length")) persistent.putBoolean(TAG_UNSIGNED, true);
                container.setChanged();
                level.playSound(null, pos, WardSounds.WARD_SEAL.get(),
                        SoundSource.BLOCKS, 1.0f, 0.7f);
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                                "This is one of them. Link " + link + ".")
                        .withStyle(ChatFormatting.DARK_AQUA), true);
            }
        }

        // While you are being watched, containers you have already emptied do not
        // stay empty. This is the whole shape of the endgame: there is no arena
        // to walk into, the pressure simply arrives where you live, on the
        // chests that already have your name on them.
        MinecraftServer watchServer = player.getServer();
        if (!persistent.contains(TAG_VALUE) && watchServer != null) {
            LockData watch = LockData.get(watchServer);
            boolean pendingReward = persistent.contains(TAG_MULT)
                    || persistent.getBoolean(TAG_DOUBLE_LOOT)
                    || persistent.contains(TAG_RELIC_GRADE);
            if (!pendingReward
                    && watch.attention(player.getUUID()) > 0
                    && RNG.nextFloat() < WardConfig.attentionResealChance
                            * AttentionSystem.resealScale(AttentionSystem.current(watch, player.getUUID()))) {
                persistent.putInt(TAG_VALUE, 45 + RNG.nextInt(56));
                persistent.putBoolean(TAG_SCANNED, true);
                persistent.putBoolean(TAG_UNSIGNED, true);
                persistent.putBoolean(TAG_LOCKED, true);
                container.setChanged();
                level.playSound(null, pos, WardSounds.WARD_SEAL.get(),
                        SoundSource.BLOCKS, 1.0f, 0.55f);
                dev.marrowseal.wardbound.WardHud.message(player, Component.literal("It has closed again.")
                        .withStyle(ChatFormatting.DARK_AQUA), true);
                event.setCanceled(true);
                event.setUseItem(Event.Result.DENY);
                return;
            }
        }

        // Structure scans are only an eager optimisation. Large structures such as
        // villages can span chunks whose StructureStart lives in a neighbouring
        // chunk, so those containers may never be reached by onChunkLoad(). If an
        // unopened randomizable container still has a loot table, value it lazily
        // on first interaction. Player-placed/already-opened containers have no
        // loot table and therefore remain vanilla.
        if (!persistent.contains(TAG_VALUE)) {
            tagContainer(level, pos, container);
        }
        if (!persistent.contains(TAG_VALUE)) return;   // player chest or already opened

        int value = persistent.getInt(TAG_VALUE);
        if (value < WardConfig.minValueToLock) {
            clearTags(container);
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return;

        String dimId = level.dimension().location().toString();
        LockData data = LockData.get(server);

        // already committed to the minigame. Relic drops are applied only after
        // a chest has actually become a ward, so they can never be consumed on a
        // container that would have failed its lock roll and opened normally.
        if (persistent.getBoolean(TAG_LOCKED)) {
            event.setCanceled(true);
            event.setUseItem(Event.Result.DENY);
            WardAdvancements.firstWard(player);
            openMinigame(player, pos, container, heldShard);
            return;
        }

        float chance = persistent.getInt(TAG_CHAIN_LINK) > 0 ? 1.0f
                : (value < WardConfig.lockThreshold ? WardConfig.lockChanceLow : WardConfig.lockChanceHigh);

        if (RNG.nextFloat() >= chance) {
            clearTags(container);          // this one just opens normally
            return;
        }

        event.setCanceled(true);
        event.setUseItem(Event.Result.DENY);
        persistent.putBoolean(TAG_LOCKED, true);
        container.setChanged();

        if (WardConfig.requireConfirmClick && !data.has(player.getUUID(), dimId, pos)) {
            if (!claimWardAttempt(player, container)) return;
            DimSettings settings = WardConfig.forDimension(dimId);
            level.playSound(null, pos, WardSounds.PIN_RISE.get(), SoundSource.BLOCKS, 0.34f, 1.18f);
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal(
                            container.getPersistentData().getBoolean(TAG_UNSIGNED)
                                    ? "This one has no maker's mark on it."
                                    : "Warded (value " + value + ", " + settings.gameLabel()
                                            + "). Right-click again to begin.")
                    .withStyle(container.getPersistentData().getBoolean(TAG_UNSIGNED)
                            ? ChatFormatting.DARK_AQUA : ChatFormatting.GOLD), true);
            data.put(player.getUUID(), dimId, pos,
                    newState(player, settings, pos, container, heldShard));
            WardAdvancements.firstWard(player);
            readWard(player, container, settings, heldShard);
            return;
        }

        openMinigame(player, pos, container, heldShard);
    }

    private static void clearTags(RandomizableContainerBlockEntity container) {
        CompoundTag persistent = container.getPersistentData();
        persistent.remove(TAG_VALUE);
        persistent.remove(TAG_SCANNED);
        persistent.remove(TAG_LOCKED);
        persistent.remove(TAG_BARGAIN_OFFERS);
        persistent.remove(TAG_BARGAIN_SEED);
        persistent.remove(TAG_BARGAIN_MODE);
        persistent.remove(TAG_BARGAIN_REFRESHED);
        persistent.remove(TAG_BARGAIN_CAP);
        persistent.remove(TAG_TEMPT);
        persistent.remove(TAG_ACTIVE_WHO);
        persistent.remove(TAG_ACTIVE_UNTIL);
        persistent.remove(TAG_DEPTH);
        persistent.remove(TAG_UNSIGNED);
        persistent.remove(TAG_GAUNTLET);
        persistent.remove(TAG_ELDRITCH);
        persistent.remove(TAG_ELDRITCH + "Total");
        persistent.remove(TAG_ELDRITCH_USED);
        persistent.remove(TAG_POSSESSED);
        persistent.remove(TAG_CTHULHU);
        persistent.remove(TAG_SPLINTER_BRACE);
        persistent.remove(TAG_ECHO_SHARD);
        persistent.remove(TAG_SHARD_OVERCHARGE);
        persistent.remove(TAG_DOUBLE_LOOT);
        persistent.remove(TAG_AFFLICTION);
        persistent.remove(TAG_LIVING);
        persistent.remove(TAG_SPITE);
        persistent.remove(TAG_CHAIN_LINK);
        persistent.remove(TAG_SEALED_UNTIL);
        persistent.remove(ChestThreat.TAG_TIER);
        persistent.remove(ChestThreat.TAG_PRESSURE);
        persistent.remove(ChestThreat.TAG_WHO);
        container.setChanged();
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        if (server == null) return;
        LockData.get(server).clearAllAt(level.dimension().location().toString(), event.getPos());
    }

    @SubscribeEvent
    public static void onServerStart(ServerStartingEvent event) {
        // Integrated servers can stop and start multiple worlds in the same JVM. These queues hold
        // ServerLevel references, so stale entries must never bleed particles/scans into the next save.
        PENDING.clear();
        RESIDUES.clear();
        SCORE_CACHE.clear();
        WardConfig.load();
        ItemValueResolver.clearCache();
        WardPerformance.clear();
    }

    // ------------------------------------------------------------------ debug commands

    private static int debugPlayerState(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LockData data = LockData.get(ctx.getSource().getServer());
        UUID id = player.getUUID();
        ctx.getSource().sendSuccess(() -> Component.literal("WARDDEBUG // PLAYER").withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Attention: " + AttentionSystem.detail(data, id)
                + " · imprint: " + PlayerImprint.compact(data, id)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Trace: " + PlayerImprint.full(data, id)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Field pity " + data.uniqueInt(id, "field_card_pity") + "/" + CardBalance.FIELD_PITY_GUARANTEE
                + " · Death pity " + data.uniqueInt(id, "death_hand_pity") + "/" + CardBalance.DEATH_PITY_GUARANTEE
                + " · resonance " + DeathResonance.detail(data, id)
                + " · Covenant stain " + data.uniqueInt(id, "covenant_stain")), false);
        int palimpsests = 0;
        for (ForbiddenBargain card : ForbiddenBargain.values())
            if (CardEvolution.activeVariant(data, id, card) >= CardEvolution.MAX_REVISION) palimpsests++;
        final int palimpsestCount = palimpsests;
        ctx.getSource().sendSuccess(() -> Component.literal("Cards: " + CardEvolution.revisedCount(data, id) + " revised · " + palimpsestCount
                + " palimpsest · corruptions " + CurseEvolution.knownCount(data, id) + "/" + CurseEvolution.Corruption.values().length), false);
        for (CardMaster master : CardMaster.values()) {
            String line = master.title + ": favor " + master.relation(data, id) + " · grudge " + master.grudge(data, id)
                    + " · story " + MasterStory.chapter(data, id, master) + "/" + MasterStory.MAX_CHAPTER + " · audiences " + data.uniqueInt(id, "dealer_audiences_" + master.id)
                    + " · accepted/refused " + data.uniqueInt(id, "dealer_acceptances_" + master.id) + "/" + data.uniqueInt(id, "dealer_refusals_" + master.id);
            ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Chains: "
                + OccultChainEvents.summary(data, id, OccultChainEvents.ChainEvent.BLACK_TABLE) + " · "
                + OccultChainEvents.summary(data, id, OccultChainEvents.ChainEvent.ASH_MARGIN) + " · "
                + OccultChainEvents.summary(data, id, OccultChainEvents.ChainEvent.WITNESS_SEQUENCE)
                + " · guardian-cache " + WardPerformance.guardianCacheStats()), false);
        return 1;
    }

    private static int debugGuardianPool(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String dim = player.level().dimension().location().toString();
        java.util.List<String> ids = Guardians.debugEligible(player, dim);
        ctx.getSource().sendSuccess(() -> Component.literal("WARDDEBUG // GUARDIANS // " + dim + " // " + ids.size() + " eligible")
                .withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < ids.size(); i += 8) {
            int from = i, to = Math.min(ids.size(), i + 8);
            String line = String.join(", ", ids.subList(from, to));
            ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        return ids.isEmpty() ? 0 : 1;
    }

    private static int debugChestState(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos origin = player.blockPosition();
        RandomizableContainerBlockEntity best = null; BlockPos bestPos = null; double bestDist = Double.MAX_VALUE;
        for (BlockPos bp : BlockPos.betweenClosed(origin.offset(-8, -5, -8), origin.offset(8, 5, 8))) {
            if (!(level.getBlockEntity(bp) instanceof RandomizableContainerBlockEntity c) || !c.getPersistentData().contains(TAG_VALUE)) continue;
            double d = bp.distSqr(origin);
            if (d < bestDist) { bestDist = d; best = c; bestPos = bp.immutable(); }
        }
        if (best == null) { ctx.getSource().sendFailure(Component.literal("No tagged Wardbound chest within 8 blocks.")); return 0; }
        LockData data = LockData.get(ctx.getSource().getServer());
        CompoundTag debugTag = best.getPersistentData();
        CompoundTag debugMemory = chestMemory(debugTag, player.getUUID());
        int memoryWins = debugMemory.getInt("wins");
        int memoryLosses = debugMemory.getInt("losses");
        int memoryStreak = debugMemory.getInt("streak");
        int rememberedPlayers = debugTag.getCompound(TAG_MEMORY_PLAYERS).getAllKeys().size();
        String line = bestPos.toShortString() + " // " + ChestThreat.debug(player, best, data)
                + " // your memory W/L " + memoryWins + "/" + memoryLosses + " streak " + memoryStreak
                + " // remembered players=" + rememberedPlayers
                + " // splinter=" + best.getPersistentData().getBoolean(TAG_SPLINTER_BRACE)
                + " eldritch=" + best.getPersistentData().getBoolean(TAG_SHARD_OVERCHARGE);
        ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wardbound")

                .then(Commands.literal("item").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p == null) return 0;
                    ItemStack held = p.getMainHandItem();
                    if (held.isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.literal("You aren't holding anything."), false);
                        return 0;
                    }
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(held.getItem());
                    int score = ItemValueResolver.score(held);
                    ctx.getSource().sendSuccess(() -> Component.literal(id + "  ->  " + score + " points")
                            .withStyle(ChatFormatting.AQUA), false);
                    return 1;
                }))

                .then(Commands.literal("chest").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p == null) return 0;
                    ServerLevel lvl = ctx.getSource().getLevel();
                    BlockPos origin = p.blockPosition();
                    BlockPos found = null;
                    double bestDist = Double.MAX_VALUE;
                    for (BlockPos bp : BlockPos.betweenClosed(origin.offset(-6, -4, -6), origin.offset(6, 4, 6))) {
                        if (!(lvl.getBlockEntity(bp) instanceof RandomizableContainerBlockEntity c)) continue;
                        if (!c.getPersistentData().contains(TAG_VALUE)) continue;
                        double d = bp.distSqr(origin);
                        if (d < bestDist) {
                            bestDist = d;
                            found = bp.immutable();
                        }
                    }
                    if (found == null) {
                        ctx.getSource().sendSuccess(() -> Component.literal("No tagged container nearby."), false);
                        return 0;
                    }
                    BlockPos fp = found;
                    RandomizableContainerBlockEntity c = (RandomizableContainerBlockEntity) lvl.getBlockEntity(fp);
                    int value = c.getPersistentData().getInt(TAG_VALUE);
                    String dim = lvl.dimension().location().toString();
                    DimSettings st = WardConfig.forDimension(dim);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                                    fp.toShortString() + "  value " + value
                                            + "  |  " + st.gameLabel()
                                            + "  |  loot x" + st.lootMultiplier
                                            + (c.getPersistentData().contains(TAG_ELDRITCH) ? "  |  eldritch x" + c.getPersistentData().getInt(TAG_ELDRITCH) : "")
                                            + (c.getPersistentData().getBoolean(TAG_CTHULHU) ? "  |  cthulhu" : ""))
                            .withStyle(ChatFormatting.AQUA), false);
                    return 1;
                }))

                // Everything the mod has quietly been remembering about you.
                // The tier, the hand watching, its verdict, how well you know
                // its work: all of it drives real numbers and none of it was
                // visible anywhere, which made a system built over a whole save
                // feel like weather rather than progression.
                // Testing a lock used to mean walking until a structure chest
                // rolled the one you wanted. This opens any of them on the spot.
                // The position sent is the player's own block, so nothing is
                // there to resolve against and both outcomes are no-ops: you can
                // win or lose freely without touching a real container.
                .then(Commands.literal("play")
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (MinigameType t : MinigameType.values()) b.suggest(t.id);
                                    return b.buildFuture();
                                })
                                .executes(ctx -> debugPlay(ctx, 1.0f, 60))
                                .then(Commands.argument("difficulty", FloatArgumentType.floatArg(0.2f, 4f))
                                        .executes(ctx -> debugPlay(ctx,
                                                FloatArgumentType.getFloat(ctx, "difficulty"), 60))
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> debugPlay(ctx,
                                                        FloatArgumentType.getFloat(ctx, "difficulty"),
                                                        IntegerArgumentType.getInteger(ctx, "value")))))))
                .then(Commands.literal("cthulhu")
                        .executes(ctx -> debugCthulhu(ctx, 1.25f, 80))
                        .then(Commands.argument("difficulty", FloatArgumentType.floatArg(0.5f, 5f))
                                .executes(ctx -> debugCthulhu(ctx, FloatArgumentType.getFloat(ctx, "difficulty"), 80))
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> debugCthulhu(ctx, FloatArgumentType.getFloat(ctx, "difficulty"), IntegerArgumentType.getInteger(ctx, "value"))))))
                .then(Commands.literal("eldritch")
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (MinigameType t : MinigameType.values()) b.suggest(t.id);
                                    return b.buildFuture();
                                })
                                .executes(ctx -> debugEldritch(ctx, 1.0f, 60, 1, WardConfig.eldritchWardStages))
                                .then(Commands.argument("difficulty", FloatArgumentType.floatArg(0.2f, 4f))
                                        .executes(ctx -> debugEldritch(ctx,
                                                FloatArgumentType.getFloat(ctx, "difficulty"), 60, 1, WardConfig.eldritchWardStages))
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> debugEldritch(ctx,
                                                        FloatArgumentType.getFloat(ctx, "difficulty"),
                                                        IntegerArgumentType.getInteger(ctx, "value"), 1, WardConfig.eldritchWardStages))
                                                .then(Commands.argument("stage", IntegerArgumentType.integer(1, 8))
                                                        .executes(ctx -> debugEldritch(ctx,
                                                                FloatArgumentType.getFloat(ctx, "difficulty"),
                                                                IntegerArgumentType.getInteger(ctx, "value"),
                                                                IntegerArgumentType.getInteger(ctx, "stage"), WardConfig.eldritchWardStages))
                                                        .then(Commands.argument("total", IntegerArgumentType.integer(2, 8))
                                                                .executes(ctx -> debugEldritch(ctx,
                                                                        FloatArgumentType.getFloat(ctx, "difficulty"),
                                                                        IntegerArgumentType.getInteger(ctx, "value"),
                                                                        IntegerArgumentType.getInteger(ctx, "stage"),
                                                                        IntegerArgumentType.getInteger(ctx, "total")))))))))
                .then(Commands.literal("possessed")
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests((c, b) -> { for (MinigameType t : MinigameType.values()) b.suggest(t.id); return b.buildFuture(); })
                                .executes(ctx -> debugPossessed(ctx, 1.0f, 60))
                                .then(Commands.argument("difficulty", FloatArgumentType.floatArg(0.2f, 4f))
                                        .executes(ctx -> debugPossessed(ctx, FloatArgumentType.getFloat(ctx, "difficulty"), 60))
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> debugPossessed(ctx, FloatArgumentType.getFloat(ctx, "difficulty"), IntegerArgumentType.getInteger(ctx, "value")))))))
                .then(Commands.literal("cards")
                        .executes(ctx -> debugCards(ctx, null))
                        .then(Commands.literal("style")
                                .then(Commands.argument("style", StringArgumentType.word())
                                        .suggests((c, b) -> { b.suggest("crooked"); b.suggest("veiled"); b.suggest("exacting"); return b.buildFuture(); })
                                        .executes(ctx -> debugCards(ctx, StringArgumentType.getString(ctx, "style")))))
                        .then(Commands.literal("page")
                                .then(Commands.argument("page", IntegerArgumentType.integer(1, 128))
                                        .executes(ctx -> debugCardsPage(ctx, IntegerArgumentType.getInteger(ctx, "page")))))
                        .then(Commands.literal("kind")
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            for (ForbiddenBargain.Kind kind : ForbiddenBargain.Kind.values()) if (kind != ForbiddenBargain.Kind.REFRESH) b.suggest(kind.name().toLowerCase(java.util.Locale.ROOT));
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> debugCardsKind(ctx, StringArgumentType.getString(ctx, "kind"), 1))
                                        .then(Commands.argument("page", IntegerArgumentType.integer(1, 128))
                                                .executes(ctx -> debugCardsKind(ctx, StringArgumentType.getString(ctx, "kind"), IntegerArgumentType.getInteger(ctx, "page"))))))
                        .then(Commands.literal("id")
                                .then(Commands.argument("id", IntegerArgumentType.integer(0, 4096))
                                        .executes(ctx -> debugCardId(ctx, IntegerArgumentType.getInteger(ctx, "id"), 0))
                                        .then(Commands.argument("revision", IntegerArgumentType.integer(0, CardEvolution.MAX_REVISION))
                                                .executes(ctx -> debugCardId(ctx, IntegerArgumentType.getInteger(ctx, "id"), IntegerArgumentType.getInteger(ctx, "revision"))))))
                        .then(Commands.argument("style", StringArgumentType.word())
                                .suggests((c, b) -> { b.suggest("crooked"); b.suggest("veiled"); b.suggest("exacting"); return b.buildFuture(); })
                                .executes(ctx -> debugCards(ctx, StringArgumentType.getString(ctx, "style")))))
                .then(Commands.literal("progression").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("summary").executes(ctx -> debugProgressionSummary(ctx)))
                        .then(Commands.literal("preset")
                                .then(Commands.argument("preset", StringArgumentType.word())
                                        .suggests((c,b)->{b.suggest("fresh");b.suggest("early");b.suggest("mid");b.suggest("late");b.suggest("endgame");b.suggest("cosmic");b.suggest("max");return b.buildFuture();})
                                        .executes(ctx -> debugProgressionPreset(ctx, StringArgumentType.getString(ctx, "preset")))))
                        .then(Commands.literal("wards")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 5000))
                                        .executes(ctx -> debugProgressionWards(ctx, IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("field_cards")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 500))
                                        .executes(ctx -> { ServerPlayer p=ctx.getSource().getPlayerOrException(); LockData.get(ctx.getSource().getServer()).setUniqueInt(p.getUUID(),"field_cards_opened",IntegerArgumentType.getInteger(ctx,"value")); return 1; })))
                        .then(Commands.literal("death_pity")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, CardBalance.DEATH_PITY_GUARANTEE))
                                        .executes(ctx -> { ServerPlayer p=ctx.getSource().getPlayerOrException(); LockData.get(ctx.getSource().getServer()).setUniqueInt(p.getUUID(),"death_hand_pity",IntegerArgumentType.getInteger(ctx,"value")); return 1; })))
                        .then(Commands.literal("objectives")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 128))
                                        .executes(ctx -> { ServerPlayer p=ctx.getSource().getPlayerOrException(); LockData.get(ctx.getSource().getServer()).setUniqueInt(p.getUUID(),"objectives_completed_total",IntegerArgumentType.getInteger(ctx,"value")); return 1; })))
                        .then(Commands.literal("master")
                                .then(Commands.argument("master", StringArgumentType.word())
                                        .suggests((c,b)->{b.suggest("curator");b.suggest("notary");b.suggest("gambler");return b.buildFuture();})
                                        .then(Commands.literal("chapter").then(Commands.argument("value", IntegerArgumentType.integer(0, MasterStory.MAX_CHAPTER)).executes(ctx -> debugProgressionMaster(ctx,"chapter",IntegerArgumentType.getInteger(ctx,"value")))))
                                        .then(Commands.literal("relation").then(Commands.argument("value", IntegerArgumentType.integer(-12,20)).executes(ctx -> debugProgressionMaster(ctx,"relation",IntegerArgumentType.getInteger(ctx,"value")))))
                                        .then(Commands.literal("audiences").then(Commands.argument("value", IntegerArgumentType.integer(0,128)).executes(ctx -> debugProgressionMaster(ctx,"audiences",IntegerArgumentType.getInteger(ctx,"value")))))
                                        .then(Commands.literal("accepted").then(Commands.argument("value", IntegerArgumentType.integer(0,128)).executes(ctx -> debugProgressionMaster(ctx,"accepted",IntegerArgumentType.getInteger(ctx,"value")))))
                                        .then(Commands.literal("defeated").then(Commands.argument("value", IntegerArgumentType.integer(0,1)).executes(ctx -> debugProgressionMaster(ctx,"defeated",IntegerArgumentType.getInteger(ctx,"value")))))))
                        .then(Commands.literal("smith")
                                .then(Commands.literal("status").executes(ctx -> {
                                    ServerPlayer p=ctx.getSource().getPlayerOrException();
                                    ctx.getSource().sendSuccess(() -> Component.literal(dev.marrowseal.wardbound.ancientsmith.AncientSmithProgression.status(p)), false);
                                    return 1;
                                }))
                                .then(Commands.literal("trigger").executes(ctx -> {
                                    ServerPlayer p=ctx.getSource().getPlayerOrException();
                                    boolean ok=dev.marrowseal.wardbound.ancientsmith.AncientSmithProgression.start(p,true);
                                    if(!ok)ctx.getSource().sendFailure(Component.literal("Ancient Smith scene could not start; check /wardbound progression smith status."));
                                    return ok?1:0;
                                }))
                                .then(Commands.literal("reset").executes(ctx -> {
                                    ServerPlayer p=ctx.getSource().getPlayerOrException();
                                    dev.marrowseal.wardbound.ancientsmith.AncientSmithProgression.debugReset(p);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Ancient Smith progression reset. Existing Nhal-Sûl items were not deleted."), false);
                                    return 1;
                                })))
                        .then(Commands.literal("chain")
                                .then(Commands.argument("chain", StringArgumentType.word())
                                        .suggests((c,b)->{b.suggest("black_table");b.suggest("ash_margin");b.suggest("witness_sequence");return b.buildFuture();})
                                        .then(Commands.argument("stage", IntegerArgumentType.integer(0,5))
                                                .executes(ctx -> debugProgressionChain(ctx,IntegerArgumentType.getInteger(ctx,"stage")))))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("player").executes(ctx -> debugPlayerState(ctx)))
                        .then(Commands.literal("guardians").executes(ctx -> debugGuardianPool(ctx)))
                        .then(Commands.literal("chest").executes(ctx -> debugChestState(ctx))))
                .then(Commands.literal("standing").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    LockData data = LockData.get(ctx.getSource().getServer());
                    java.util.UUID id = player.getUUID();

                    int total = data.totalBeaten(id);
                    int tier = tierOf(total);
                    String watcher = data.watcher(id);
                    int known = data.familiarity(id, watcher);
                    String verdict = data.verdict(id);

                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u2500\u2500 Wardbound \u2500\u2500").withStyle(ChatFormatting.GOLD), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Seals broken: " + total + "   \u00b7   tier " + tier
                                            + String.format("   (wards x%.2f, loot x%.2f)",
                                            tierDifficulty(tier), tierLoot(tier)))
                            .withStyle(ChatFormatting.GRAY), false);

                    String signature = WardConfig.masterSignaturesEnabled
                            && known >= WardConfig.masterSignatureRevealAfter
                            ? " (" + MasterSignature.ofHouse(watcher).label() + ")" : "";
                    String hand = total < WardConfig.watcherAfter
                            ? "No hand has taken an interest yet."
                            : watcher + signature + " has taken an interest. You have broken "
                                    + known + " of their seals.";
                    ctx.getSource().sendSuccess(() -> Component.literal(hand)
                            .withStyle(ChatFormatting.GRAY), false);

                    String call = switch (verdict) {
                        case "favour" -> watcher + " found nothing to correct in your work. (+1 life, everywhere)";
                        case "contempt" -> watcher + " makes their seals with you in mind now. (harder, richer)";
                        default -> known < WardConfig.verdictAfter
                                ? "No verdict yet: " + (WardConfig.verdictAfter - known)
                                        + " more of their seals."
                                : "A verdict is due.";
                    };
                    int unsigned = data.unsignedBeaten(id);
                    int runs = data.gauntlets(id);
                    String deep = unsigned == 0
                            ? "You have not met an unsigned seal."
                            : unsigned + " unsigned seal" + (unsigned == 1 ? "" : "s") + " broken"
                                    + (unsigned < WardConfig.gauntletAfter
                                    ? "   \u00b7   " + (WardConfig.gauntletAfter - unsigned)
                                            + " before they come in threes"
                                    : "   \u00b7   gauntlets run: " + runs);
                    ctx.getSource().sendSuccess(() -> Component.literal(deep)
                            .withStyle(ChatFormatting.DARK_AQUA), false);

                    String chainLine = Chain.standing(ctx.getSource().getServer(), player);
                    if (chainLine != null) {
                        ctx.getSource().sendSuccess(() -> Component.literal(chainLine)
                                .withStyle(ChatFormatting.DARK_AQUA), false);
                    }

                    java.util.List<String> debts = new java.util.ArrayList<>();
                    if (data.hasBorrowedBreath(id)) debts.add("one borrowed breath");
                    if (data.hasIronDebt(id)) debts.add("one iron debt");
                    if (data.hasWatchingMark(id)) debts.add("a watching mark");
                    if (!debts.isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Debts outstanding: " + String.join(", ", debts))
                                .withStyle(ChatFormatting.DARK_PURPLE), false);
                    }

                    int att = data.attention(id);
                    if (att > 0 || data.released(id)) {
                        String watchLine = data.released(id)
                                ? "It let go of you."
                                : "It is watching. " + att + " of its seals left to break.";
                        ctx.getSource().sendSuccess(() -> Component.literal(watchLine)
                                .withStyle(data.released(id) ? ChatFormatting.AQUA
                                        : ChatFormatting.DARK_RED), false);
                    }

                    ctx.getSource().sendSuccess(() -> Component.literal(call)
                            .withStyle("favour".equals(verdict) ? ChatFormatting.GOLD
                                    : "contempt".equals(verdict) ? ChatFormatting.DARK_PURPLE
                                    : ChatFormatting.DARK_GRAY), false);
                    return 1;
                }))
                .then(Commands.literal("rescan").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p == null) return 0;
                    ServerLevel lvl = ctx.getSource().getLevel();
                    BlockPos o = p.blockPosition();
                    int r = 24;

                    SCORE_CACHE.clear();
                    ItemValueResolver.clearCache();
                    WardPerformance.clear();
                    WardConfig.load();

                    BoundingBox box = new BoundingBox(o.getX() - r, o.getY() - r, o.getZ() - r,
                            o.getX() + r, o.getY() + r, o.getZ() + r);

                    int cleared = 0;
                    for (BlockPos bp : BlockPos.betweenClosed(
                            new BlockPos(box.minX(), box.minY(), box.minZ()),
                            new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) {
                        if (lvl.getBlockEntity(bp) instanceof RandomizableContainerBlockEntity c) {
                            clearTags(c);
                            cleared++;
                        }
                    }
                    scanBox(lvl, box);
                    int found = cleared;
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Config reloaded, " + found + " containers scanned."), true);
                    return 1;
                })));
    }

}
