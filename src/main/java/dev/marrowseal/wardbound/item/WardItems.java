package dev.marrowseal.wardbound.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.nhalsul.NhalSulModule;

/**
 * Wardbound's hand-used keys, relics, shards and utility items. Chest aids are
 * bound only from the InteractionHand that actually clicked the container; the
 * rest of the inventory is never searched for a stronger substitute.
 */
public final class WardItems {

    public static final DeferredRegister<Item> REGISTRY =
            DeferredRegister.create(ForgeRegistries.ITEMS, Wardbound.MODID);
    public static final RegistryObject<Item> ARCHIVE_WRIT=REGISTRY.register("archive_writ",()->new dev.marrowseal.wardbound.boss.ArchiveWritItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> LAST_MARGIN=REGISTRY.register("last_margin",()->new dev.marrowseal.wardbound.boss.LastMarginItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> FINAL_WITNESS_BELL=REGISTRY.register("final_witness_bell",()->new dev.marrowseal.wardbound.boss.FinalWitnessBellItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    /** Fourth movement revealed only after Gambler, Curator and Notary are all defeated. */
    public static final RegistryObject<Item> SCORE_BEYOND_THE_MARGIN=REGISTRY.register("score_beyond_the_margin",()->new dev.marrowseal.wardbound.boss.ScoreBeyondMarginItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    /** Maestro's final notation; usable only from the End to reach the severed Head encounter. */
    public static final RegistryObject<Item> CANTICLE_FOR_THE_SLEEPER=REGISTRY.register("canticle_for_the_sleeper",()->new dev.marrowseal.wardbound.boss.CanticleForSleeperItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    /** Head-only pre-final reward; future altar key to pair with the Cthulhu Idol. */
    public static final RegistryObject<Item> ABYSSAL_VESTIGE=REGISTRY.register("abyssal_vestige",()->new dev.marrowseal.wardbound.boss.AbyssalVestigeItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    /**
     * Creative tabs are a vanilla registry in 1.20.1, not a Forge one, so this
     * takes the key from {@code net.minecraft.core.registries.Registries}
     * rather than from {@code ForgeRegistries.Keys}, which has no entry for it.
     */
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Wardbound.MODID);

    /**
     * Plain old iron, forged by somebody who was not thinking about how it
     * looked. It does not fit this lock, or any lock, but a key in your hand
     * while you work one is worth a mistake.
     */
    public static final RegistryObject<Item> WARD_KEY = REGISTRY.register("ward_key",
            () -> new CharmItem(new Item.Properties()
                    .stacksTo(1)
                    .durability(Charm.KEY_DURABILITY)
                    .rarity(Rarity.COMMON),
                    Charm.WARD_KEY));

    /**
     * Still warm. It is not from anything that had a name, and the veins in it
     * do not run the way veins run. It answers a lock the way a key does, which
     * is the part nobody wants explained.
     */
    public static final RegistryObject<Item> ICHORHEART = REGISTRY.register("ichorheart",
            () -> new IchorheartItem(new Item.Properties()
                    .stacksTo(1)
                    .durability(Charm.HEART_DURABILITY)
                    .rarity(Rarity.RARE)
                    .fireResistant()));

    /**
     * A sprung strip of cold iron with a hook worked into the tip, found in
     * walls and under floors more often than in toolboxes. Slid into a ward
     * before you start, it takes the first stage of the mechanism out of play.
     *
     * <p>Not craftable. Nobody alive makes these, which is most of the point.
     */
    public static final RegistryObject<Item> COLD_SHIM = REGISTRY.register("cold_shim",
            () -> new RelicItem(new Item.Properties()
                    .stacksTo(1)
                    .durability(3)
                    .rarity(Rarity.UNCOMMON),
                    RelicItem.Kind.SHIM));

    /**
     * A cyclopean viewing lens of black stone, verdigrised brass and glass that
     * reflects architecture not present in the room. Carried, it reads a ward
     * before commitment: mechanism and quirks are revealed without changing the
     * ward itself.
     */
    public static final RegistryObject<Item> CYCLOPEAN_LENS = REGISTRY.register("cyclopean_lens",
            () -> new RelicItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE),
                    RelicItem.Kind.LENS));

    /**
     * Only out of the End after late progression, and only from a chest that
     * reaches the current high-reward band. Progression and multiplier gates are
     * both required so the Savant stays rare without depending on obsolete x5 loot.
     */
    public static final RegistryObject<Item> THE_SAVANT = REGISTRY.register("the_savant",
            () -> new CthulhuEyeItem(new Item.Properties()
                    .stacksTo(1)
                    .durability(5)
                    .rarity(Rarity.EPIC)
                    .fireResistant()));

    /**
     * Taken from the end of Cthulhu's Game. Worn if Curios is present, or used
     * directly from the hand: it arrests everything in front of you for four seconds.
     */
    public static final RegistryObject<Item> CTHULHU_IDOL = REGISTRY.register("cthulhu_idol",
            () -> new CthulhuIdolItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .fireResistant()));

    /**
     * Taken off the last seal of a chain, and named after the house that cast
     * it. Not craftable and not findable any other way: it is the payoff for
     * following a trail across several hundred blocks, and it should mean that.
     */
    public static final RegistryObject<Item> SEALWRIGHTS_KEY = REGISTRY.register("sealwrights_key",
            () -> new SealwrightKeyItem(new Item.Properties()
                    .stacksTo(1)
                    .durability(Charm.WRIGHT_DURABILITY)
                    .rarity(Rarity.EPIC)
                    .fireResistant()));

    /**
     * A coal that has not gone out. It enters the Nether reward pool after the
     * player has reached the mid progression band and cleared a sufficiently
     * rich ward. It holds the ward open; time is the one thing every lock lacks.
     */
    public static final RegistryObject<Item> SLOW_EMBER = REGISTRY.register("slow_ember",
            () -> new RelicItem(new Item.Properties()
                    .stacksTo(1)
                    .durability(3)
                    .rarity(Rarity.RARE)
                    .fireResistant(),
                    RelicItem.Kind.EMBER));


    /** Uncommon attunement shard: right-click to prime the next ward for +50% loot. */
    public static final RegistryObject<Item> WARD_SPLINTER = REGISTRY.register("ward_splinter",
            () -> new RelicDropItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                    RelicDropItem.Kind.SPLINTER));

    /** Epic attunement shard: right-click to prime the next warded chest to duplicate its final contents. */
    public static final RegistryObject<Item> ELDRITCH_SHARD = REGISTRY.register("eldritch_shard",
            () -> new RelicDropItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(),
                    RelicDropItem.Kind.ELDRITCH_SHARD));

