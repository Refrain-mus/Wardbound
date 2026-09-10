package dev.marrowseal.wardbound.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;

/** Client-only bootstrap points that must not resolve UI classes on dedicated servers. */
public final class WardboundClientHooks {
    private WardboundClientHooks() {}

    public static void registerConfigScreen() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                        () -> new ConfigScreenHandler.ConfigScreenFactory(
                                (minecraft, parentScreen) -> new WardConfigScreen(parentScreen))));
    }
}
