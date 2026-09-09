package dev.marrowseal.wardbound.ancientsmith;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** One authoritative smith scene per player, persisted in the Overworld data storage. */
public final class SmithSceneData extends SavedData {
    public static final class Session {
        public UUID owner, smith;
        public String dimension, entryDimension;
        public ItemStack sword;
        public Vec3 origin, offer;
        public float yaw, pitch;
        public int tick, slot;
        public boolean recovering, acquisition;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Owner", owner);
            t.putUUID("Smith", smith);
            t.putString("Dimension", dimension);
            t.putString("EntryDimension", entryDimension);
            t.put("Sword", sword.save(new CompoundTag()));
            t.putInt("Tick", tick);
            t.putInt("Slot", slot);
            t.putBoolean("Recovering", recovering);
            t.putBoolean("Acquisition", acquisition);
            vector(t, "Origin", origin);
            vector(t, "Offer", offer);
            t.putFloat("Yaw", yaw);
            t.putFloat("Pitch", pitch);
            return t;
        }

        static Session load(CompoundTag t) {
            if (t == null || !t.hasUUID("Owner") || !t.hasUUID("Smith")) return null;
            Session s = new Session();
            s.owner = t.getUUID("Owner");
            s.smith = t.getUUID("Smith");
            s.dimension = t.getString("Dimension");
            s.entryDimension = t.contains("EntryDimension") ? t.getString("EntryDimension") : s.dimension;
            s.sword = ItemStack.of(t.getCompound("Sword"));
            s.tick = Math.max(0, t.getInt("Tick"));
            s.slot = t.getInt("Slot");
            s.origin = vector(t, "Origin");
            s.offer = vector(t, "Offer");
            s.yaw = Float.isFinite(t.getFloat("Yaw")) ? t.getFloat("Yaw") : 0f;
            s.pitch = Float.isFinite(t.getFloat("Pitch")) ? t.getFloat("Pitch") : 0f;
            if (s.dimension == null || s.dimension.isBlank() || !finite(s.origin) || !finite(s.offer) || s.sword.isEmpty()) return null;
            // A loaded scene is never replayed blindly after a restart. Reforge scenes recover the
            // escrowed original; acquisition scenes abort without creating the reward.
            s.recovering = true;
            s.acquisition = t.getBoolean("Acquisition");
            return s;
        }
    }

    public final Map<UUID, Session> sessions = new HashMap<>();

    public static SmithSceneData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SmithSceneData::load, SmithSceneData::new, "wardbound_ancient_smith");
    }

    public static SmithSceneData load(CompoundTag t) {
        SmithSceneData d = new SmithSceneData();
        ListTag list = t.getList("Sessions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Session s = Session.load(list.getCompound(i));
            if (s != null) d.sessions.put(s.owner, s);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag l = new ListTag();
        sessions.values().forEach(s -> l.add(s.save()));
        t.put("Sessions", l);
        return t;
    }

    private static void vector(CompoundTag t, String key, Vec3 v) {
        CompoundTag a = new CompoundTag();
        a.putDouble("X", v.x);
        a.putDouble("Y", v.y);
        a.putDouble("Z", v.z);
        t.put(key, a);
    }

    private static Vec3 vector(CompoundTag t, String key) {
        CompoundTag a = t.getCompound(key);
        return new Vec3(a.getDouble("X"), a.getDouble("Y"), a.getDouble("Z"));
    }

    private static boolean finite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}
