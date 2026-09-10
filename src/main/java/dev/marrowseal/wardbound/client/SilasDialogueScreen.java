package dev.marrowseal.wardbound.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.OpenSilasDialoguePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Silas dialogue presented as a readable scene rather than a chat dump.
 *
 * <p>Long testimony is paged instead of being squeezed outside the texture.
 * Keyboard, mouse-wheel and click-through navigation all mirror the visible
 * buttons so dialogue remains comfortable at different GUI scales.</p>
 */
public final class SilasDialogueScreen extends Screen {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Wardbound.MODID, "textures/gui/silas_dialogue.png");
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 196;
    private static final int BODY_W = PANEL_W - 66;
    private static final int LINES_PER_PAGE = 7;
    private static final int LINE_H = 12;

    private final OpenSilasDialoguePacket msg;
    private int left;
    private int top;
    private int page;
    private List<FormattedCharSequence> lines = List.of();
    private Button backButton;
    private Button nextButton;

    private SilasDialogueScreen(OpenSilasDialoguePacket msg) {
        super(Component.literal("Silas Veyr"));
        this.msg = msg;
    }

    public static void open(OpenSilasDialoguePacket msg) {
        Minecraft.getInstance().setScreen(new SilasDialogueScreen(msg));
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        lines = font.split(Component.translatable(msg.key()), BODY_W);
        if (lines.isEmpty()) lines = List.of(FormattedCharSequence.EMPTY);
        page = Math.max(0, Math.min(page, pageCount() - 1));

        backButton = addRenderableWidget(Button.builder(
                        Component.translatable("wardbound.ui.dialogue.back"), b -> previousPage())
                .bounds(left + 31, top + PANEL_H - 26, 72, 18).build());
        nextButton = addRenderableWidget(Button.builder(
                        Component.translatable("wardbound.ui.dialogue.next"), b -> advance())
                .bounds(left + PANEL_W - 103, top + PANEL_H - 26, 72, 18).build());
        refreshButtons();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        RenderSystem.enableBlend();
        g.blit(TEXTURE, left, top, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);

        g.drawCenteredString(font, "SILAS VEYR", left + PANEL_W / 2, top + 17, 0xFFDCD6C7);
        g.drawCenteredString(font, subtitle(), left + PANEL_W / 2, top + 31, subtitleColor());

        // Keep long prose away from the ornamental edge of the texture.
        int bodyLeft = left + 27;
        int bodyRight = left + PANEL_W - 27;
        int bodyTop = top + 52;
        int bodyBottom = top + 147;
        g.fill(bodyLeft, bodyTop, bodyRight, bodyBottom, 0x54030507);
        g.fill(bodyLeft + 5, bodyTop + 5, bodyLeft + 6, bodyBottom - 5, subtitleColor() & 0x70FFFFFF);
        g.fill(bodyLeft + 10, bodyTop + 4, bodyRight - 10, bodyTop + 5, 0x464B443D);

        int from = page * LINES_PER_PAGE;
        int to = Math.min(lines.size(), from + LINES_PER_PAGE);
        int y = bodyTop + 8;
        int textX = bodyLeft + 13;
        for (int i = from; i < to; i++) {
            g.drawString(font, lines.get(i), textX, y, 0xFFE3DED2, false);
            y += LINE_H;
        }

        if (pageCount() > 1) {
            String pageText = (page + 1) + " / " + pageCount();
            int hintY = top + 152;
            g.drawString(font, pageText, bodyRight - font.width(pageText) - 6, hintY, 0xFF827C70, false);
            g.drawString(font, Component.translatable("wardbound.ui.dialogue.hint"),
                    bodyLeft + 8, hintY, 0xFF827C70, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_RIGHT) {
            advance();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            previousPage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (pageCount() <= 1) return super.mouseScrolled(mouseX, mouseY, delta);
        if (delta < 0 && page < pageCount() - 1) {
            page++;
            pageSound(1.02f);
            refreshButtons();
        } else if (delta > 0) {
            previousPage();
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && mouseX >= left + 27 && mouseX <= left + PANEL_W - 27
                && mouseY >= top + 52 && mouseY <= top + 145) {
            advance();
            return true;
        }
        return false;
    }

    private int pageCount() {
        return Math.max(1, (lines.size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
    }

    private void advance() {
        if (page < pageCount() - 1) {
            page++;
            pageSound(1.02f);
            refreshButtons();
        } else {
            onClose();
        }
    }

    private void previousPage() {
        if (page > 0) {
            page--;
            pageSound(0.90f);
            refreshButtons();
        }
    }

    private void pageSound(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, pitch));
    }

    private void refreshButtons() {
        if (backButton == null || nextButton == null) return;
        backButton.visible = pageCount() > 1;
        backButton.active = page > 0;
        nextButton.setMessage(Component.translatable(page < pageCount() - 1
                ? "wardbound.ui.dialogue.next" : "wardbound.ui.dialogue.close"));
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
