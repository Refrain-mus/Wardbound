package dev.marrowseal.wardbound.net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import dev.marrowseal.wardbound.client.WitnessLedgerScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenWitnessLedgerPacket {
    /** Hard wire bound. The decoder must consume exactly the count written by the encoder. */
    private static final int MAX_LIST_ENTRIES = 1024;
    public final List<String> lootLines;
    public final List<String> recordLines;
    public final String lastWard;
    public final List<String> grimoireLines;
    public final List<String> statLines;
    public final List<String> ruleLines;

    public OpenWitnessLedgerPacket(List<String> lootLines, List<String> recordLines, String lastWard,
                                   List<String> grimoireLines, List<String> statLines, List<String> ruleLines) {
        this.lootLines = lootLines == null ? List.of() : List.copyOf(lootLines);
        this.recordLines = recordLines == null ? List.of() : List.copyOf(recordLines);
        this.lastWard = lastWard == null ? "" : lastWard;
        this.grimoireLines = grimoireLines == null ? List.of() : List.copyOf(grimoireLines);
        this.statLines = statLines == null ? List.of() : List.copyOf(statLines);
        this.ruleLines = ruleLines == null ? List.of() : List.copyOf(ruleLines);
    }

    public static void encode(OpenWitnessLedgerPacket msg, FriendlyByteBuf buf) {
        writeList(buf, msg.lootLines);
        writeList(buf, msg.recordLines);
        buf.writeUtf(msg.lastWard, 768);
        writeList(buf, msg.grimoireLines);
        writeList(buf, msg.statLines);
        writeList(buf, msg.ruleLines);
    }

    public static OpenWitnessLedgerPacket decode(FriendlyByteBuf buf) {
        return new OpenWitnessLedgerPacket(readList(buf), readList(buf), buf.readUtf(768), readList(buf), readList(buf), readList(buf));
    }

    public static void handle(OpenWitnessLedgerPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof WitnessLedgerScreen existing) {
                existing.refreshFromServer(msg.lootLines, msg.recordLines, msg.lastWard, msg.grimoireLines, msg.statLines, msg.ruleLines);
            } else {
                mc.setScreen(new WitnessLedgerScreen(msg.lootLines, msg.recordLines, msg.lastWard, msg.grimoireLines, msg.statLines, msg.ruleLines));
            }
        }));
        ctx.get().setPacketHandled(true);
    }

    private static void writeList(FriendlyByteBuf buf, List<String> lines) {
        // Never advertise more entries than we actually serialize. The old code wrote the
        // full count while the decoder silently clamped it to 256, which left unread strings
        // in the buffer and shifted every following Ledger field.
        int size = Math.min(lines.size(), MAX_LIST_ENTRIES);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            String line = lines.get(i);
            buf.writeUtf(line == null ? "" : line, 1024);
        }
    }

    private static List<String> readList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_LIST_ENTRIES) {
            throw new IllegalArgumentException("Invalid Witness Ledger list size: " + size);
        }
        List<String> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) out.add(buf.readUtf(1024));
        return out;
    }
}
