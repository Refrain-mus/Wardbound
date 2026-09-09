package dev.marrowseal.wardbound.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import dev.marrowseal.wardbound.CuriosCompat;
import dev.marrowseal.wardbound.WardHistory;
import dev.marrowseal.wardbound.client.geo.WitnessLedgerRenderer;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class WitnessLedgerItem extends Item implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.witness_ledger.idle");
    private static final RawAnimation OPEN = RawAnimation.begin().thenPlay("animation.witness_ledger.open").thenLoop("animation.witness_ledger.opened");
    private static final RawAnimation CLOSE = RawAnimation.begin().thenPlay("animation.witness_ledger.close");
    private static final RawAnimation DISCOVERY = RawAnimation.begin().thenPlay("animation.witness_ledger.discovery");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public WitnessLedgerItem(Properties properties) {
        super(properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "ledger", 0, state -> state.setAndContinue(IDLE))
                .triggerableAnim("open", OPEN)
                .triggerableAnim("close", CLOSE)
                .triggerableAnim("discovery", DISCOVERY));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new WitnessLedgerRenderer();
                return renderer;
            }
        });
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            trigger(serverPlayer, stack, "open");
            if (open(serverPlayer)) {
                level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 0.92f);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public static boolean tryOpenFromHandsInventoryOrCurios(ServerPlayer player) {
        return tryOpenFromHandsInventoryOrCurios(player, true);
    }

    public static boolean tryOpenFromHandsInventoryOrCurios(ServerPlayer player, boolean animate) {
        // Prefer a visible hand stack so the trigger is actually seen when a
        // player also has another ledger equipped in Curios.
        if (player.getMainHandItem().is(WardItems.WITNESS_LEDGER.get())) { if (animate) trigger(player, player.getMainHandItem(), "open"); return open(player); }
        if (player.getOffhandItem().is(WardItems.WITNESS_LEDGER.get())) { if (animate) trigger(player, player.getOffhandItem(), "open"); return open(player); }
        ItemStack equipped = CuriosCompat.findFirstEquipped(player, WardItems.WITNESS_LEDGER.get());
        if (!equipped.isEmpty()) { if (animate) trigger(player, equipped, "open"); return open(player); }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(WardItems.WITNESS_LEDGER.get())) { if (animate) trigger(player, stack, "open"); return open(player); }
        }
        return false;
    }

    public static void triggerDiscovery(ServerPlayer player) {
        triggerFirstLedger(player, "discovery");
    }

    public static void triggerClose(ServerPlayer player) {
        triggerFirstLedger(player, "close");
    }

    private static void triggerFirstLedger(ServerPlayer player, String animation) {
        if (player == null) return;
        if (player.getMainHandItem().is(WardItems.WITNESS_LEDGER.get())) { trigger(player, player.getMainHandItem(), animation); return; }
        if (player.getOffhandItem().is(WardItems.WITNESS_LEDGER.get())) { trigger(player, player.getOffhandItem(), animation); return; }
        ItemStack equipped = CuriosCompat.findFirstEquipped(player, WardItems.WITNESS_LEDGER.get());
        if (!equipped.isEmpty()) { trigger(player, equipped, animation); return; }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(WardItems.WITNESS_LEDGER.get())) { trigger(player, stack, animation); return; }
        }
    }

    private static void trigger(ServerPlayer player, ItemStack stack, String animation) {
        if (player == null || stack == null || stack.isEmpty() || !(player.level() instanceof ServerLevel level)) return;
        if (!(stack.getItem() instanceof WitnessLedgerItem ledger)) return;
        ledger.triggerAnim(player, GeoItem.getOrAssignId(stack, level), "ledger", animation);
    }

    private static boolean open(ServerPlayer player) {
        if (!WardHistory.hasLedger(player)) return false;
        WardHistory.openLedger(player);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("A field ledger that remembers wards, cards and witnesses.")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("Right-click or use the keybind to open it.")
                .withStyle(ChatFormatting.GRAY));
    }
}
