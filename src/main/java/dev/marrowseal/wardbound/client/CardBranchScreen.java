package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.CardBranches;
import dev.marrowseal.wardbound.ForbiddenBargain;
import dev.marrowseal.wardbound.WardConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;
import java.util.function.IntConsumer;

/** No choice is sent until the player signs one of the two complete clauses. */
public final class CardBranchScreen extends Screen {
    private final Screen parent;
    private final ForbiddenBargain card;
    private final int revision;
    private final IntConsumer sign;
    private int columnWidth, top, bottom, bodyHeight, fullHeight, scroll;
    private List<FormattedCharSequence> original, alternate;
    private final long openedNanos = System.nanoTime();

    public CardBranchScreen(Screen parent, ForbiddenBargain card, int revision, IntConsumer sign) {
        super(Component.literal("Choose the Revision"));
        this.parent = parent; this.card = card; this.revision = revision; this.sign = sign;
    }

    @Override protected void init() {
        columnWidth = Math.min(230, (width - 30) / 2);
        original = font.split(Component.literal(CardBranches.description(card, revision, 1)), columnWidth - 16);
        alternate = font.split(Component.literal(CardBranches.description(card, revision, 2)), columnWidth - 16);
        fullHeight = Math.max(original.size(), alternate.size()) * 10;
        bodyHeight = Math.min(fullHeight, Math.max(30, height - 148));
        scroll = Math.min(scroll, Math.max(0, fullHeight - bodyHeight));
        top = Math.max(42, (height - bodyHeight - 110) / 2);
        bottom = top + 24 + bodyHeight + 12;
        for (int branch = 1; branch <= 2; branch++) {
            final int chosen = branch;
            int x = width / 2 + (branch == 1 ? -columnWidth - 5 : 5);
            addRenderableWidget(Button.builder(Component.literal("Sign this path"), b -> sign.accept(chosen))
                    .bounds(x, bottom, columnWidth, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Back to the hand"), b -> onClose())
                .bounds(width / 2 - 75, bottom + 28, 150, 20).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        double uiTime = (System.nanoTime() - openedNanos) / 1_000_000_000.0;
        CardUiVfx.ambient(g, width, height, uiTime, 0xFFE4C47A, WardConfig.accessibilityReduceMotion ? 0.28f : 0.72f, WardConfig.accessibilityReduceMotion);
        g.drawCenteredString(font, card.title + " / Revision " + revision, width / 2, top - 30, 0xE4C47A);
        g.drawCenteredString(font, "Choose again whenever you sign a revised copy.", width / 2, top - 17, 0xABA39A);
        for (int branch = 1; branch <= 2; branch++) {
            int x = width / 2 + (branch == 1 ? -columnWidth - 5 : 5);
            boolean hot = mx >= x && mx < x + columnWidth && my >= top && my < bottom;
            int aura = branch == 1 ? 0xFFBDA478 : 0xFF79CDBA;
            CardUiVfx.cardAura(g, x, top, columnWidth, bottom - top - 4, uiTime, card.id * 41L + branch * 137L, aura, hot ? 1f : 0.22f, .48f, WardConfig.accessibilityReduceMotion);
            g.fill(x, top, x + columnWidth, bottom - 4, 0xED182124);
            g.fill(x, top, x + columnWidth, top + 2, branch == 1 ? 0xFFBDA478 : 0xFF79CDBA);
            g.drawCenteredString(font, CardBranches.title(card, branch), x + columnWidth / 2, top + 8, 0xE4DDD0);
            g.enableScissor(x + 6, top + 24, x + columnWidth - 6, top + 25 + bodyHeight);
            int y = top + 25 - scroll;
            for (FormattedCharSequence line : branch == 1 ? original : alternate) {
                g.drawString(font, line, x + 8, y, 0xC1CDCE); y += 10;
            }
            g.disableScissor();
        }
        if (fullHeight > bodyHeight) g.drawCenteredString(font, "Scroll to read both clauses", width / 2, bottom + 52, 0xABA39A);
        super.render(g, mx, my, partial);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        scroll = Math.max(0, Math.min(fullHeight - bodyHeight, scroll - (int)(delta * 20)));
        return true;
    }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
}
