package dev.marrowseal.wardbound.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.OpenSilasDialoguePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** A restrained dialogue surface so Silas speaks like a scene, not a chat spam source. */
public final class SilasDialogueScreen extends Screen {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Wardbound.MODID, "textures/gui/silas_dialogue.png");
    private final OpenSilasDialoguePacket msg;
    private int left, top;
    private final int panelWidth = 320;
    private final int panelHeight = 196;

    private SilasDialogueScreen(OpenSilasDialoguePacket msg) {
        super(Component.literal("Silas Veyr"));
        this.msg = msg;
    }

    public static void open(OpenSilasDialoguePacket msg) {
        Minecraft.getInstance().setScreen(new SilasDialogueScreen(msg));
    }

    @Override
    protected void init() {
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), b -> onClose())
                .bounds(left + panelWidth / 2 - 40, top + panelHeight - 26, 80, 18).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        RenderSystem.enableBlend();
        g.blit(TEXTURE, left, top, 0, 0, panelWidth, panelHeight, panelWidth, panelHeight);

        g.drawCenteredString(font, "SILAS VEYR", left + panelWidth / 2, top + 18, 0xFFD6D0C1);
        g.drawCenteredString(font, subtitle(), left + panelWidth / 2, top + 31, subtitleColor());

        List<FormattedCharSequence> lines = font.split(Component.translatable(msg.key()), panelWidth - 56);
        int lineHeight = 12;
        int bodyTop = top + 54;
        int bodyBottom = top + panelHeight - 42;
        int textHeight = lines.size() * lineHeight;
        int y = bodyTop + Math.max(0, (bodyBottom - bodyTop - textHeight) / 2);
        int centerX = left + panelWidth / 2;
        for (FormattedCharSequence line : lines) {
            int x = centerX - font.width(line) / 2;
            g.drawString(font, line, x, y, 0xFFE0DBCE, false);
            y += lineHeight;
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private Component subtitle() {
        return Component.translatable("silas.subtitle." + Math.max(1, Math.min(5, msg.stage())));
    }

    private int subtitleColor() {
        return switch (msg.stage()) {
            case 1 -> 0xFF9E9A8C;
            case 2 -> 0xFFA69071;
            case 3 -> 0xFF9DAA98;
            case 4 -> 0xFFB58AA2;
            default -> 0xFFB7A77E;
        };
    }
}
