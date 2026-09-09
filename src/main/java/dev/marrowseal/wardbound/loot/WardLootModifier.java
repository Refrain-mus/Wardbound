package dev.marrowseal.wardbound.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import dev.marrowseal.wardbound.ChestValuator;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import dev.marrowseal.wardbound.ItemValueResolver;
import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.RewardBreakdown;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.item.WardItems;
import dev.marrowseal.wardbound.champion.SilasLore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Reads the multiplier the minigame wrote onto the chest and grows or shrinks the rolled loot.
 *
 * <p>The penalty used to be a lie. It scaled every stack's count by the multiplier and rounded, so
 * with the usual 0.5 a stack of one item became {@code Math.round(0.5) == 1} and survived intact.
 * Structure chests are mostly single items - a saddle, an enchanted book, one diamond - so losing
 * the minigame took away almost nothing and the whole punishment was invisible.
 *
 * <p>It now works on the chest's <b>worth</b> rather than its item count, and it takes from the top:
 * entries are ranked with {@link ItemValueResolver} and the best ones are pulled until the share of
 * worth called for has been removed. Losing costs you the diamond, not four cobblestone. Whatever
 * is left over after whole entries are taken is settled by trimming the largest remaining stack.
 */
public class WardLootModifier extends LootModifier {

    public static final Codec<WardLootModifier> CODEC = RecordCodecBuilder.create(
            inst -> codecStart(inst).apply(inst, WardLootModifier::new));

    private static final Random RNG = new Random();

    public WardLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Nonnull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (!context.hasParam(LootContextParams.ORIGIN)) return loot;

        Vec3 origin = context.getParam(LootContextParams.ORIGIN);
        BlockPos pos = BlockPos.containing(origin);
        BlockEntity be = context.getLevel().getBlockEntity(pos);
        if (!(be instanceof RandomizableContainerBlockEntity container)) return loot;

        CompoundTag persistent = container.getPersistentData();
        if (!persistent.contains(ChestValuator.TAG_MULT)) return loot;

        float multiplier = RewardBreakdown.clamp(persistent.getFloat(ChestValuator.TAG_MULT));
        persistent.remove(ChestValuator.TAG_MULT);       // one shot
        container.setChanged();

        UUID who = persistent.hasUUID(ChestValuator.TAG_WHO)
                ? persistent.getUUID(ChestValuator.TAG_WHO) : null;
        persistent.remove(ChestValuator.TAG_WHO);

        int before = totalWorth(loot);

        ObjectArrayList<ItemStack> result = loot;
        if (Math.abs(multiplier - 1.0f) >= 0.01f) {
            boolean allowFreshRoll = bonusFreshRollUnlocked(context, who);
            result = multiplier > 1.0f ? grow(loot, multiplier, context, allowFreshRoll) : shrink(loot, multiplier);
            report(context, who, multiplier, before, totalWorth(result));
        }

        // Eldritch Shard is deliberately not another multiplier. It repeats the
        // final chest contents after every ordinary reward/penalty calculation,
        // so its "double the chest" identity remains distinct from Ward Splinter's
        // +50% reward. Separate stacks also avoid overflowing stack-size limits.
        if (persistent.getBoolean(ChestValuator.TAG_DOUBLE_LOOT)) {
            persistent.remove(ChestValuator.TAG_DOUBLE_LOOT);
            ObjectArrayList<ItemStack> doubled = new ObjectArrayList<>();
            doubled.addAll(result);
            for (ItemStack stack : result) {
                if (!stack.isEmpty()) doubled.add(stack.copy());
            }
            result = doubled;
            container.setChanged();
        }

        // A charm now and then, but only out of a chest whose lock was actually
        // played. Winning is what pays for the next lock, not looting in general:
        // if these dropped from any chest they would stop being a decision and
        // become a currency you accumulate without noticing.
        int relicGrade = persistent.getInt(ChestValuator.TAG_RELIC_GRADE);
        persistent.remove(ChestValuator.TAG_RELIC_GRADE);
        if (relicGrade >= 2) {
            result.add(new ItemStack(context.getRandom().nextFloat() < 0.15f
                    ? WardItems.BLOODGLASS_SHARD.get() : WardItems.ELDRITCH_SHARD.get()));
        } else if (relicGrade == 1) {
            result.add(new ItemStack(context.getRandom().nextFloat() < 0.25f
                    ? WardItems.ECHO_SHARD.get() : WardItems.WARD_SPLINTER.get()));
        }
        else addCharmDrop(result, multiplier, before,
                context.getLevel().dimension() == Level.END,
                context.getLevel().dimension() == Level.NETHER,
                context, who);

