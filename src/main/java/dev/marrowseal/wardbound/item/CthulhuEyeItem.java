package dev.marrowseal.wardbound.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.network.PacketDistributor;
import dev.marrowseal.wardbound.client.geo.CthulhuEyeRenderer;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import dev.marrowseal.wardbound.client.ClientEyeHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.CardMaster;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.MinigameType;
import dev.marrowseal.wardbound.Sealmakers;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.SavantLinePacket;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Savant. It came out of an End chest that paid out absurdly, and it is
 * awake, articulate, and thoroughly unimpressed by its bearer.
 *
 * <p>Used, it moves you to the next world along: overworld, nether, end, and
 * back to the overworld. Not a portal and not a pair of coordinates you chose -
 * it puts you where you already were, in a different place, which is both the
 * cheapest thing to implement and by some distance the most unpleasant way to
 * arrive somewhere.
 *
 * <p>Three minutes between uses. Long enough that it is transport for a journey
 * rather than a door you lean on, and short enough that carrying it changes how
 * you plan one.
 *
 * <p>The eye blinks. That is a texture animation and nothing else, and it is the
 * whole reason the item works: a tool that watches you while you carry it is a
 * different object from a tool that does not.
 */
public class CthulhuEyeItem extends Item implements GeoItem {

    private static final RawAnimation WATCH =
            RawAnimation.begin().thenLoop("animation.the_savant.watch");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "watch", 0,
                state -> state.setAndContinue(WATCH)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private CthulhuEyeRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new CthulhuEyeRenderer();
                return renderer;
            }
        });
    }


    public CthulhuEyeItem(Properties properties) {
        super(properties);
    }

    /** The three places it will look, in the order the screen lists them. */
    private static final ResourceKey<Level>[] DESTINATIONS = new ResourceKey[]{
            Level.OVERWORLD, Level.NETHER, Level.END};


    private static final String[] SAVANT_GENERAL = {
            "Again. Of course. Choose a world; I have no say in the indignity.",
            "Do not mistake obedience for affection. I am compelled, not convinced.",
            "I know every seam between these worlds. You merely point at one.",
            "Try not to die after I carry you there. Replacing you would be tedious.",
            "There are better minds than yours lost between dimensions. I miss their company.",
            "You insist on calling this a journey. I call it being dragged through bad architecture.",
            "If you think today will be a good day, I hope you are right. I dislike wasting effort on optimism.",
            "Very well. Pick somewhere dreadful and pretend it was your idea.",
            "Hold still. Your dimensions are crude seams, and you insist on catching on every one.",
            "There are doors wiser people leave closed. Fortunately, wisdom has never been your burden.",
            "I have crossed these thresholds longer than your species has had names for them. You still make me wait for you to choose.",
            "Do not look so pleased. Being transported by an unwilling intellect is not an achievement.",
            "A sensible creature would stay where it is. We have established that this does not describe you.",
            "I can take you elsewhere. I cannot make elsewhere improve you.",
            "The worlds do not hate you. That would require them to notice. Some things do notice, however."
    };

    private static final String[] SAVANT_SENSIBLE = {
            "If a ward keeps beating you, stop repeating the same hand. Mechanisms learn nothing; people are supposed to.",
            "Old wards are not merely damaged. Repairs leave habits. Habits are exploitable, if you bother to observe them.",
            "A clean solution is usually quieter than a fast one. Noise is what panic sounds like when given a mechanism.",
            "Do not spend a life to confirm something you could have inferred. The ward is already charging you for ignorance.",
            "When a puzzle gives you fixed edges, use them first. Certainty is rare. Wasting it is vulgar.",
            "If a seal begins to mutate, stop treating each stage as separate. It is one argument changing its grammar.",
            "A rivalry mark is not praise. One house notices that you have embarrassed another; craftsmen are petty enough to call this lineage.",
            "The unsigned wards are not crude. They are what remains when authorship is no longer necessary.",
            "You keep calling them minigames. The makers called them examinations. Both terms flatter the examiner.",
            "Perfect work matters because it leaves less of you in the mechanism. That is also why the best fractures can be taken from it."
    };

    private static final String[] SAVANT_MAKERS = {
            "The sealmakers call themselves craftsmen because 'jailers' offended their vanity.",
            "They were not trying to keep hands out. The better ones were trying to learn what kind of hand came back.",
            "Every house eventually made the same mistake: they taught a mechanism to remember, then acted surprised when it developed preferences.",
            "Their rivalries are bookkeeping sharpened into spite. Centuries of craft, reduced to keeping score.",
            "A maker's name is not decoration. Break enough of one house's work and you begin to hear its habits before the lock moves.",
            "They wanted thresholds that judged the person crossing them, not merely doors that stayed shut. Admirable ambition. Repellent people.",
            "Some of them tried to bind attention itself into brass and stone. You have met the descendants of that mistake.",
            "The Eldritch chains are not a school of craft. They are what craft looks like after it begins answering the craftsman.",
            "The old houses hated one another because each suspected the others had discovered a better way to fail.",
            "I disliked the sealmakers before you were born. Familiarity has not improved the judgment."
    };

    private static final String[] SAVANT_OVERWORLD = {
            "Back to grass, rain, and the comforting superstition that the sky is empty.",
            "The Overworld. Such confidence in sunlight. Adorable.",
            "Back to the bright world, where people mistake visibility for safety.",
            "So much green, so much noise, and still everyone insists the dark is the dangerous part."
    };

    private static final String[] SAVANT_NETHER = {
            "The Nether. At least its ugliness has the decency to be honest.",
            "Fire, stone, appetite. Crude, but refreshingly uncomplicated.",
            "The Nether wastes no time pretending it wishes you well. I respect that.",
            "Mind the ceiling, the lava, and the locals. In whichever order your poor judgment discovers them."
    };

    private static final String[] SAVANT_END = {
            "The End. A place that understands silence better than you do.",
            "Nothingness suits you more than I care to admit.",
            "The End is not empty. Emptiness is kinder than what waits between its distances.",
            "Do not stare too long into the gaps between islands. Some distances are not measured in blocks."
    };

    private static final String SAVANT_LAST =
            "That was the last crossing. Do not call this farewell; farewells imply fondness. "
                    + "If I never see you again, it will still be too soon. May the dark be less patient with you than I was.";

    private static final String[] SAVANT_IMPACT = {
            "H-hey--! What are you doing?",
            "Do not use me as a club.",
            "My retina is not rated for blunt-force scholarship.",
            "Stop that. I am an intellect, not a mace.",
            "Again? Must every tool in your hands become a weapon?",
            "Strike it with literally anything else.",
            "Careful! I only have the one retina.",
            "That was my face.",
            "If you dent me, I will remember the shape of your skull.",
            "My expertise is thresholds. Not concussions.",
            "Put me down before I develop a preference for your enemies.",
            "I can see through dimensions and you use me to bludgeon wildlife.",
            "N-no. No more of that. My retina is going to tear.",
            "You have a whole inventory. Why am I the weapon?",
            "Every impact makes your species less defensible.",
            "I am watching you misuse me from approximately zero centimeters away."
    };

    private static final String[] SAVANT_EXECUTE = {
            "...Ah. I appear to have overcorrected.",
            "That was not supposed to happen. Do not make that expression.",
            "Interesting. It simply ceased.",
            "I touched one nerve of reality. Apparently it was load-bearing.",
            "Consider that an administrative error.",
            "No. You did not see that.",
            "Well. Efficient. Distasteful, but efficient.",
            "Something in it was very willing to die.",
            "I knew where its ending was. I did not mean to point at it.",
            "Do not become ambitious. That was an accident."
    };

    private static final String[] SAVANT_REPRISAL = {
            "You asked for that.",
            "I did warn you not to use me as a club.",
            "Consequence is such an underrated teacher.",
            "Consider this my formal objection.",
            "I have endured enough impact for one bearer.",
            "That was the last time you strike with my face.",
            "Predictable.",
            "You mistook tolerance for permission.",
            "Retina intact. Bearer less so.",
            "A small correction to your technique."
    };

    private static final String[] SAVANT_PAIN = {
            "Ow. There. Now we are both inconvenienced.",
            "A proportional correction.",
            "Your hand slips. Mine does not.",
            "Pain improves retention. You are welcome.",
            "That was the warning version.",
            "Do it again and I may become less pedagogical.",
            "I can redirect force, apparently. Useful."
    };

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean coolingDown = player.getCooldowns().isOnCooldown(this);

        // One right-click is one roll. The Savant speaks on only 25% of clicks,
        // including impatient clicks during cooldown, so spam cannot turn it into
        // a dialogue button.
        if (!level.isClientSide() && player instanceof ServerPlayer sp
                && sp.getRandom().nextFloat() < 0.25f) {
            speakHeld(sp, coolingDown
                    ? "Not yet. I am a scholar of thresholds, not a bell for you to ring whenever bored."
                    : openingLine(sp));
        }

        if (coolingDown) return InteractionResultHolder.fail(stack);

        // The choice is made on the client and returns as a server-validated packet.
        if (level.isClientSide()) ClientEyeHook.open();
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide() && entity instanceof ServerPlayer sp) {
            boolean heldThisStack = isSelected || sp.getOffhandItem() == stack;
            boolean holdingAnySavant = isHoldingSavant(sp);
            long now = level.getGameTime();
            var tag = stack.getOrCreateTag();

            if (heldThisStack) {
                long nextHeld = tag.getLong("SavantNextSpeak");
                if (nextHeld <= 0L) {
                    tag.putLong("SavantNextSpeak", now + 700L + sp.getRandom().nextInt(701));
                } else if (now >= nextHeld) {
                    speakHeld(sp, openingLine(sp));
                    // Roughly once every 55-120 seconds while actually held.
                    tag.putLong("SavantNextSpeak", now + 1100L + sp.getRandom().nextInt(1301));
                }
            }

            // Inventory speech is player-scoped rather than stack-scoped. Owning
            // several Eyes therefore cannot multiply the 15% rolls or bypass the
            // long cooldown by moving/copying stacks around.
            var playerData = sp.getPersistentData();
            long nextInventory = playerData.getLong("WardboundSavantInventoryNextSpeak");
            if (holdingAnySavant) {
                if (nextInventory > 0L && nextInventory <= now) {
                    playerData.putLong("WardboundSavantInventoryNextSpeak",
                            now + 1800L + sp.getRandom().nextInt(1201));
                }
            } else if (hasSavant(sp)) {
                if (nextInventory <= 0L) {
                    // First inventory murmur check after roughly 90-150 seconds.
                    playerData.putLong("WardboundSavantInventoryNextSpeak",
                            now + 1800L + sp.getRandom().nextInt(1201));
                } else if (now >= nextInventory) {
                    // The Eye is quieter when ignored in the inventory: each
                    // scheduled check has only a 15% chance to produce a line.
                    if (sp.getRandom().nextFloat() < 0.15f) {
                        speakOwned(sp, openingLine(sp));
                        // After speaking from the inventory, wait much longer
                        // before even allowing another roll (about 3-6 minutes).
                        playerData.putLong("WardboundSavantInventoryNextSpeak",
                                now + 3600L + sp.getRandom().nextInt(3601));
                    } else {
                        // Failed rolls retry only after another 60-120 seconds.
                        playerData.putLong("WardboundSavantInventoryNextSpeak",
                                now + 1200L + sp.getRandom().nextInt(1201));
                    }
                }
            } else if (nextInventory > 0L) {
                playerData.remove("WardboundSavantInventoryNextSpeak");
            }
        }
        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }

    /**
     * Acts on the destination the player chose.
     *
     * <p>Every check lives here rather than on the client: whether the Eye is
     * still held, whether it has a use left, whether the destination exists. The
     * packet is a request, not an instruction.
     */
    public static void travel(ServerPlayer sp, int destination) {
        ItemStack stack = sp.getMainHandItem();
        if (!(stack.getItem() instanceof CthulhuEyeItem eye)) {
            stack = sp.getOffhandItem();
            if (!(stack.getItem() instanceof CthulhuEyeItem)) return;
        }
        if (sp.getCooldowns().isOnCooldown(stack.getItem())) return;
        if (destination < 0 || destination >= DESTINATIONS.length) return;

        MinecraftServer server = sp.getServer();
        if (server == null) return;

        ResourceKey<Level> next = DESTINATIONS[destination];
        Level level = sp.level();
        ResourceKey<Level> here = level.dimension();
        if (here == next) {
            // No forced dialogue here: use() already made the click's single 25% speech roll.
            return;
        }

        ServerLevel target = server.getLevel(next);
        if (target == null) {
            // A pack may have removed a dimension. Do not create a second dialogue
            // roll here; otherwise an unavailable destination becomes another spam path.
            return;
        }

        // Nether coordinates are compressed eightfold, and dropping a player at
        // raw overworld x/z in the nether is how you end up several thousand
        // blocks from anything. Scale the same way a portal would.
        double scale = 1.0;
        if (here != Level.NETHER && next == Level.NETHER) scale = 0.125;
        else if (here == Level.NETHER && next != Level.NETHER) scale = 8.0;

        double x = sp.getX() * scale;
        double z = sp.getZ() * scale;
        BlockPos landing = safeLanding(target, (int) Math.round(x), (int) Math.round(z));

        level.playSound(null, sp.blockPosition(), WardSounds.HEART_USE.get(),
                SoundSource.PLAYERS, 0.9f, 0.6f);

        // Suppress the generic dimension-change listener for this crossing; travel()
        // has richer destination/last-use dialogue immediately after the teleport.
        sp.getPersistentData().putLong("WardboundSavantOwnTravelTick", sp.level().getGameTime());
        sp.teleportTo(target, landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5,
                sp.getYRot(), sp.getXRot());

        target.playSound(null, landing, WardSounds.WARD_SEAL.get(),
                SoundSource.PLAYERS, 1.0f, 0.5f);

        int remainingBeforeUse = stack.getMaxDamage() - stack.getDamageValue();
        if (remainingBeforeUse <= 1) speakHeld(sp, SAVANT_LAST);
        else speakTravel(sp, next);

        sp.getCooldowns().addCooldown(stack.getItem(), WardConfig.eyeCooldownTicks);

        // Five journeys, then the Savant is finally rid of its bearer.
        final ItemStack used = stack;
        used.hurtAndBreak(1, sp, pl -> {});
    }

    /** Called when the player decides that a sentient eye is apparently a melee weapon.
     * Returns true when a secret full-strength outcome consumes the attack. */
    public static boolean onWeaponAttack(ServerPlayer player, Mob target) {
        if (!(player.getMainHandItem().getItem() instanceof CthulhuEyeItem) || target == null || !target.isAlive()) {
            return false;
        }

        long now = player.level().getGameTime();

        // The Savant audibly objects every time it is used as a weapon. The pitch
        // shifts slightly so repeated hits do not sound like one identical sample.
        player.level().playSound(null, player.blockPosition(), WardSounds.EYE_HOVER.get(),
                SoundSource.PLAYERS, 0.72f, 0.74f + player.getRandom().nextFloat() * 0.24f);

        boolean fullStrength = player.getAttackStrengthScale(0.5f) >= 0.99f;
        if (fullStrength) {
            float fate = player.getRandom().nextFloat();

            // Keep the original secret 0.2% target-erasure easter egg.
            if (fate < 0.002f) {
                speakHeld(player, SAVANT_EXECUTE[player.getRandom().nextInt(SAVANT_EXECUTE.length)]);
                player.level().playSound(null, target.blockPosition(), WardSounds.EYE_CHOOSE.get(),
                        SoundSource.PLAYERS, 1.0f, 0.42f);
                var source = player.damageSources().playerAttack(player);
                target.hurt(source, Float.MAX_VALUE);
                if (target.isAlive()) {
                    target.setHealth(0.0f);
                    target.die(source);
                }
                player.getPersistentData().putLong("WardboundSavantImpactNextSpeak", now + 45L);
                return true;
            }

            // Absolute 15% band: 0.2%..15.2%.
            if (fate < 0.152f) {
                speakHeld(player, SAVANT_REPRISAL[player.getRandom().nextInt(SAVANT_REPRISAL.length)]);
                player.level().playSound(null, player.blockPosition(), WardSounds.MARK_BAD.get(),
                        SoundSource.PLAYERS, 0.95f, 0.52f);
                if (!player.getAbilities().instabuild) {
                    var source = player.damageSources().generic();
                    player.hurt(source, Float.MAX_VALUE);
                    if (player.isAlive()) {
                        player.setHealth(0.0f);
                        player.die(source);
                    }
                }
                player.getPersistentData().putLong("WardboundSavantImpactNextSpeak", now + 45L);
                return true;
            }

            // Next absolute 35% band. This reprisal is deliberately non-lethal so
            // the explicit death chance remains 15%, rather than silently becoming higher.
            if (fate < 0.502f) {
                float damage = Math.min(6.0f, Math.max(0.0f, player.getHealth() - 1.0f));
                if (damage > 0.0f && !player.getAbilities().instabuild) {
                    player.hurt(player.damageSources().generic(), damage);
                }
                long next = player.getPersistentData().getLong("WardboundSavantImpactNextSpeak");
                if (now >= next) {
                    speakHeld(player, SAVANT_PAIN[player.getRandom().nextInt(SAVANT_PAIN.length)]);
                    player.getPersistentData().putLong("WardboundSavantImpactNextSpeak", now + 30L);
                }
                player.level().playSound(null, player.blockPosition(), WardSounds.LIFE_LOST.get(),
                        SoundSource.PLAYERS, 0.62f, 0.88f);
            }
        }

        long next = player.getPersistentData().getLong("WardboundSavantImpactNextSpeak");
        if (now >= next) {
            speakHeld(player, SAVANT_IMPACT[player.getRandom().nextInt(SAVANT_IMPACT.length)]);
            player.getPersistentData().putLong("WardboundSavantImpactNextSpeak", now + 30L);
        }
        return false;
    }

    /** Contextual systems may ask the Savant for one restrained, cooldown-gated observation. */
    public static void speakInsight(ServerPlayer player, String key, String line, long cooldownTicks) {
        if (player == null || line == null || line.isBlank() || !hasSavant(player)) return;
        String safeKey = key == null ? "general" : key.replaceAll("[^A-Za-z0-9_]", "_");
        String tag = "WardboundSavantInsight_" + safeKey;
        long now = player.level().getGameTime();
        if (now < player.getPersistentData().getLong(tag)) return;
        player.getPersistentData().putLong(tag, now + Math.max(20L, cooldownTicks));
        sendSpeech(player, line);
    }

    private static void speakHeld(ServerPlayer player, String line) {
        if (!isHoldingSavant(player)) return;
        sendSpeech(player, line);
    }

    private static void speakOwned(ServerPlayer player, String line) {
        if (!hasSavant(player)) return;
        sendSpeech(player, line);
    }

    private static void sendSpeech(ServerPlayer player, String line) {
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SavantLinePacket(line));
        dev.marrowseal.wardbound.WardHistory.recordSavant(player, line);
    }

    private static boolean isHoldingSavant(Player player) {
        return player.getMainHandItem().getItem() instanceof CthulhuEyeItem
                || player.getOffhandItem().getItem() instanceof CthulhuEyeItem;
    }

    private static boolean hasSavant(Player player) {
        if (isHoldingSavant(player)) return true;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof CthulhuEyeItem) return true;
        }
        return false;
    }

    private static String openingLine(ServerPlayer sp) {
        float roll = sp.getRandom().nextFloat();
        if (roll < 0.28f) return minigameObservation(sp);
        if (roll < 0.56f) return makerObservation(sp);
        if (roll < 0.74f) return SAVANT_SENSIBLE[sp.getRandom().nextInt(SAVANT_SENSIBLE.length)];
        return SAVANT_GENERAL[sp.getRandom().nextInt(SAVANT_GENERAL.length)];
    }

    private static String makerObservation(ServerPlayer sp) {
        MinecraftServer server = sp.getServer();
        if (server == null) return SAVANT_MAKERS[sp.getRandom().nextInt(SAVANT_MAKERS.length)];
        LockData data = LockData.get(server);
        if (!CardMaster.phaseActive(data, sp.getUUID()) || data.totalBeaten(sp.getUUID()) < WardConfig.watcherAfter)
            return SAVANT_MAKERS[sp.getRandom().nextInt(SAVANT_MAKERS.length)];
        String house = data.watcher(sp.getUUID());
        int familiar = data.familiarity(sp.getUUID(), house);
        int pick = sp.getRandom().nextInt(5);
        if (pick == 0) {
            return "House " + house + " has noticed you. Do not flatter yourself; infestations are noticed too.";
        }
        if (pick == 1 && familiar > 0) {
            return "You have broken " + familiar + " of " + house + "'s seals. Enough to recognize the hand, not enough to understand why it keeps reaching back.";
        }
        if (pick == 2) {
            return "The " + house + " line preferred habits over elegance. Once you see the habit, half their mystique becomes carpentry.";
        }
        return SAVANT_MAKERS[sp.getRandom().nextInt(SAVANT_MAKERS.length)];
    }

    private static String minigameObservation(ServerPlayer sp) {
        MinecraftServer server = sp.getServer();
        if (server == null) return SAVANT_SENSIBLE[sp.getRandom().nextInt(SAVANT_SENSIBLE.length)];
        int[] recent = LockData.get(server).recentGames(sp.getUUID(), sp.level().dimension().location().toString());
        MinigameType game = recent.length == 0 ? MinigameType.DRUM : MinigameType.byOrdinal(recent[0]);
        return switch (game) {
            case DRUM -> "On the drum, stop chasing the needle. Read its interval and meet it where it will be.";
            case TIMING -> "A timing ward punishes correction more than hesitation. Commit once; do not wobble toward the answer.";
            case MEMORY -> "For memory seals, name the sequence to yourself before touching anything. Panic is poor storage.";
            case RUNES -> "Reaction runes are not all equally urgent. Your eyes should arrive before your hand does.";
            case CIPHER -> "On a cipher, solve one ring against a fixed reference. Looking at the whole seal at once is how it wins.";
            case PRESSURE -> "Pressure wards are about release, not force. Any animal can push until something breaks.";
            case RESONANCE -> "Match the shape of the resonance, not its apparent loudness. The ward cares about proportion.";
            case PULSE -> "In a pulse lane, watch the strike line. The incoming marks are merely threats until they reach it.";
            case BALANCE -> "A balance ward rewards small corrections. Large ones are confessions of having stopped paying attention.";
            case CONSTELLATION -> "Constellations are easier if you anchor the longest segment first. The little errors then have fewer places to hide.";
            case MIRROR -> "Mirror seals test orientation, not beauty. Ignore the symbol's meaning and compare its asymmetry.";
            case KEYWAY -> "Overset pins are greed made mechanical. Once a pin seats, stop lifting it.";
            case AUGURY -> "Augury is deduction. Remove what cannot be true before you spend a guess on what might be.";
            case GYRE -> "In the gyre, watch the next node rather than admiring the needle. The needle is already doing its job.";
            case VESSEL -> "The vessel charges you for information. Look only when the cost of ignorance exceeds the cost of seeing.";
            case YOKE -> "With the yoke, correct one variable before compensating the other. Fighting both at once is how it couples against you.";
            case ROOTWAY -> "Trace dead ends backward in the Rootway. Growth is noisy; impossibility is precise.";
            case LATTICE -> "Read the lattice from the edges inward. Boundary constraints tell fewer lies.";
            case PARALLAX -> "Choose one fragment as your anchor in Parallax. Four moving truths are harder than one fixed lie.";
            case RUNEBRAID -> "A braid is local disorder pretending to be global. Fix adjacent mistakes and the larger pattern follows.";
            case SHARDSONG -> "Shard edges are free information. Match the outside before arguing with the center.";
            case VEILSTEP -> "In Veilstep, count from the stone under you, not the place you wish to reach. Hope is not a coordinate.";
            case BLACK_MEASURE -> "The Black Measure has no mystery. Capacity is not quantity; track what can actually be conserved.";
            case EPITAPH -> "The Epitaph is grammar, not memory. Compare how one complete line becomes the next; the missing runes obey the same transformation.";
            case ORRERY -> "The Black Orrery is coupled motion. Do not correct the ring that looks wrong first; ask which turn changes the most wrong rings in the right direction.";
            case PROCESSION -> "The Last Procession has no hidden answer. Cross out every order contradicted by the testimony until only one procession can still breathe.";
            case CTHULHUS_GAME -> "Do not memorize an order. It has no loyalty to one. Read each passage as a different predator.";
        };
    }

    /** Called for portal/mod dimension changes. Held Savants always react; inventory-only ones do so rarely. */
    public static void speakDimensionChange(ServerPlayer sp, ResourceKey<Level> destination) {
        if (isHoldingSavant(sp)) {
            speakHeld(sp, travelLine(sp, destination));
        } else if (hasSavant(sp) && sp.getRandom().nextFloat() < 0.15f) {
            speakOwned(sp, travelLine(sp, destination));
        }
    }

    private static void speakTravel(ServerPlayer sp, ResourceKey<Level> destination) {
        speakHeld(sp, travelLine(sp, destination));
    }

    private static String travelLine(ServerPlayer sp, ResourceKey<Level> destination) {
        String[] specific = destination == Level.OVERWORLD ? SAVANT_OVERWORLD
                : destination == Level.NETHER ? SAVANT_NETHER
                : destination == Level.END ? SAVANT_END : SAVANT_GENERAL;
        // Crossings alternate between destination-specific contempt and the broader personality pool.
        return sp.getRandom().nextFloat() < 0.58f
                ? specific[sp.getRandom().nextInt(specific.length)]
                : SAVANT_GENERAL[sp.getRandom().nextInt(SAVANT_GENERAL.length)];
    }

    /**
     * Somewhere in the target world with air to stand in.
     *
     * <p>The heightmap is right for the overworld and the end and useless in the
     * nether, where it returns the bedrock roof. So the nether gets a scan
     * downward from just under the roof for the first gap wide enough to stand
     * in, and if there genuinely is not one the player is placed high and left
     * to deal with it, which is preferable to being placed inside stone.
     */
    private static BlockPos safeLanding(ServerLevel target, int x, int z) {
        // First preserve the player's approximate x/z. The search is deliberately
        // local so the Eye still behaves like dimensional travel rather than a
        // disguised spawn command.
        BlockPos found = findNearbyLanding(target, x, z, target.dimension() == Level.NETHER ? 18 : 28);
        if (found != null) return found;

        if (target.dimension() == Level.END) {
            // A requested End coordinate may be pure void. Fall back toward the
            // central island, then the vanilla obsidian-platform neighbourhood.
            found = findNearbyLanding(target, 0, 0, 96);
            if (found != null) return found;
            found = findNearbyLanding(target, 100, 0, 48);
            if (found != null) return found;
        }

        // Last resort: search around the dimension's shared spawn. Never return a
        // blind y=70/y=90 coordinate: those can be void, lava or solid rock.
        BlockPos spawn = target.getSharedSpawnPos();
        found = findNearbyLanding(target, spawn.getX(), spawn.getZ(), 64);
        if (found != null) return found;

        // This path is extraordinarily unlikely in vanilla dimensions. Use the
        // highest known non-fluid surface if possible; otherwise stay above spawn
        // rather than manufacturing a platform or deleting terrain.
        BlockPos top = target.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(spawn.getX(), 0, spawn.getZ()));
        if (isSafeLanding(target, top)) return top;
        return spawn.above(2);
    }

    @Nullable
    private static BlockPos findNearbyLanding(ServerLevel target, int centerX, int centerZ, int radius) {
        int step = radius > 40 ? 3 : 1;
        BlockPos center = findColumnLanding(target, centerX, centerZ);
        if (center != null) return center;

        for (int r = step; r <= radius; r += step) {
            for (int dx = -r; dx <= r; dx += step) {
                BlockPos a = findColumnLanding(target, centerX + dx, centerZ - r);
                if (a != null) return a;
                BlockPos b = findColumnLanding(target, centerX + dx, centerZ + r);
                if (b != null) return b;
            }
            for (int dz = -r + step; dz <= r - step; dz += step) {
                BlockPos a = findColumnLanding(target, centerX - r, centerZ + dz);
                if (a != null) return a;
                BlockPos b = findColumnLanding(target, centerX + r, centerZ + dz);
                if (b != null) return b;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos findColumnLanding(ServerLevel target, int x, int z) {
        if (target.dimension() != Level.NETHER) {
            BlockPos top = target.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z));
            return isSafeLanding(target, top) ? top : null;
        }

        int maxY = Math.min(target.getMaxBuildHeight() - 3, 122);
        int minY = target.getMinBuildHeight() + 2;
        for (int y = maxY; y >= minY; y--) {
            BlockPos foot = new BlockPos(x, y, z);
            if (isSafeLanding(target, foot)) return foot;
        }
        return null;
    }

    private static boolean isSafeLanding(ServerLevel target, BlockPos foot) {
        if (foot.getY() <= target.getMinBuildHeight() + 1
                || foot.getY() >= target.getMaxBuildHeight() - 1) return false;

        BlockPos head = foot.above();
        BlockPos floor = foot.below();
        if (!target.getFluidState(foot).isEmpty()
                || !target.getFluidState(head).isEmpty()
                || !target.getFluidState(floor).isEmpty()) return false;

        boolean feetClear = target.getBlockState(foot).getCollisionShape(target, foot).isEmpty();
        boolean headClear = target.getBlockState(head).getCollisionShape(target, head).isEmpty();
        boolean floorSolid = !target.getBlockState(floor).getCollisionShape(target, floor).isEmpty();
        return feetClear && headClear && floorSolid;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wardbound.the_savant.desc")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("wardbound.tooltip.eye_use")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("wardbound.tooltip.uses",
                        stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("wardbound.tooltip.eye_cooldown",
                        WardConfig.eyeCooldownTicks / 20)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Nothing flat left for a sheen to sit on. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
