package dev.marrowseal.wardbound.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Opens the Eye's screen.
 *
 * <p>A separate class purely so the item can name it without dragging a client
 * type into a class the server also loads; the call site is already guarded by
 * an isClientSide check, and the class is only ever resolved on that path.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientEyeHook {

    private ClientEyeHook() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new EyeScreen());
    }
}
