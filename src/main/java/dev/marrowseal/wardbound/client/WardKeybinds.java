package dev.marrowseal.wardbound.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import dev.marrowseal.wardbound.Wardbound;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Wardbound.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WardKeybinds {

    public static final KeyMapping USE_CTHULHU_IDOL = new KeyMapping(
            "key.wardbound.use_cthulhu_idol",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.wardbound");

    public static final KeyMapping OPEN_WITNESS_LEDGER = new KeyMapping(
            "key.wardbound.open_witness_ledger",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.wardbound");

    public static final KeyMapping CAST_NINTH_MARGIN = new KeyMapping(
            "key.wardbound.cast_ninth_margin",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.wardbound");

    private WardKeybinds() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(USE_CTHULHU_IDOL);
        event.register(OPEN_WITNESS_LEDGER);
        event.register(CAST_NINTH_MARGIN);
    }
}
