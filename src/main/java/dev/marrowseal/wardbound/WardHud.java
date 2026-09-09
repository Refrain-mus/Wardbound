package dev.marrowseal.wardbound;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import dev.marrowseal.wardbound.net.WardHudPacket;

import java.util.Locale;

/** Routes short Wardbound event feedback to the dedicated HUD instead of chat/action bar. */
public final class WardHud {

    public enum Mood {
        WARD,
        GLITCH,
        DANGER,
        REWARD,
        SHARD
    }

    private WardHud() {}

    public static void message(Player player, Component component, boolean ignoredActionBar) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        send(serverPlayer, component == null ? "" : component.getString(), infer(component));
    }

    public static void send(ServerPlayer player, String text, Mood mood) {
        send(player, text, mood, WardHudPacket.DEFAULT_DURATION_MS);
    }

    public static void send(ServerPlayer player, String text, Mood mood, int durationMs) {
        if (player == null || text == null || text.isBlank()) return;
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new WardHudPacket(text, mood.ordinal(), durationMs));
        WardHistory.recordHud(player, text, mood);
    }

    private static Mood infer(Component component) {
        String text = component == null ? "" : component.getString().toLowerCase(Locale.ROOT);
        if (containsAny(text, "shard", "splinter", "ward-sign", "ward sign", "shadow", "+50%", "repeat")) {
            return Mood.SHARD;
        }
        // Resolution text such as "Seal broken. Loot x1.50" is a success, not a
        // danger message. Reward classification has to win before the generic
        // word "broken" is considered.
        if (containsAny(text, "reward", "loot", "earned", "favour", "perfect", "yield", "sends more", "kept",
                "half again", "multiplier", "defeated. loot", "broken. loot")) {
            return Mood.REWARD;
        }
        if (containsAny(text, "detonated", "broken", "never be opened", "guardian", "lost", "life", "wither",
                "snapped", "trap", "dead", "closed again", "still shut", "shut")) {
            return Mood.DANGER;
        }
        if (containsAny(text, "eldritch", "eye", "attention", "watch", "mark", "unsigned", "possessed", "living",
                "glitch", "closer than it was", "trail goes cold", "made this one for you")) {
            return Mood.GLITCH;
        }
        return Mood.WARD;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }
}