    /** Rare memory shard: forces the next ward away from recent patterns and resonates with familiar makers. */
    public static final RegistryObject<Item> ECHO_SHARD = REGISTRY.register("echo_shard",
            () -> new RelicDropItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    RelicDropItem.Kind.ECHO_SHARD));

    /** Rare combat shard: counts hostile kills for four minutes and pays back every fourth. */
    public static final RegistryObject<Item> BLOODGLASS_SHARD = REGISTRY.register("bloodglass_shard",
            () -> new RelicDropItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant(),
                    RelicDropItem.Kind.BLOODGLASS_SHARD));

    /** Cheap field ledger that lets the player review Wardbound loot, voices and encounters. */
    public static final RegistryObject<Item> WITNESS_LEDGER = REGISTRY.register("witness_ledger",
            () -> new WitnessLedgerItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    /** Physical testimony fragments connected to Silas Veyr and the Masters' unresolved record. */
    public static final RegistryObject<Item> SILAS_FRAGMENT = REGISTRY.register("silas_fragment",
            () -> new SilasFragmentItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    /** Twelve Silas fragments assembled into one record, meant to be returned rather than used. */
    public static final RegistryObject<Item> UNCONCLUDED_TESTIMONY = REGISTRY.register("unconcluded_testimony",
            () -> new UnconcludedTestimonyItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    /** A plain ring from Nhal-Sûl's scabbard. Silas gives it only after accepting the complete testimony. */
    public static final RegistryObject<Item> WORN_SCABBARD_RING = REGISTRY.register("worn_scabbard_ring",
            () -> new WornScabbardRingItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .fireResistant()));

    /** Rare field-card token dropped by hostile mobs. Open it later, never mid-fight. */
    public static final RegistryObject<Item> SEALED_CARD = REGISTRY.register("sealed_card",
            () -> new SealedCardItem(new Item.Properties()
                    .stacksTo(16)
                    .rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> GAMBLER_INVITATION = REGISTRY.register("gambler_invitation",
            () -> new dev.marrowseal.wardbound.boss.GamblerInvitationItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> HOUSEBREAKERS_ACE = REGISTRY.register("housebreakers_ace",
            () -> new dev.marrowseal.wardbound.boss.HousebreakersAceItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> DEALERS_HAND = REGISTRY.register("dealers_hand",
            () -> new dev.marrowseal.wardbound.boss.DealersHandItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> UNSIGNED_VERDICT = REGISTRY.register("unsigned_verdict",
            () -> new dev.marrowseal.wardbound.boss.UnsignedVerdictItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("charms",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.wardbound.charms"))
                    .icon(() -> WARD_KEY.get().getDefaultInstance())
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .displayItems((params, output) -> {
                        output.accept(WARD_KEY.get());
                        output.accept(ICHORHEART.get());
                        output.accept(COLD_SHIM.get());
                        output.accept(CYCLOPEAN_LENS.get());
                        output.accept(THE_SAVANT.get());
                        output.accept(CTHULHU_IDOL.get());
                        output.accept(SEALWRIGHTS_KEY.get());
                        output.accept(SLOW_EMBER.get());
                        output.accept(WARD_SPLINTER.get());
                        output.accept(ELDRITCH_SHARD.get());
                        output.accept(ECHO_SHARD.get());
                        output.accept(BLOODGLASS_SHARD.get());
                        output.accept(WITNESS_LEDGER.get());
                        output.accept(SILAS_FRAGMENT.get());
                        output.accept(UNCONCLUDED_TESTIMONY.get());
                        output.accept(WORN_SCABBARD_RING.get());
                        output.accept(NhalSulModule.NHAL_SUL.get());
                        output.accept(SEALED_CARD.get());
                        output.accept(GAMBLER_INVITATION.get());
                        output.accept(HOUSEBREAKERS_ACE.get());
                        output.accept(DEALERS_HAND.get());
                        output.accept(ARCHIVE_WRIT.get());
                        output.accept(LAST_MARGIN.get());
                        output.accept(FINAL_WITNESS_BELL.get());
                        output.accept(UNSIGNED_VERDICT.get());
                        output.accept(SCORE_BEYOND_THE_MARGIN.get());
                        output.accept(CANTICLE_FOR_THE_SLEEPER.get());
                        output.accept(ABYSSAL_VESTIGE.get());
                    })
                    .build());

    private WardItems() {
    }
}
