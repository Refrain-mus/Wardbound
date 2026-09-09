package dev.marrowseal.wardbound;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import dev.marrowseal.wardbound.client.WardConfigScreen;
import dev.marrowseal.wardbound.item.WardItems;
import dev.marrowseal.wardbound.loot.WardLootModifier;
import dev.marrowseal.wardbound.net.MinigameResultPacket;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;
import dev.marrowseal.wardbound.net.EyeTravelPacket;
import dev.marrowseal.wardbound.net.SaveProgressPacket;
import dev.marrowseal.wardbound.net.OpenBargainPacket;
import dev.marrowseal.wardbound.net.BargainChoicePacket;
import dev.marrowseal.wardbound.net.CthulhuFieldPacket;
import dev.marrowseal.wardbound.net.UseCurioIdolPacket;
import dev.marrowseal.wardbound.net.SavantLinePacket;
import dev.marrowseal.wardbound.net.WardHudPacket;
import dev.marrowseal.wardbound.net.OpenWitnessLedgerPacket;
import dev.marrowseal.wardbound.net.UseWitnessLedgerPacket;
import dev.marrowseal.wardbound.net.CloseWitnessLedgerPacket;
import dev.marrowseal.wardbound.net.OpenGamblerChoicePacket;
import dev.marrowseal.wardbound.net.GamblerChoicePacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Wardbound.MODID)
public class Wardbound {

    public static final String MODID = "wardbound";
    public static final Logger LOG = LogManager.getLogger("Wardbound");

