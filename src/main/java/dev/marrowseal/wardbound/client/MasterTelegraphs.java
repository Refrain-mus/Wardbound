package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.net.MasterCuesPacket;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/**
 * Client telegraph bridge. The old RenderType.lines overlay has deliberately been removed:
 * geometry now arrives as server-owned cues and is painted by Lodestone in GamblerCinematicFx.
 */
public final class MasterTelegraphs {
    private static final Map<Integer, Long> LAST_LODESTONE_PULSE = new HashMap<>();
    private static Object levelIdentity;

    private MasterTelegraphs() {}

    public static void show(MasterCuesPacket packet) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level!=levelIdentity){LAST_LODESTONE_PULSE.clear();levelIdentity=mc.level;}
        if(mc.level==null)return;
        long now=System.nanoTime();
        long cadence=WardConfig.accessibilityReduceMotion?150_000_000L:75_000_000L;
        long last=LAST_LODESTONE_PULSE.getOrDefault(packet.entityId(),Long.MIN_VALUE);
        // Do not subtract the sentinel: nanoTime - Long.MIN_VALUE can overflow and suppress a first pulse.
        if(last!=Long.MIN_VALUE && now>=last && now-last<cadence)return;
        LAST_LODESTONE_PULSE.put(packet.entityId(),now);
        GamblerTextureFx.telegraph(packet);
        GamblerCinematicFx.telegraphPulse(packet);
    }
}
