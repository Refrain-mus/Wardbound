package dev.marrowseal.wardbound.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.champion.SilasLore;
import dev.marrowseal.wardbound.net.OpenSilasFragmentPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class SilasFragmentScreen extends Screen {
    private static ResourceLocation texture(int family) {
        String suffix = switch (family) {
            case 1 -> "archive";
            case 2 -> "docket";
            case 3 -> "table";
            case 4 -> "personal";
            default -> "road";
        };
        return new ResourceLocation(Wardbound.MODID, "textures/gui/silas_fragment_" + suffix + ".png");
    }
    private final OpenSilasFragmentPacket msg;
    private int left, top;
    private final int panelWidth = 300;
    private final int panelHeight = 210;

    private SilasFragmentScreen(OpenSilasFragmentPacket msg) {
        super(Component.literal("Unresolved Testimony"));
        this.msg = msg;
    }

    public static void open(OpenSilasFragmentPacket msg) {
        Minecraft.getInstance().setScreen(new SilasFragmentScreen(msg));
    }

    @Override
    protected void init() {
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), b -> onClose())
                .bounds(left + panelWidth / 2 - 40, top + panelHeight - 25, 80, 18).build());
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float p) {
        renderBackground(g);
        RenderSystem.enableBlend();
        g.blit(texture(msg.family()), left, top, 0, 0, panelWidth, panelHeight, panelWidth, panelHeight);
        SilasLore.Fragment fragment = SilasLore.Fragment.byId(msg.fragmentId());
        Component title = fragment == null ? Component.literal("UNRESOLVED TESTIMONY") : Component.translatable("silas.fragment.title." + fragment.id);
        Component family = fragment == null ? Component.literal("UNKNOWN SOURCE") : Component.translatable("silas.fragment.family." + fragment.family.name().toLowerCase(java.util.Locale.ROOT));
        g.drawCenteredString(font, family, left + panelWidth / 2, top + 18, familyColor());
        g.drawCenteredString(font, title, left + panelWidth / 2, top + 34, 0xFFDED3B6);
        g.fill(left + 34, top + 49, left + panelWidth - 34, top + 50, 0x886F604B);

        Component body = Component.translatable(fragment == null ? "silas.fragment.unknown" : SilasLore.translationKey(fragment));
        List<FormattedCharSequence> lines = font.split(body, panelWidth - 52);
        int y = top + 62;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, left + 26, y, 0xFFD4C8AC, false);
            y += 11;
        }
        g.drawCenteredString(font, Component.translatable("silas.fragment.copied"), left + panelWidth / 2, top + panelHeight - 42, 0xFF8E846F);
        super.render(g, mx, my, p);
    }

    private int familyColor() {
        return switch (msg.family()) {
            case 1 -> 0xFF9D8F76; // archive
            case 2 -> 0xFF9A6F78; // docket
            case 3 -> 0xFF927A55; // table
            case 4 -> 0xFF8A8798; // personal
            default -> 0xFF7F9085; // road
        };
    }
}