    /**
     * Bumped whenever a packet's wire format changes. Version negotiation is the
     * only thing standing between a mismatched client and a decode that reads
     * whatever happens to be next in the buffer, so this has to move every time
     * a field is added, removed or reordered.
     *
     * <p>2: OpenMinigamePacket carries the bound charm.
     * <p>3: SaveProgressPacket carries the seed of the lock it describes.
     * <p>4: OpenMinigamePacket carries the maker familiarity.
     * <p>5: OpenMinigamePacket carries the unsigned flag.
     * <p>6: EyeTravelPacket added.
     * <p>7: OpenMinigamePacket carries the bought seconds.
     * <p>8: post-ward bargain card packets added.
     * <p>9: OpenMinigamePacket carries per-game tuning from the server config.
     * <p>10: OpenMinigamePacket carries persistent ward-history presentation data.
     * <p>11: MinigameResultPacket carries clean-win timing and optional balance telemetry.
     * <p>12: OpenMinigamePacket carries Eldritch Ward stage metadata.
     * <p>13: packet also carries possessed/rivalry/mutation presentation state.
     * <p>15: server can broadcast the temporary Cthulhu Idol freeze field effect.
     * <p>16: dedicated Idol key packet removed; Idol activation is hand-use only.
     * <p>17: dedicated Idol key packet restored without exposing it in the item tooltip.
     * <p>23: complete minigame resume snapshots and seed-bound result validation.
     * <p>24: Savant speech moved to a dedicated client HUD packet.
     * <p>25: transient Wardbound event messages moved to the dedicated HUD packet.
     * <p>26: witness ledger packet added for reviewing loot and encounter history.
     * <p>27: witness ledger packet now carries timestamps, kinds and the last-ward summary.
     * <p>28: bargain packet carries special-deck mode and forced-choice state.
     * <p>29: Witness Ledger packet carries Grimoire discoveries.
     * <p>30: dedicated Witness Ledger key packet and revised journal UI payloads.
     * <p>31: bargain presenter labels for field-card masters.
     * <p>32: Witness Grimoire live status schematic payload.
     * <p>34: bargain packets carry Palimpsest revision level 3.
     * <p>35: Witness Ledger close packet added for synced GeckoLib close animation.
     * <p>36: minigame packets carry mastery, curated corruption slot and final-exam discipline anchors.
     * <p>38: minigame packets carry adaptive deception, hybrid micro-round and skill-profile resume state.
     * <p>39: Witness Ledger list framing consumes the exact serialized entry count.
     * <p>46: Pale Gambler forced-choice attack packets added.
     * <p>47: Housebreaker's Ace two-card choice packets added.
     * <p>48: Ace packet carries permanent-law signature state.
     * <p>49: Pale Gambler cinematic VFX cue packet added (legacy player-body animation dependency removed before the 1.0 release).
     * <p>50: Gambler cinematic cue set expanded with intro/charge/marked/brace/victory/release events.
     * <p>52: Gambler forced hand now carries two card IDs drawn from a three-card encounter pool.
     * <p>53: Silas Veyr dialogue packet and bespoke lore screen added.
     * <p>54: Silas physical testimony fragments and fragment reader packet added.
     * <p>55: Ninth Margin virtual hotbar spell cast intent packet added.
     * <p>56: Dealer's Hand reward draw/choice packets added.
     * <p>57: Cthulhu Head arena combat adds new Lodestone attack cue kinds.
     */
    private static final String PROTOCOL = "57";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    public Wardbound() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerSerializers);

        WardItems.REGISTRY.register(modBus);
        dev.marrowseal.wardbound.nhalsul.NhalSulModule.register(modBus);
        dev.marrowseal.wardbound.ancientsmith.AncientSmithModule.register(modBus);
        dev.marrowseal.wardbound.boss.WardBosses.TYPES.register(modBus);
        WardEffects.REGISTRY.register(modBus);
        WardItems.TABS.register(modBus);
        dev.marrowseal.wardbound.boss.CuratorSounds.init();
        dev.marrowseal.wardbound.champion.ChampionSounds.init();
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                dev.marrowseal.wardbound.champion.ChampionConfig.SPEC,"wardbound-silas.toml");
        WardSounds.REGISTRY.register(modBus);

        MinecraftForge.EVENT_BUS.register(ChestValuator.class);
        MinecraftForge.EVENT_BUS.addListener(WardEffects::onPlayerAttack);
        MinecraftForge.EVENT_BUS.addListener(WardEffects::onLivingDeath);

        WardConfig.load();

        // Adds the "Config" button next to Wardbound in the Mods list (Mods menu -> Wardbound -> Config).
        // The lambda's body is only ever executed on the client, so this is safe on a dedicated
        // server even though WardConfigScreen is a client-only class.
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parentScreen) -> new WardConfigScreen(parentScreen)));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        int id = 0;
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.MasterCuesPacket.class, dev.marrowseal.wardbound.net.MasterCuesPacket::encode, dev.marrowseal.wardbound.net.MasterCuesPacket::decode, dev.marrowseal.wardbound.net.MasterCuesPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.MasterLinePacket.class,
                dev.marrowseal.wardbound.net.MasterLinePacket::encode, dev.marrowseal.wardbound.net.MasterLinePacket::decode, dev.marrowseal.wardbound.net.MasterLinePacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, OpenMinigamePacket.class,
                OpenMinigamePacket::encode, OpenMinigamePacket::decode, OpenMinigamePacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, MinigameResultPacket.class,
                MinigameResultPacket::encode, MinigameResultPacket::decode, MinigameResultPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, EyeTravelPacket.class,
                EyeTravelPacket::encode, EyeTravelPacket::decode, EyeTravelPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, SaveProgressPacket.class,
                SaveProgressPacket::encode, SaveProgressPacket::decode, SaveProgressPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, OpenBargainPacket.class,
                OpenBargainPacket::encode, OpenBargainPacket::decode, OpenBargainPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, BargainChoicePacket.class,
                BargainChoicePacket::encode, BargainChoicePacket::decode, BargainChoicePacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, CthulhuFieldPacket.class,
                CthulhuFieldPacket::encode, CthulhuFieldPacket::decode, CthulhuFieldPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.WardLodestoneFxPacket.class,
                dev.marrowseal.wardbound.net.WardLodestoneFxPacket::encode, dev.marrowseal.wardbound.net.WardLodestoneFxPacket::decode, dev.marrowseal.wardbound.net.WardLodestoneFxPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, UseCurioIdolPacket.class,
                UseCurioIdolPacket::encode, UseCurioIdolPacket::decode, UseCurioIdolPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, SavantLinePacket.class,
                SavantLinePacket::encode, SavantLinePacket::decode, SavantLinePacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, WardHudPacket.class,
                WardHudPacket::encode, WardHudPacket::decode, WardHudPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, OpenWitnessLedgerPacket.class,
                OpenWitnessLedgerPacket::encode, OpenWitnessLedgerPacket::decode, OpenWitnessLedgerPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, UseWitnessLedgerPacket.class,
                UseWitnessLedgerPacket::encode, UseWitnessLedgerPacket::decode, UseWitnessLedgerPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, CloseWitnessLedgerPacket.class,
                CloseWitnessLedgerPacket::encode, CloseWitnessLedgerPacket::decode, CloseWitnessLedgerPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, OpenGamblerChoicePacket.class,
                OpenGamblerChoicePacket::encode, OpenGamblerChoicePacket::decode, OpenGamblerChoicePacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, GamblerChoicePacket.class,
                GamblerChoicePacket::encode, GamblerChoicePacket::decode, GamblerChoicePacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.OpenAceChoicePacket.class,
                dev.marrowseal.wardbound.net.OpenAceChoicePacket::encode, dev.marrowseal.wardbound.net.OpenAceChoicePacket::decode, dev.marrowseal.wardbound.net.OpenAceChoicePacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.AceChoicePacket.class,
                dev.marrowseal.wardbound.net.AceChoicePacket::encode, dev.marrowseal.wardbound.net.AceChoicePacket::decode, dev.marrowseal.wardbound.net.AceChoicePacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.MasterAnimationBeatPacket.class,
                dev.marrowseal.wardbound.net.MasterAnimationBeatPacket::encode, dev.marrowseal.wardbound.net.MasterAnimationBeatPacket::decode, dev.marrowseal.wardbound.net.MasterAnimationBeatPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.MasterDamageReactionPacket.class,
                dev.marrowseal.wardbound.net.MasterDamageReactionPacket::encode, dev.marrowseal.wardbound.net.MasterDamageReactionPacket::decode, dev.marrowseal.wardbound.net.MasterDamageReactionPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.GamblerCinematicPacket.class,
                dev.marrowseal.wardbound.net.GamblerCinematicPacket::encode, dev.marrowseal.wardbound.net.GamblerCinematicPacket::decode, dev.marrowseal.wardbound.net.GamblerCinematicPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.OpenCuratorInvocationPacket.class,
                dev.marrowseal.wardbound.net.OpenCuratorInvocationPacket::encode, dev.marrowseal.wardbound.net.OpenCuratorInvocationPacket::decode, dev.marrowseal.wardbound.net.OpenCuratorInvocationPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.CuratorInvocationActionPacket.class,
                dev.marrowseal.wardbound.net.CuratorInvocationActionPacket::encode, dev.marrowseal.wardbound.net.CuratorInvocationActionPacket::decode, dev.marrowseal.wardbound.net.CuratorInvocationActionPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.OpenNotaryInvocationPacket.class,
                dev.marrowseal.wardbound.net.OpenNotaryInvocationPacket::encode, dev.marrowseal.wardbound.net.OpenNotaryInvocationPacket::decode, dev.marrowseal.wardbound.net.OpenNotaryInvocationPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.NotaryInvocationActionPacket.class,
                dev.marrowseal.wardbound.net.NotaryInvocationActionPacket::encode, dev.marrowseal.wardbound.net.NotaryInvocationActionPacket::decode, dev.marrowseal.wardbound.net.NotaryInvocationActionPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.NotaryRitualPacket.class,
                dev.marrowseal.wardbound.net.NotaryRitualPacket::encode, dev.marrowseal.wardbound.net.NotaryRitualPacket::decode, dev.marrowseal.wardbound.net.NotaryRitualPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.OpenSilasDialoguePacket.class,
                dev.marrowseal.wardbound.net.OpenSilasDialoguePacket::encode, dev.marrowseal.wardbound.net.OpenSilasDialoguePacket::decode, dev.marrowseal.wardbound.net.OpenSilasDialoguePacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.OpenSilasFragmentPacket.class,
                dev.marrowseal.wardbound.net.OpenSilasFragmentPacket::encode, dev.marrowseal.wardbound.net.OpenSilasFragmentPacket::decode, dev.marrowseal.wardbound.net.OpenSilasFragmentPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.CastNinthMarginPacket.class,
                dev.marrowseal.wardbound.net.CastNinthMarginPacket::encode, dev.marrowseal.wardbound.net.CastNinthMarginPacket::decode, dev.marrowseal.wardbound.net.CastNinthMarginPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.OpenDealersHandPacket.class,
                dev.marrowseal.wardbound.net.OpenDealersHandPacket::encode, dev.marrowseal.wardbound.net.OpenDealersHandPacket::decode, dev.marrowseal.wardbound.net.OpenDealersHandPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, dev.marrowseal.wardbound.net.DealersHandChoicePacket.class,
                dev.marrowseal.wardbound.net.DealersHandChoicePacket::encode, dev.marrowseal.wardbound.net.DealersHandChoicePacket::decode, dev.marrowseal.wardbound.net.DealersHandChoicePacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
    }

    private void registerSerializers(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                new ResourceLocation(MODID, "dim_multiplier"),
                () -> WardLootModifier.CODEC);
    }
}
