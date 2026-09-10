package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.item.WardItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;

/** Death-return, retry cadence and physical protection for Master invocation relics. */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class MasterInvocationRelics {
    private static final long RETRY_COOLDOWN = 7L * 60L * 20L;
    private static final long MESSAGE_COOLDOWN = 60L * 20L;

    private MasterInvocationRelics() {}

    public static boolean canInvoke(ServerPlayer player, MasterArenaManager.Kind kind) {
        if (player == null || player.getServer() == null || kind == null || player.isCreative()) return true;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        long now = player.getServer().overworld().getGameTime();
        long until = data.uniqueLong(id, cooldownKey(kind));
        if (until == Long.MIN_VALUE || now >= until) {
            if (until != Long.MIN_VALUE) data.setUniqueLong(id, cooldownKey(kind), Long.MIN_VALUE);
            return true;
        }
        long last = data.uniqueLong(id, messageKey(kind));
        if (last == Long.MIN_VALUE || now < last || now - last >= MESSAGE_COOLDOWN) {
            int index = Math.floorMod(data.uniqueInt(id, lineKey(kind)), lines(kind).length);
            data.setUniqueInt(id, lineKey(kind), index + 1);
            data.setUniqueLong(id, messageKey(kind), now);
            long seconds = Math.max(1L, (until - now + 19L) / 20L);
            long minutes = seconds / 60L;
            long rem = seconds % 60L;
            String time = minutes > 0 ? minutes + "m " + rem + "s" : rem + "s";
            WardHud.send(player, lines(kind)[index] + " // The road will answer again in " + time + ".", WardHud.Mood.WARD, 4200);
        }
        return false;
    }

    public static void noteDeath(ServerPlayer player, MasterArenaManager.Kind kind) {
        if (player == null || player.getServer() == null || kind == null || player.isCreative()) return;
        LockData data = LockData.get(player.getServer());
        long now = player.getServer().overworld().getGameTime();
        data.setUniqueLong(player.getUUID(), cooldownKey(kind), now + RETRY_COOLDOWN);
        data.setUniqueLong(player.getUUID(), messageKey(kind), Long.MIN_VALUE);
    }

    public static void rescueDeathDrops(ServerPlayer player, Collection<ItemEntity> drops) {
        if (player == null || player.getServer() == null || drops == null || drops.isEmpty()) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        for (Iterator<ItemEntity> it = drops.iterator(); it.hasNext();) {
            ItemEntity entity = it.next();
            if (entity == null) continue;
            ItemStack stack = entity.getItem();
            MasterArenaManager.Kind kind = kindFor(stack);
            if (kind == null) continue;
            data.setUniqueInt(id, pendingRelicKey(kind), 1);
            it.remove();
            entity.discard();
        }
    }

    public static void rescueGroundRelics(ServerPlayer player, ServerLevel level, BlockPos center) {
        if (player == null || player.getServer() == null || level == null || center == null) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        AABB box = new AABB(center).inflate(112, 112, 112);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box, e -> !e.isRemoved() && isInvocationRelic(e.getItem()))) {
            MasterArenaManager.Kind kind = kindFor(entity.getItem());
            if (kind == null) continue;
            data.setUniqueInt(id, pendingRelicKey(kind), 1);
            entity.discard();
        }
    }

    public static void deliverPending(ServerPlayer player) {
        if (player == null || player.getServer() == null || !player.isAlive() || player.isSpectator()) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        for (MasterArenaManager.Kind kind : MasterArenaManager.Kind.values()) {
            if (data.uniqueInt(id, pendingRelicKey(kind)) == 0) continue;
            Item item = itemFor(kind);
            if (item == Items.AIR) { data.setUniqueInt(id, pendingRelicKey(kind), 0); continue; }
            if (contains(player, item)) {
                data.setUniqueInt(id, pendingRelicKey(kind), 0);
                continue;
            }
            ItemStack restored = new ItemStack(item);
            if (player.getInventory().add(restored)) {
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
                data.setUniqueInt(id, pendingRelicKey(kind), 0);
                WardHud.send(player, returnLine(kind), WardHud.Mood.GLITCH, 3000);
            }
        }
    }

    public static void consumeOnVictory(ServerPlayer player, MasterArenaManager.Kind kind) {
        if (player == null || player.getServer() == null || kind == null || player.isCreative()) return;
        Item item = itemFor(kind);
        if (item == Items.AIR) return;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        LockData.get(player.getServer()).setUniqueInt(player.getUUID(), pendingRelicKey(kind), 0);
    }

    public static boolean isInvocationRelic(ItemStack stack) { return kindFor(stack) != null; }

    @SubscribeEvent
    public static void protectGroundRelic(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (!(e instanceof ItemEntity item) || !isInvocationRelic(item.getItem())) return;
        item.setInvulnerable(true);
        item.setUnlimitedLifetime();
        item.clearFire();
        // Manual drops keep normal gravity so ImportantRelicItem's void rescue can
        // still recover them. Controlled full-inventory deliveries use the helper
        // below to create a stationary, glowing fallback at the player's feet.
    }


    /**
     * Give a progression key normally; if inventory is full, materialize it in a
     * controlled hover at the player's feet. Unlike the global EntityJoin hook this
     * never changes the physics of a relic the player deliberately throws later.
     */
    public static boolean giveOrProtectedDrop(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        if (player.getInventory().add(stack)) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            return true;
        }
        ItemEntity entity = player.drop(stack, false);
        if (entity == null) return false;
        entity.setInvulnerable(true);
        entity.setUnlimitedLifetime();
        entity.clearFire();
        entity.setNoGravity(true);
        entity.setDeltaMovement(0, 0, 0);
        entity.setGlowingTag(true);
        entity.setTarget(player.getUUID());
        entity.setNoPickUpDelay();
        return true;
    }

    private static MasterArenaManager.Kind kindFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.is(WardItems.GAMBLER_INVITATION.get())) return MasterArenaManager.Kind.GAMBLER;
        if (stack.is(WardItems.ARCHIVE_WRIT.get())) return MasterArenaManager.Kind.CURATOR;
        if (stack.is(WardItems.FINAL_WITNESS_BELL.get())) return MasterArenaManager.Kind.NOTARY;
        if (stack.is(WardItems.SCORE_BEYOND_THE_MARGIN.get())) return MasterArenaManager.Kind.MAESTRO;
        return null;
    }

    private static Item itemFor(MasterArenaManager.Kind kind) {
        return switch (kind) {
            case GAMBLER -> WardItems.GAMBLER_INVITATION.get();
            case CURATOR -> WardItems.ARCHIVE_WRIT.get();
            case NOTARY -> WardItems.FINAL_WITNESS_BELL.get();
            case MAESTRO -> WardItems.SCORE_BEYOND_THE_MARGIN.get();
        };
    }

    private static boolean contains(ServerPlayer p, Item item) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) return true;
        return false;
    }

    private static String cooldownKey(MasterArenaManager.Kind kind) { return "master_retry_" + kind.name().toLowerCase() + "_until"; }
    private static String messageKey(MasterArenaManager.Kind kind) { return "master_retry_" + kind.name().toLowerCase() + "_message"; }
    private static String lineKey(MasterArenaManager.Kind kind) { return "master_retry_" + kind.name().toLowerCase() + "_line"; }
    private static String pendingRelicKey(MasterArenaManager.Kind kind) { return "master_relic_" + kind.name().toLowerCase() + "_pending"; }

    private static String[] lines(MasterArenaManager.Kind kind) {
        return switch (kind) {
            case GAMBLER -> new String[]{
                    "THE PALE GAMBLER // As entertaining as it was, I cannot spend every second swatting at the same ant. Learn to crawl back with style.",
                    "THE PALE GAMBLER // The House has not finished shuffling your last death. Do not ask for another hand while your blood is still on the felt.",
                    "THE PALE GAMBLER // Again? Patience. Even desperation needs rhythm if it expects an audience."
            };
            case CURATOR -> new String[]{
                    "ASHEN CURATOR // Your last death is still being catalogued. The archive does not shelve the same error twice in the same breath.",
                    "ASHEN CURATOR // The ash has not settled on your previous entry. Return when the margin can be read again.",
                    "ASHEN CURATOR // Repetition does not become scholarship merely because the witness is stubborn."
            };
            case NOTARY -> new String[]{
                    "MOURNING NOTARY // Your previous death remains before the court. A second petition filed this quickly is contempt, not testimony.",
                    "MOURNING NOTARY // The ink on your last failure is still wet. The court will not accept a duplicate signature.",
                    "MOURNING NOTARY // The bell was heard. The hearing has not yet been reopened."
            };
            case MAESTRO -> new String[]{
                    "MAESTRO // Your previous ending is still ringing. Do not demand an encore before the hall has stopped shaking.",
                    "MAESTRO // The orchestra is resetting the measure you died in. Return when it can be performed cleanly.",
                    "MAESTRO // Again already? Even panic requires tempo."
            };
        };
    }

    private static String returnLine(MasterArenaManager.Kind kind) {
        return switch (kind) {
            case GAMBLER -> "THE LAST TABLE // The invitation has found its way back into your hand.";
            case CURATOR -> "ASH ARCHIVE // The writ refuses to remain among the dead.";
            case NOTARY -> "FINAL WITNESS // The bell has returned. The court remembers the unfinished hearing.";
            case MAESTRO -> "MAESTRO STAGE // The score has found its way back to the performer.";
        };
    }
}
