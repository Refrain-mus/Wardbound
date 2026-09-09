package dev.marrowseal.wardbound;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Hidden +1 cards that sit outside the normal shelf economy. Their visible face is deliberately
 * incomplete; the actual result is rolled only after the player signs the card.
 */
public final class AnomalyCardSystem {
    private AnomalyCardSystem() {}

    public static boolean isAnomaly(ForbiddenBargain card) {
        return card == ForbiddenBargain.ANOMALY_GILDED || card == ForbiddenBargain.ANOMALY_FERAL
                || card == ForbiddenBargain.ANOMALY_HOLLOW || card == ForbiddenBargain.ANOMALY_STATIC
                || card == ForbiddenBargain.ANOMALY_MIRROR || card == ForbiddenBargain.ANOMALY_BLACK;
    }

    public static boolean available(LockData data, UUID id, ForbiddenBargain card) {
        return data != null && id != null && card != null && data.totalBeaten(id) >= card.minResolved;
    }

    public static String hiddenTitle(ForbiddenBargain card) {
        return switch (card) {
            case ANOMALY_GILDED -> "??? // GILDED STATIC";
            case ANOMALY_FERAL -> "??? // SOMETHING MOVES";
            case ANOMALY_HOLLOW -> "??? // AN EMPTY ADDRESS";
            case ANOMALY_STATIC -> "??? // SIGNAL WITHOUT SOURCE";
            case ANOMALY_MIRROR -> "??? // YOUR HAND, WRONG";
            case ANOMALY_BLACK -> "??? // DO NOT NAME IT";
            default -> "??? // ANOMALY";
        };
    }

    public static String hiddenBody(ForbiddenBargain card) {
        return switch (card) {
            case ANOMALY_GILDED -> "Something valuable may be inside. Value and safety are not synonyms.";
            case ANOMALY_FERAL -> "The card is warm. The ink occasionally behaves like a footprint.";
            case ANOMALY_HOLLOW -> "There is a destination in the paper. It has not agreed to stay still.";
            case ANOMALY_STATIC -> "The symbols change whenever you stop looking directly at them.";
            case ANOMALY_MIRROR -> "The card appears to contain something you already own. This is probably false.";
            case ANOMALY_BLACK -> "No clause can be read. The margin has been crossed out from the inside.";
            default -> "The result is not written until the card is signed.";
        };
    }

    /** Base +1-slot chance. Queued anomaly invitations are handled by ChestValuator. */
    public static float chance(LockData data, UUID id, boolean loose) {
        if (data == null || id == null) return 0f;
        int resolved = data.totalBeaten(id);
        if (resolved < 35) return 0f;
        float base = resolved < 120 ? 0.045f : resolved < 300 ? 0.060f : resolved < 600 ? 0.078f : 0.095f;
        if (loose) base *= 0.65f;
        int lure = data.uniqueInt(id, "anomaly_lure");
        if (lure > 0) base += 0.16f;
        if (data.uniqueInt(id, "anomaly_guarantee") > 0) return 1f;
        return Math.min(0.32f, base);
    }

    public static ForbiddenBargain pick(LockData data, UUID id, Random random) {
        List<ForbiddenBargain> fresh = new ArrayList<>();
        List<ForbiddenBargain> recent = new ArrayList<>();
        for (ForbiddenBargain card : ForbiddenBargain.values()) {
            if (!isAnomaly(card) || !available(data, id, card)) continue;
            (data.wasRecentlyOffered(id, card.id) ? recent : fresh).add(card);
        }
        List<ForbiddenBargain> pool = fresh.isEmpty() ? recent : fresh;
        if (pool.isEmpty()) return null;
        return pool.get(random.nextInt(pool.size()));
    }

    public static void consumeAppearanceQueue(LockData data, UUID id) {
        if (data.uniqueInt(id, "anomaly_guarantee") > 0)
            data.setUniqueInt(id, "anomaly_guarantee", Math.max(0, data.uniqueInt(id, "anomaly_guarantee") - 1));
        consumeLureAttempt(data, id);
    }

