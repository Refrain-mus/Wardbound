package dev.marrowseal.wardbound.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Client-only bridge for the vanilla Totem-style item activation presentation. */
public final class ClientItemActivation {
    private ClientItemActivation() {}

    public static void show(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null) mc.gameRenderer.displayItemActivation(stack.copy());
    }
}