        // Silas testimony fragments are world-lore, not a combat reward. They can only
        // appear after an actually resolved ward chest and are progression-gated so later
        // truths never leak into the opening chapters.
        if (who != null && context.getLevel().getServer() != null) {
            ServerPlayer witness = context.getLevel().getServer().getPlayerList().getPlayer(who);
            if (witness != null) SilasLore.tryAddWorldFragment(result, witness, context.getRandom());
        }
        return result;
    }

    /**
     * Rolls for a key, and much more rarely a heart.
     *
     * <p>The key's chance is flat, so any beaten lock can feed the next one. The
     * heart's is scaled by how rich the chest was and by how well the lock went,
     * which means the only realistic source is doing well on something already
     * worth doing well on.
     */
    /**
     * Rolls the container's own loot table again and adds what comes out.
     *
     * <p>Uses the same context the modifier was handed, so luck, the killing
     * blow and every other parameter are exactly what the first roll saw. Fails
     * quietly and returns zero if the table id was never recorded or no longer
     * resolves, in which case the caller falls back to duplication.
     */
    private static int freshRoll(LootContext context, ObjectArrayList<ItemStack> into, float share) {
        // One full re-roll represents roughly +1.0 of multiplier. Fractional
        // reward is handled by stack duplication below; rolling a whole table for
        // x1.25 made modest rewards explode on rich modded loot tables.
        if (share < 1.0f) return 0;

        BlockEntity be = context.getParamOrNull(LootContextParams.BLOCK_ENTITY);
        if (!(be instanceof RandomizableContainerBlockEntity)) return 0;
        String id = be.getPersistentData().getString(ChestValuator.TAG_TABLE);
        if (id.isEmpty()) return 0;

        ResourceLocation table = ResourceLocation.tryParse(id);
        ServerLevel level = context.getLevel();
        if (table == null || level == null) return 0;
        LootTable lt = level.getServer().getLootData().getLootTable(table);
        if (lt == null || lt == LootTable.EMPTY) return 0;

        // The context's own params are not reachable, and LootTable only exposes
        // the LootParams entry point publicly, so rebuild the two parameters a
        // chest table actually asks for. Anything else in a chest pool is
        // optional and rolls fine without it.
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(be.getBlockPos()))
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, be)
                .create(LootContextParamSets.CHEST);

        // Only whole multiplier shares become whole table re-rolls. x3.5 is
        // therefore two extra table passes plus a 0.5 fractional duplication
        // budget, rather than three full extra tables.
        int passes = Math.max(1, Math.min(3, (int) Math.floor(share)));
        for (int i = 0; i < passes; i++) {
            for (ItemStack st : lt.getRandomItems(params)) {
                if (st.isEmpty()) continue;
                into.add(st);
            }
        }
        return passes;
    }

    private static void addCharmDrop(ObjectArrayList<ItemStack> loot, float multiplier, int worth,
                                     boolean inTheEnd, boolean inTheNether, LootContext context, UUID who) {
        if (multiplier <= 1.0f) return;      // forced locks give up nothing extra
        int beaten = 0;
        MinecraftServer server = context.getLevel().getServer();
        if (server != null && who != null) beaten = LockData.get(server).totalBeaten(who);

        float heartOdds = WardConfig.heartDropChance
                * Mth.clamp(worth / 260f, 0f, 1f)
                * Mth.clamp(multiplier - 0.9f, 0f, 1.6f);
        if (RNG.nextFloat() < heartOdds) {
            loot.add(new ItemStack(WardItems.ICHORHEART.get()));
            return;                          // never both out of one chest
        }
        // The ember: a mid-progression Nether relic. Both progression and the
        // current reward band are required, so plentiful modpack chests cannot
        // brute-force it during the opening phase.
        if (beaten >= WardConfig.emberAfterBeaten && multiplier >= WardConfig.emberLootThreshold && inTheNether
                && RNG.nextFloat() < WardConfig.emberDropChance) {
            loot.add(new ItemStack(WardItems.SLOW_EMBER.get()));
            return;
        }

        // The Savant, only out of the End. Progression and a high-reward band
        // are both required; this replaces the obsolete x5-chest gate while
        // keeping the relic rare in chest-dense modpacks.
        if (beaten >= WardConfig.eyeAfterBeaten && multiplier >= WardConfig.eyeLootThreshold && inTheEnd
                && RNG.nextFloat() < WardConfig.eyeDropChance) {
            loot.add(new ItemStack(WardItems.THE_SAVANT.get()));
            return;
        }

        // Relics before charms: they are the rarer thing and a chest gives up
        // one extra item, not two.
        if (RNG.nextFloat() < WardConfig.relicDropChance * Mth.clamp(worth / 200f, 0.15f, 1f)) {
            loot.add(new ItemStack(RNG.nextFloat() < 0.28f
                    ? WardItems.CYCLOPEAN_LENS.get() : WardItems.COLD_SHIM.get()));
            return;
        }
        if (RNG.nextFloat() < WardConfig.keyDropChance) {
            loot.add(new ItemStack(WardItems.WARD_KEY.get()));
        }
    }

    private static int totalWorth(List<ItemStack> loot) {
        int n = 0;
        for (ItemStack s : loot) n += worth(s);
        return n;
    }

    /**
     * Tells the player what actually happened to the chest. Without this the reward and the
     * penalty are both invisible: you open a chest and have no way to know whether the multiplier
     * did anything at all.
     */
    private void report(LootContext context, UUID who, float multiplier, int before, int after) {
        if (who == null) return;
        MinecraftServer server = context.getLevel().getServer();
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(who);
        if (player == null) return;

        if (before <= 0) return;
        // Reported as worth rather than item count, because count is the number that lies: taking
        // one diamond and leaving nine cobblestone is a heavy penalty that reads as "1 item lost".
        int percent = Math.round((after - before) * 100f / before);
        String text;
        ChatFormatting colour;
        if (percent > 0) {
            text = String.format("Loot x%.2f  \u2014  haul up %d%%", multiplier, percent);
            colour = ChatFormatting.GREEN;
        } else if (percent < 0) {
            text = String.format("Loot x%.2f  \u2014  %d%% of the haul taken", multiplier, -percent);
            colour = ChatFormatting.RED;
        } else {
            return;
        }
        dev.marrowseal.wardbound.WardHud.message(player, Component.literal(text).withStyle(colour), true);
    }

    /** Worth of one stack, so a bonus lands where it is felt. */
    private static int worth(ItemStack stack) {
        return Math.max(1, ItemValueResolver.score(stack)) * Math.max(1, stack.getCount());
    }

    private static boolean bonusFreshRollUnlocked(LootContext context, UUID who) {
        if (who == null || context == null || context.getLevel() == null) return false;
        MinecraftServer server = context.getLevel().getServer();
        if (server == null) return false;
        return dev.marrowseal.wardbound.LockData.get(server).totalBeaten(who) >= WardConfig.bonusFreshRollAfterBeaten;
    }

    private ObjectArrayList<ItemStack> grow(ObjectArrayList<ItemStack> loot, float multiplier,
                                            LootContext context, boolean allowFreshRoll) {
        ObjectArrayList<ItemStack> bonus = new ObjectArrayList<>();
        float extraShare = multiplier - 1.0f;

        // Rank by worth so the good half of the chest is what multiplies. A stack of one is the
        // normal case in a structure chest, so the fractional part has to be a real roll rather
        // than being floored away, or a x1.6 chest would hand out nothing at all.
        List<ItemStack> ranked = new ArrayList<>(loot);
        ranked.sort(Comparator.comparingInt(WardLootModifier::worth).reversed());
        int topHalf = Math.max(1, ranked.size() / 2);

        // Before anything is duplicated, try to roll the table again.
        //
        // The multiplier used to answer "more loot" with "more of the same
        // loot", because by the time this runs the chest's table has already
        // been consumed and all that is left is the pile it produced. The table
        // id is written onto the container during the scan precisely so it can
        // be rolled a second time here, which is the difference between a chest
        // that pays double and a chest that pays better.
        int freshPasses = allowFreshRoll ? freshRoll(context, bonus, extraShare) : 0;
        if (freshPasses > 0) {
            // Each full table pass consumes one whole multiplier share. The old
            // code subtracted 0.35 per *item stack returned*, so a table with many
            // entries behaved completely differently from one with few entries.
            extraShare = Math.max(0f, extraShare - freshPasses);
        }

        // Unstackables are never duplicated.
        //
        // Copying the stack was fine for arrows and iron, and absurd for a bow:
        // you got a second bow with byte-identical enchantments, which reads as
        // a bug rather than as a reward. Their share is handed to the stackable
        // items instead, so the chest still pays out the full multiplier - it
        // just pays it in things that make sense to have two of.
        float orphanedShare = 0f;
        int stackableCount = 0;
        for (ItemStack stack : loot) {
            if (stack.isEmpty()) continue;
            if (stack.getMaxStackSize() > 1) stackableCount++;
        }

        for (ItemStack stack : loot) {
            if (stack.isEmpty()) continue;
            float share = extraShare;
            if (ranked.indexOf(stack) < topHalf) share *= 1.35f;

            if (stack.getMaxStackSize() <= 1) {
                orphanedShare += share;
                continue;
            }
            if (stackableCount > 0 && orphanedShare > 0f) {
                share += orphanedShare / stackableCount;
            }

            float extraF = stack.getCount() * share;
            int extra = (int) Math.floor(extraF);
            if (RNG.nextFloat() < (extraF - extra)) extra++;
            if (extra <= 0) continue;

            ItemStack copy = stack.copy();
            copy.setCount(Math.min(extra, stack.getMaxStackSize()));
            bonus.add(copy);
        }

        // A chest of nothing but gear has nowhere to put the bonus, so it gets
        // one extra piece and only when the multiplier really earned it.
        if (stackableCount == 0 && orphanedShare >= 0.9f && !ranked.isEmpty()) {
            ItemStack extra = ranked.get(RNG.nextInt(Math.min(3, ranked.size()))).copy();
            extra.setCount(1);
            bonus.add(extra);
        }

        // a chest that rolled a single cheap item should still visibly pay out
        if (bonus.isEmpty() && !loot.isEmpty() && extraShare >= 0.25f) {
            ItemStack best = ranked.get(0).copy();
            best.setCount(1);
            bonus.add(best);
        }

        loot.addAll(bonus);
        return loot;
    }

    private ObjectArrayList<ItemStack> shrink(ObjectArrayList<ItemStack> loot, float multiplier) {
        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack s : loot) if (!s.isEmpty()) kept.add(s);
        if (kept.isEmpty()) return loot;

        int totalWorth = 0;
        for (ItemStack s : kept) totalWorth += worth(s);
        int toRemove = Math.round(totalWorth * (1.0f - multiplier));
        if (toRemove <= 0) return loot;

        // best first: the punishment should be losing the prize, not losing the filler
        kept.sort(Comparator.comparingInt(WardLootModifier::worth).reversed());

        ObjectArrayList<ItemStack> result = new ObjectArrayList<>();
        int removed = 0;
        for (ItemStack stack : kept) {
            int w = worth(stack);
            if (removed < toRemove && removed + w <= toRemove + w / 2) {
                removed += w;
                continue;                                   // this entry is taken outright
            }
            result.add(stack.copy());
        }

        // settle any shortfall by trimming the biggest surviving stack rather than another entry
        if (removed < toRemove && !result.isEmpty()) {
            ItemStack biggest = result.get(0);
            for (ItemStack s : result) if (s.getCount() > biggest.getCount()) biggest = s;
            if (biggest.getCount() > 1) {
                int per = Math.max(1, worth(biggest) / biggest.getCount());
                int trim = Math.min(biggest.getCount() - 1, Math.max(1, (toRemove - removed) / per));
                biggest.shrink(trim);
            }
        }

        // never hand back a completely empty chest; one thing survives as a consolation
        if (result.isEmpty()) {
            ItemStack mercy = kept.get(kept.size() - 1).copy();
            mercy.setCount(1);
            result.add(mercy);
        }
        return result;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