    public static void consumeLureAttempt(LockData data, UUID id) {
        if (data.uniqueInt(id, "anomaly_lure") > 0)
            data.setUniqueInt(id, "anomaly_lure", Math.max(0, data.uniqueInt(id, "anomaly_lure") - 1));
    }

    public static void resolve(ServerPlayer player, LockData data, ForbiddenBargain card) {
        if (player == null || data == null || card == null || !(player.level() instanceof ServerLevel level)) return;
        UUID id = player.getUUID();
        boolean restrained = data.uniqueInt(id, "anomaly_white_thread") > 0;
        if (restrained) data.setUniqueInt(id, "anomaly_white_thread", Math.max(0, data.uniqueInt(id, "anomaly_white_thread") - 1));

        Random random = new Random(level.getGameTime() ^ player.getUUID().getLeastSignificantBits() ^ (long)card.id * 0x9E3779B97F4A7C15L);
        int roll = random.nextInt(100);
        String result;
        boolean bad = false;

        switch (card) {
            case ANOMALY_GILDED -> {
                if (roll < 38) result = giveRandomRegisteredItem(player, random, 1, false);
                else if (roll < 53) result = giveRandomSpawnEgg(player, random);
                else if (roll < 68) { ExperienceOrb.award(level, player.position(), 12 + random.nextInt(19)); result = "The anomaly paid experience instead of an object."; }
                else if (roll < 82) { data.setUniqueInt(id, "deck_plus_one", Math.max(1, data.uniqueInt(id, "deck_plus_one"))); result = "The anomaly left one extra card waiting in the next hand."; }
                else if (roll < 94 || restrained) { player.addEffect(new MobEffectInstance(MobEffects.LUCK, 20*60*3, 1, false, true, true)); result = "The gilt flaked away as three minutes of impossible luck."; }
                else { bad = true; result = applyRandomCurse(player, data, random); }
            }
            case ANOMALY_FERAL -> {
                if (roll < 24) { WildCardEffects.apply(player,data,ForbiddenBargain.BEAST_MARCH); result = "Nearby animals began treating you like a migration route."; }
                else if (roll < 42) { WildCardEffects.apply(player,data,ForbiddenBargain.CREEPER_COURTESY); result = "Creepers entered an unsolicited temporary truce."; }
                else if (roll < 57) result = giveRandomSpawnEgg(player, random);
                else if (roll < 72) { WildCardEffects.apply(player,data,ForbiddenBargain.GOLEM_ESCORT); result = "Iron nearby briefly remembered a protective interpretation of violence."; }
                else if (roll < 88 || restrained) { player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,20*75,1,false,true,true)); player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*75,0,false,true,true)); result = "The feral margin lent you seventy-five seconds of predatory certainty."; }
                else { bad=true; WildCardEffects.apply(player,data,ForbiddenBargain.GOLEM_ENMITY); result = "The feral margin convinced nearby iron that you were the problem."; }
            }
            case ANOMALY_HOLLOW -> {
                if (roll < 22) { WildCardEffects.apply(player,data,ForbiddenBargain.WRONG_DOOR); result = "The paper opened somewhere else and took you with it."; }
                else if (roll < 42) { WildCardEffects.apply(player,data,ForbiddenBargain.RETURN_ADDRESS); result = "The anomaly recorded your current position and promised to remember it later."; }
                else if (roll < 58) { WildCardEffects.apply(player,data,ForbiddenBargain.TEN_SECONDS_UNWRITTEN); result = "For ten seconds, injury stopped finding your address."; }
                else if (roll < 74) { player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,20*30,0,false,true,true)); player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,20*60*3,0,false,true,true)); result = "Thirty seconds of absolute dark were followed by an excess of sight."; }
                else if (roll < 91 || restrained) { result = giveRandomRegisteredItem(player, random, 2, false); }
                else { bad=true; WildCardEffects.apply(player,data,ForbiddenBargain.CHORUS_ERROR); result = "The hollow address began moving you every twenty seconds."; }
            }
            case ANOMALY_STATIC -> {
                if (roll < 23) { WildCardEffects.apply(player,data,ForbiddenBargain.CHANCE_ENGINE); result = "The signal selected one temporary law without displaying the menu."; }
                else if (roll < 40) { lightningHostiles(player, level, 4); result = "The signal grounded itself through nearby hostile bodies."; }
                else if (roll < 57) { data.setUniqueInt(id,"deck_rare_ink",1); result = "The next hand was marked for rarer ink."; }
                else if (roll < 72) { result = giveRandomRegisteredItem(player, random, 1, true); }
                else if (roll < 90 || restrained) { player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED,20*90,2,false,true,true)); result = "Static resolved into ninety seconds of Haste III."; }
                else { bad=true; player.addEffect(new MobEffectInstance(MobEffects.CONFUSION,20*35,1,false,true,true)); player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,20*35,1,false,true,true)); result = "The signal entered your nervous system instead of leaving the card."; }
            }
            case ANOMALY_MIRROR -> {
                if (roll < 25) result = duplicateRandomInventoryItem(player, random);
                else if (roll < 43) { float heal = Math.min(player.getMaxHealth()-player.getHealth(), 10f); player.heal(10f); result = heal > 0 ? "The mirror returned five hearts that looked used." : "The mirror found no missing health and returned nothing."; }
                else if (roll < 58) { WildCardEffects.apply(player,data,ForbiddenBargain.MOB_EXCHANGE); result = "You traded places with something that had not agreed to the exchange."; }
                else if (roll < 73) { data.setUniqueInt(id,"deck_no_echoes",Math.max(2,data.uniqueInt(id,"deck_no_echoes"))); result = "The next two hands were instructed not to repeat themselves."; }
                else if (roll < 91 || restrained) { player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,20*75,1,false,true,true)); result = "The mirror decided the healthier version was authoritative."; }
                else { bad=true; player.setHealth(Math.max(2f, player.getHealth()/2f)); result = "The mirror kept the healthier half."; }
            }
            case ANOMALY_BLACK -> {
                if (roll < 18 && !restrained) { bad=true; result = applyRandomCurse(player, data, random); }
                else if (roll < 33 && !restrained) { bad=true; AttentionSystem.set(player,data,data.attention(id)+6,"a black anomaly noticed the player"); result = "The black card did not change you. It changed how closely the system watches you."; }
                else if (roll < 48 && !restrained) { bad=true; player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,20*60,0,false,true,true)); player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,20*60,1,false,true,true)); result = "One minute was crossed out of your senses and strength."; }
                else if (roll < 63) { WildCardEffects.apply(player,data,ForbiddenBargain.WARDENS_BLIND_SPOT); result = "For four minutes, Wardens found an intentional blank where you should be."; }
                else if (roll < 77) { WildCardEffects.apply(player,data,ForbiddenBargain.TEN_SECONDS_UNWRITTEN); result = "Ten seconds were removed from the damage ledger."; }
                else if (roll < 90) { data.setUniqueInt(id,"deck_deep_shelf",1); result = "The next hand was given permission to reach a shelf it normally hesitates to touch."; }
                else { result = giveRandomRegisteredItem(player, random, 2, false); }
            }
            default -> { result = "The anomaly folded itself into an ordinary blank."; }
        }

        int opened = data.uniqueInt(id, "anomalies_opened") + 1;
        data.setUniqueInt(id, "anomalies_opened", opened);
        data.setUniqueInt(id, bad ? "anomaly_bad" : "anomaly_good", data.uniqueInt(id, bad ? "anomaly_bad" : "anomaly_good") + 1);
        data.setUniqueInt(id, "last_anomaly_card", card.id);
        if (opened == 1) {
            dev.marrowseal.wardbound.item.CthulhuEyeItem.speakInsight(player, "first_anomaly",
                    "That card was not authored by the hand that carried it. More importantly, its outcome did not exist until you signed it. I recommend distrusting any future card that appears to orbit its own border.", 20L * 180L);
        } else if (bad && data.uniqueInt(id, "anomaly_bad") == 3) {
            dev.marrowseal.wardbound.item.CthulhuEyeItem.speakInsight(player, "anomaly_bad_three",
                    "Three hostile anomaly resolutions. Statistically meaningful? Barely. Emotionally persuasive? Apparently.", 20L * 240L);
        }
        WardHistory.recordSpecial(player, bad ? "DANGER" : "WARD", "ANOMALY // " + result);
        WardHud.message(player, Component.literal("ANOMALY RESOLVED // " + result)
                .withStyle(bad ? ChatFormatting.DARK_RED : ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        level.playSound(null, player.blockPosition(), bad ? SoundEvents.SCULK_SHRIEKER_SHRIEK : SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, bad ? 0.55f : 0.70f, bad ? 0.62f : 1.18f);
        level.sendParticles(bad ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD,
                player.getX(), player.getY()+1.0, player.getZ(), 26, 0.45, 0.65, 0.45, 0.035);
    }

    public static String giveRandomRegisteredItem(ServerPlayer player, Random random, int draws, boolean allowSeveral) {
        List<Item> items = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) if (item != Items.AIR) items.add(item);
        if (items.isEmpty()) return "The registry returned an empty line.";
        String last = "unknown item";
        for (int d=0; d<Math.max(1,draws); d++) {
            Item item = items.get(random.nextInt(items.size()));
            ItemStack stack = new ItemStack(item);
            if (allowSeveral && stack.getMaxStackSize() > 1) stack.setCount(1 + random.nextInt(Math.min(3, stack.getMaxStackSize())));
            last = stack.getHoverName().getString();
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
        return draws > 1 ? "The registry produced " + draws + " unrelated objects; the last was " + last + "."
                : "The registry produced: " + last + ".";
    }

    public static String giveRandomSpawnEgg(ServerPlayer player, Random random) {
        List<Item> eggs = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) if (item instanceof SpawnEggItem) eggs.add(item);
        if (eggs.isEmpty()) return giveRandomRegisteredItem(player, random, 1, false);
        ItemStack stack = new ItemStack(eggs.get(random.nextInt(eggs.size())));
        String name = stack.getHoverName().getString();
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        return "The anomaly produced an unidentified promise of life: " + name + ".";
    }

    private static String duplicateRandomInventoryItem(ServerPlayer player, Random random) {
        List<ItemStack> candidates = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) if (!stack.isEmpty()) candidates.add(stack);
        if (candidates.isEmpty()) return "The mirror found an empty inventory and had nothing to imitate.";
        ItemStack source = candidates.get(random.nextInt(candidates.size()));
        ItemStack copy = source.copy();
        copy.setCount(1);
        if (!player.getInventory().add(copy)) player.drop(copy, false);
        return "The mirror copied one " + copy.getHoverName().getString() + ".";
    }

    private static String applyRandomCurse(ServerPlayer player, LockData data, Random random) {
        List<ForbiddenBargain> curses = new ArrayList<>();
        for (ForbiddenBargain card : ForbiddenBargain.values())
            if (card.kind == ForbiddenBargain.Kind.CURSE && card.available(data, player.getUUID(), true)) curses.add(card);
        if (curses.isEmpty()) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,20*90,1,false,true,true));
            return "No legal Curse was available, so the anomaly wrote ninety seconds of Weakness instead.";
        }
        ForbiddenBargain curse = curses.get(random.nextInt(curses.size()));
        WardHistory.discoverCard(player, curse);
        WardHistory.acceptCard(player, curse);
        ChestValuator.applyCardState(player, data, curse);
        return "The hidden card contained another card: " + curse.title + ".";
    }

    private static void lightningHostiles(ServerPlayer player, ServerLevel level, int max) {
        int n = 0;
        for (var mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(18), m -> m instanceof net.minecraft.world.entity.monster.Enemy)) {
            var bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(mob.getX(), mob.getY(), mob.getZ());
                bolt.setCause(player);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            mob.hurt(level.damageSources().lightningBolt(), 5.0F);
            if (++n >= max) break;
        }
    }
}
