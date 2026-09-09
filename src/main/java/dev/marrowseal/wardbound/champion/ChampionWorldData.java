package dev.marrowseal.wardbound.champion;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/** Stored once in Overworld data storage, shared by every dimension and every player. */
public final class ChampionWorldData extends SavedData {
    UUID person;
    long incarnation = 0, arrival = 0, lastSeen = 0, nextCheck = 0;
    int eligibleChecks = 0;
    /** Current physical location, including temporary companion calls. */
    BlockPos position = BlockPos.ZERO;
    /** Stable road/camp location. Companion travel never overwrites this. */
    BlockPos roadPosition = BlockPos.ZERO;
    CompoundTag snapshot = new CompoundTag();

    public static ChampionWorldData get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(ChampionWorldData::load, ChampionWorldData::new, "wardbound_silas");
    }

    public static ChampionWorldData load(CompoundTag t) {
        var d = new ChampionWorldData();
        d.person = t.hasUUID("person") ? t.getUUID("person") : null;
        d.incarnation = t.getLong("incarnation");
        d.arrival = t.getLong("arrival");
        d.lastSeen = t.getLong("lastSeen");
        d.nextCheck = t.getLong("nextCheck");
        d.eligibleChecks = t.getInt("checks");
        d.position = BlockPos.of(t.getLong("position"));
        d.roadPosition = t.contains("roadPosition") ? BlockPos.of(t.getLong("roadPosition")) : d.position;
        d.snapshot = t.getCompound("snapshot").copy();
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        if (person != null) t.putUUID("person", person);
        t.putLong("incarnation", incarnation);
        t.putLong("arrival", arrival);
        t.putLong("lastSeen", lastSeen);
        t.putLong("nextCheck", nextCheck);
        t.putInt("checks", eligibleChecks);
        t.putLong("position", position.asLong());
        t.putLong("roadPosition", roadPosition.asLong());
        t.put("snapshot", snapshot.copy());
        return t;
    }

    boolean accepts(DefeatedChampionEntity e) {
        return ChampionIdentity.accepts(person, incarnation, false, e.getUUID(), e.incarnation());
    }

    void remember(DefeatedChampionEntity e) {
        if (!accepts(e)) return;
        position = e.blockPosition();
        if (!e.companionActive()) roadPosition = e.blockPosition();
        snapshot = new CompoundTag();
        e.saveWithoutId(snapshot);
        setDirty();
    }
}
