package dev.marrowseal.wardbound.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.MinigameType;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;
import dev.marrowseal.wardbound.net.OpenBargainPacket;
import dev.marrowseal.wardbound.net.OpenGamblerChoicePacket;
import dev.marrowseal.wardbound.net.OpenAceChoicePacket;
import dev.marrowseal.wardbound.net.OpenDealersHandPacket;

@OnlyIn(Dist.CLIENT)
public final class ClientHooks {

    private ClientHooks() {
    }

    public static void openScreen(OpenMinigamePacket msg) {
        Minecraft mc = Minecraft.getInstance();
        MinigameType type = MinigameType.byOrdinal(msg.gameId);
        // Routine ward entry should read as a light mechanical/glass cue, not a sub-heavy impact.
        Sfx.play(WardSounds.WARD_OPEN, 0.34f, 1.0f);
        if (mc.player != null) WardLodestoneFx.spawnLocal(mc.player.position().add(0, 1.0, 0), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.WARD_OPEN, 1.0f);
        BaseMinigameScreen screen = switch (type) {
            case DRUM -> new DrumScreen(msg);
            case TIMING -> new TimingScreen(msg);
            case MEMORY -> new MemoryScreen(msg);
            case RUNES -> new RuneScreen(msg);
            case CIPHER -> new CipherScreen(msg);
            case PRESSURE -> new PressureScreen(msg);
            case RESONANCE -> new ResonanceScreen(msg);
            case PULSE -> new PulseScreen(msg);
            case BALANCE -> new BalanceScreen(msg);
            case CONSTELLATION -> new ConstellationScreen(msg);
            case MIRROR -> new MirrorScreen(msg);
            case KEYWAY -> new KeywayScreen(msg);
            case AUGURY -> new AuguryScreen(msg);
            case GYRE -> new GyreScreen(msg);
            case VESSEL -> new VesselScreen(msg);
            case YOKE -> new YokeScreen(msg);
            case ROOTWAY -> new RootwayScreen(msg);
            case LATTICE -> new LatticeScreen(msg);
            case PARALLAX -> new ParallaxScreen(msg);
            case RUNEBRAID -> new RunebraidScreen(msg);
            case SHARDSONG -> new ShardsongScreen(msg);
            case VEILSTEP -> new VeilstepScreen(msg);
            case BLACK_MEASURE -> new BlackMeasureScreen(msg);
            case EPITAPH -> new EpitaphScreen(msg);
            case ORRERY -> new OrreryScreen(msg);
            case PROCESSION -> new ProcessionScreen(msg);
            case CTHULHUS_GAME -> new CthulhusGameScreen(msg);
        };
        mc.setScreen(screen);
    }


    public static void openBargain(OpenBargainPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) WardLodestoneFx.spawnLocal(mc.player.position().add(0, 1.0, 0), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.BARGAIN_MASTER, 0.85f);
        mc.setScreen(new BargainScreen(msg));
    }

    public static void openAceChoice(OpenAceChoicePacket msg) {
        Minecraft.getInstance().setScreen(new AceChoiceScreen(msg));
    }

    public static void openDealersHand(OpenDealersHandPacket msg) {
        Minecraft.getInstance().setScreen(new DealersHandScreen(msg));
    }

    public static void openGamblerChoice(OpenGamblerChoicePacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GamblerChoiceScreen current && current.matches(msg)) return;
        mc.setScreen(new GamblerChoiceScreen(msg));
    }
}
