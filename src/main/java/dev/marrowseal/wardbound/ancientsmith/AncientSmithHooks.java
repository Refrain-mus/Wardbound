package dev.marrowseal.wardbound.ancientsmith;

import dev.marrowseal.wardbound.WardHud;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;

@Mod.EventBusSubscriber(modid = "wardbound")
public final class AncientSmithHooks {
    public enum Result {STARTED, WRONG_THREAD, INVALID_SWORD, NOT_IN_INVENTORY, PLAYER_BUSY, NO_SMITH, NO_ANVIL, SMITH_BUSY, INVALID_PLAYER}

    /** Original reforge API: the exact inventory stack is escrowed and restored on abort. */
    public static Result beginNhalSulReforge(ServerPlayer player, ItemStack sword) {
        if (!validThread(player)) return player == null || player.getServer() == null ? Result.INVALID_PLAYER : Result.WRONG_THREAD;
        if (!validPlayer(player)) return Result.INVALID_PLAYER;
        if (!isNhalSul(sword)) return Result.INVALID_SWORD;
        int slot = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i) == sword) { slot = i; break; }
        }
        if (slot < 0) return Result.NOT_IN_INVENTORY;
        Result result = beginSession(player, sword.copy(), slot, false);
        if (result != Result.STARTED) return result;
        player.getInventory().setItem(slot, ItemStack.EMPTY);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return Result.STARTED;
    }

    /** Acquisition API used by Wardbound progression. Abort/restart/logout never creates the reward. */
    public static Result beginNhalSulAcquisition(ServerPlayer player, ItemStack reward) {
        if (!validThread(player)) return player == null || player.getServer() == null ? Result.INVALID_PLAYER : Result.WRONG_THREAD;
        if (!validPlayer(player)) return Result.INVALID_PLAYER;
        if (!isNhalSul(reward)) return Result.INVALID_SWORD;
        return beginSession(player, reward.copy(), -1, true);
    }

    private static Result beginSession(ServerPlayer player, ItemStack sword, int slot, boolean acquisition) {
        SmithSceneData data = SmithSceneData.get(player.server);
        if (data.sessions.containsKey(player.getUUID())) return Result.PLAYER_BUSY;
        AncientSmithEntity smith = player.serverLevel().getEntitiesOfClass(AncientSmithEntity.class,
                        player.getBoundingBox().inflate(32), AncientSmithEntity::isAlive)
                .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (smith == null) return Result.NO_SMITH;
        for (SmithSceneData.Session existing : data.sessions.values()) if (existing.smith.equals(smith.getUUID())) return Result.SMITH_BUSY;
        Vec3 expectedAnvil = smith.local(0, 0, -5.125);
        if (player.serverLevel().getEntitiesOfClass(AncientAnvilEntity.class, smith.getBoundingBox().inflate(10),
                anvil -> anvil.position().distanceToSqr(expectedAnvil) < 1.0).isEmpty()) return Result.NO_ANVIL;

        SmithSceneData.Session session = new SmithSceneData.Session();
        session.owner = player.getUUID();
        session.smith = smith.getUUID();
        session.dimension = player.level().dimension().location().toString();
        session.entryDimension = session.dimension;
        session.origin = player.position();
        session.yaw = player.getYRot();
        session.pitch = player.getXRot();
        session.slot = slot;
        session.sword = sword.copy();
        session.acquisition = acquisition;
        session.offer = safeOfferPoint(player, smith);
        data.sessions.put(session.owner, session);
        data.setDirty();
        smith.scene(0, session.sword, session.offer);
        return Result.STARTED;
    }

    private static Vec3 safeOfferPoint(ServerPlayer player, AncientSmithEntity smith) {
        // The authored forge scene has a deliberate presentation mark directly in front of the
        // Smith. Prefer that stable composition instead of inheriting whichever direction the
        // player happened to be looking when the session began.
        Vec3 authored = smith.local(0, .35, -10.6);
        Vec3 delta = authored.subtract(player.position());
        if (Double.isFinite(authored.x + authored.y + authored.z)
                && player.level().noCollision(player, player.getBoundingBox().move(delta))) return authored;
        // Debug/world-placed Smith fallback: keep the old local-to-player solution if the authored
        // presentation mark is obstructed.
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-6) {
            double yaw = Math.toRadians(player.getYRot());
            flat = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        } else flat = flat.normalize();
        return player.position().add(flat.scale(2.4)).add(0, .35, 0);
    }


    private static boolean carriesNhalSul(ServerPlayer player) {
        if (player == null) return false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (isNhalSul(player.getInventory().getItem(i))) return true;
        return false;
    }

    private static boolean isNhalSul(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getCount() == 1
                && AncientSmithModule.id("nhal_sul").equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    private static boolean validThread(ServerPlayer player) {
        return player != null && player.getServer() != null && player.server.isSameThread();
    }

    private static boolean validPlayer(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator();
    }

    /** Set the pre-realm return location after STARTED, before the next server tick. */
    public static void setReturnAnchor(ServerPlayer player, ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Server thread required");
        if (position == null || !Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("Non-finite return anchor");
        SmithSceneData data = SmithSceneData.get(player.server);
        SmithSceneData.Session session = data.sessions.get(player.getUUID());
        if (session == null) throw new IllegalStateException("No scene");
        session.entryDimension = dimension.location().toString();
        session.origin = position;
        session.yaw = yaw;
        session.pitch = pitch;
        data.setDirty();
    }

    /** Safe abort. Acquisition scenes return empty-handed; reforge scenes recover the original stack. */
    public static void cancel(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        SmithSceneData data = SmithSceneData.get(player.server);
        SmithSceneData.Session session = data.sessions.get(player.getUUID());
        if (session == null) return;
        session.recovering = true;
        clear(player, session);
        data.setDirty();
        deliver(player, session, false);
    }

    public static boolean claim(ServerPlayer player) {
        SmithSceneData.Session session = SmithSceneData.get(player.server).sessions.get(player.getUUID());
        if (session == null) return false;
        if (session.recovering) return deliver(player, session, false);
        if (session.tick < SmithTimeline.OFFER_RELEASE || !player.level().dimension().location().toString().equals(session.dimension)
                || player.position().distanceToSqr(session.offer) > 2.25) return false;
        return deliver(player, session, true);
    }

    private static boolean deliver(ServerPlayer player, SmithSceneData.Session session, boolean complete) {
        if (!player.isAlive() || player.isSpectator()) return false;
        SmithSceneData data = SmithSceneData.get(player.server);
        if (data.sessions.get(player.getUUID()) != session) return false;

        // Cross-storage crash reconciliation: player inventory and SavedData are not necessarily
        // flushed in the same filesystem write. If the blade was already inserted but an older
        // scene record survived the crash, do not insert a second Nhal-Sûl on recovery. For an
        // acquisition this also commits progression, because possession proves the reward side of
        // the transaction survived.
        if (session.recovering && carriesNhalSul(player)) {
            data.sessions.remove(session.owner);
            data.setDirty();
            clear(player, session);
            MinecraftForge.EVENT_BUS.post(new AncientSmithReturnEvent(player, session, session.acquisition));
            return true;
        }

        // An interrupted acquisition never materializes Nhal-Sûl. The scene record is simply closed.
        if (session.acquisition && !complete) {
            data.sessions.remove(session.owner);
            data.setDirty();
            clear(player, session);
            MinecraftForge.EVENT_BUS.post(new AncientSmithReturnEvent(player, session, false));
            return true;
        }

        int slot = session.slot;
        if (slot < 0 || slot >= player.getInventory().getContainerSize() || !player.getInventory().getItem(slot).isEmpty())
            slot = player.getInventory().getFreeSlot();
        if (slot < 0) {
            if (complete && session.tick % 40 == 0) WardHud.send(player, "ANCIENT FORGE // MAKE ROOM FOR THE BLADE", WardHud.Mood.WARD);
            return false;
        }

        player.getInventory().setItem(slot, session.sword.copy());
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        data.sessions.remove(session.owner);
        data.setDirty();
        clear(player, session);
        MinecraftForge.EVENT_BUS.post(new AncientSmithReturnEvent(player, session, complete));
        return true;
    }

    private static AncientSmithEntity smith(ServerPlayer player, SmithSceneData.Session session) {
        ResourceLocation id = ResourceLocation.tryParse(session.dimension);
        if (id == null) return null;
        ServerLevel level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        return level != null && level.getEntity(session.smith) instanceof AncientSmithEntity entity ? entity : null;
    }

    private static void clear(ServerPlayer player, SmithSceneData.Session session) {
        AncientSmithEntity entity = smith(player, session);
        if (entity != null) entity.clearScene();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        SmithSceneData data = SmithSceneData.get(server);
        for (SmithSceneData.Session session : new ArrayList<>(data.sessions.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.owner);
            if (player == null) {
                if (!session.recovering) { session.recovering = true; data.setDirty(); }
                continue;
            }
            AncientSmithEntity smith = smith(player, session);
            boolean wrongDimension = !player.level().dimension().location().toString().equals(session.dimension);
            if (session.recovering || !player.isAlive() || wrongDimension || smith == null
                    || player.distanceToSqr(smith) > 4096 || session.tick > SmithTimeline.TIMEOUT) {
                session.recovering = true;
                clear(player, session);
                data.setDirty();
                if (player.isAlive()) deliver(player, session, false);
                continue;
            }
            session.tick++;
            smith.scene(session.tick, session.sword, session.offer);
            holdPresentation(player, smith, session);
            data.setDirty();
            if (SmithTimeline.impact(session.tick)) SmithVfx.emit(smith,
                    session.tick == SmithTimeline.FINAL_HIT ? SmithVfx.FINAL : SmithVfx.STRIKE);
            if (session.tick == SmithTimeline.SILENCE) SmithVfx.emit(smith, SmithVfx.SILENCE);
            if (session.tick == 72) say(player, "second_one", 1400);
            if (session.tick == 192) say(player, "first", 1400);
            if (session.tick == 292) say(player, "second", 1400);
            if (session.tick == SmithTimeline.OFFER) say(player, "use", 1500);
            if (session.tick >= SmithTimeline.OFFER_RELEASE && player.position().distanceToSqr(session.offer) < 2.25)
                deliver(player, session, true);
        }
    }


    private static void holdPresentation(ServerPlayer player, AncientSmithEntity smith, SmithSceneData.Session session) {
        if (player == null || smith == null || session == null) return;
        if (session.tick < 390 || session.tick > SmithTimeline.OFFER + 120) return;
        Vec3 anchor = new Vec3(session.offer.x, session.offer.y - 0.35, session.offer.z);
        if (!Double.isFinite(anchor.x) || !Double.isFinite(anchor.y) || !Double.isFinite(anchor.z)) return;
        double dx = smith.getX() - anchor.x;
        double dz = smith.getZ() - anchor.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        // Position is authoritative during the hand-off, but do not re-teleport just because the
        // client moved its camera. Repeated same-position teleports caused visible jitter and can
        // fight camera/animation mods. Only correct genuine positional drift.
        if (player.position().distanceToSqr(anchor) > 0.0225) {
            player.teleportTo(player.serverLevel(), anchor.x, anchor.y, anchor.z, yaw, 0f);
        } else {
            player.setYRot(yaw);
            player.setYHeadRot(yaw);
            player.setYBodyRot(yaw);
            player.setXRot(0f);
        }
        player.fallDistance = 0f;
    }

    private static void say(ServerPlayer player, String key, int durationMs) {
        WardHud.send(player, net.minecraft.network.chat.Component.translatable("ancient_smith.dialogue." + key).getString(), WardHud.Mood.WARD, durationMs);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SmithSceneData data = SmithSceneData.get(player.server);
        SmithSceneData.Session session = data.sessions.get(player.getUUID());
        if (session == null) return;
        session.recovering = true;
        clear(player, session);
        data.setDirty();
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SmithSceneData.Session session = SmithSceneData.get(player.server).sessions.get(player.getUUID());
        if (session != null) {
            session.recovering = true;
            deliver(player, session, false);
        }
    }

    private AncientSmithHooks() {}
}
