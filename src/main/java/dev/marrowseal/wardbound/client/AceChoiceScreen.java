package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.AceChoicePacket;
import dev.marrowseal.wardbound.net.OpenAceChoicePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Housebreaker's Ace offers two permanent laws stolen from the Last Table. */
public final class AceChoiceScreen extends Screen {
    private final OpenAceChoicePacket msg;
    private boolean answered;

    public AceChoiceScreen(OpenAceChoicePacket msg) {
        super(Component.literal("Housebreaker's Ace"));
        this.msg = msg;
    }

    private int cardW() { return Math.min(154, Math.max(112, (width - 84) / 2)); }
    private int cardH() { return Math.min(222, Math.max(174, cardW() * 3 / 2)); }
    private int gap() { return Math.max(22, Math.min(42, width / 15)); }
    private int leftX() { return width / 2 - gap() / 2 - cardW(); }
    private int rightX() { return width / 2 + gap() / 2; }
    private int topY() { return Math.max(58, height / 2 - cardH() / 2 + 10); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        g.fill(0, 0, width, height, 0xB006070B);
        int y = topY(), w = cardW(), h = cardH();
        boolean leftHot = !msg.wardLawSigned() && inside(mx, my, leftX(), y, w, h);
        boolean rightHot = !msg.worldLawSigned() && inside(mx, my, rightX(), y, w, h);

        g.drawCenteredString(font, "HOUSEBREAKER'S ACE", width / 2, 15, 0xFFF1D27A);
        g.drawCenteredString(font, "You did not win a consumable. You stole the rules of the table.", width / 2, 29, 0xFFD2C4A8);
        g.drawCenteredString(font, msg.remainingUses() + " law" + (msg.remainingUses() == 1 ? " remains unsigned" : "s remain unsigned"),
                width / 2, 42, msg.remainingUses() <= 1 ? 0xFFE98A86 : 0xFF8FD5C1);

        drawAceCard(g, leftX(), y, w, h, leftHot, 0, msg.wardLawSigned());
        drawAceCard(g, rightX(), y, w, h, rightHot, 1, msg.worldLawSigned());
        g.drawCenteredString(font, "ESC closes the Ace. A signature is spent only when a law is chosen.", width / 2,
                Math.min(height - 15, y + h + 13), 0xFF81777B);
        super.render(g, mx, my, partial);
    }

    private void drawAceCard(GuiGraphics g, int x, int y, int w, int h, boolean hot, int choice, boolean signed) {
        boolean world = choice == 1;
        int accent = signed ? 0xFF59545A : world ? (hot ? 0xFFFF7C70 : 0xFFB64F4D) : (hot ? 0xFFFFDE85 : 0xFFB79A50);
        int inner = signed ? 0xFF121214 : world ? 0xFF251216 : 0xFF1C1910;
        int lift = hot ? 5 : 0;
        y -= lift;
        if (hot) g.fill(x - 6, y - 6, x + w + 6, y + h + 6, world ? 0x664A0914 : 0x66514312);
        g.fill(x + 7, y + 8, x + w + 10, y + h + 11, 0x77000000);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, accent);
        g.fill(x, y, x + w, y + h, 0xFF08090B);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, inner);
        g.fill(x + 8, y + 8, x + w - 8, y + 9, accent);
        g.fill(x + 8, y + h - 9, x + w - 8, y + h - 8, accent);

        int cx = x + w / 2, sy = y + 46;
        if (world) {
            // Broken table / world turned into the same game.
            g.fill(cx - 16, sy, cx + 16, sy + 2, accent);
            g.fill(cx - 12, sy - 9, cx - 10, sy + 13, accent);
            g.fill(cx + 10, sy - 9, cx + 12, sy + 13, accent);
            g.fill(cx - 12, sy - 10, cx, sy - 17, accent);
            g.fill(cx, sy - 17, cx + 12, sy - 10, accent);
            g.fill(cx - 2, sy - 10, cx + 2, sy - 2, 0xFFFFC26B);
            g.fill(cx + 1, sy - 2, cx + 5, sy + 5, 0xFFFFC26B);
            g.fill(cx - 2, sy + 5, cx + 2, sy + 13, 0xFFFFC26B);
        } else {
            // Crowned heart / the House owes the player instead.
            g.fill(cx - 10, sy - 8, cx - 2, sy - 2, accent);
            g.fill(cx + 2, sy - 8, cx + 10, sy - 2, accent);
            g.fill(cx - 13, sy - 2, cx + 13, sy + 4, accent);
            g.fill(cx - 9, sy + 4, cx + 9, sy + 9, accent);
            g.fill(cx - 4, sy + 9, cx + 4, sy + 13, accent);
            g.fill(cx - 12, sy - 15, cx - 8, sy - 10, 0xFFF6DEA1);
            g.fill(cx - 2, sy - 18, cx + 2, sy - 10, 0xFFF6DEA1);
            g.fill(cx + 8, sy - 15, cx + 12, sy - 10, 0xFFF6DEA1);
        }

        String title = world ? "THE WORLD IS ANOTHER TABLE" : "THE HOUSE OWES YOU";
        String[] lines = world ? new String[]{
                "PERMANENT WORLD LAW",
                "+15% damage to hostiles",
                "12% less damage taken",
                "+10% movement speed",
                "+1.5 block / +1 entity reach",
                "+2 Luck and +4 max hearts",
                "Wardbound Masters take +25% more"
        } : new String[]{
                "PERMANENT WARD LAW",
                "+1 starting life on every ordinary ward",
                "+4 seconds to every minigame",
                "first meaningful error in each game is pardoned",
                "+25% ward loot, permanently"
        };
        g.drawCenteredString(font, title, cx, y + 76, signed ? 0xFF898389 : accent);
        int yy = y + 94;
        for (String text : lines) {
            for (var line : font.split(Component.literal(text), Math.max(60, w - 20))) {
                g.drawCenteredString(font, line, cx, yy, signed ? 0xFF706B70 : world ? 0xFFE5B7B2 : 0xFFE2D5AE);
                yy += 9;
            }
            yy += 1;
        }
        g.drawCenteredString(font, signed ? "LAW ALREADY STOLEN" : hot ? "SIGN THIS LAW" : "", cx, y + h - 20,
                signed ? 0xFF736C73 : accent);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void choose(int choice) {
        if (answered) return;
        if ((choice == 0 && msg.wardLawSigned()) || (choice == 1 && msg.worldLawSigned())) return;
        answered = true;
        Sfx.play(choice == 1 ? WardSounds.WARD_FAIL : WardSounds.EYE_CHOOSE, 0.68f, choice == 1 ? 0.64f : 0.92f);
        Wardbound.CHANNEL.sendToServer(new AceChoicePacket(choice));
        if (minecraft != null) minecraft.setScreen(null);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && !answered) {
            int y = topY(), w = cardW(), h = cardH();
            if (!msg.wardLawSigned() && inside(mx, my, leftX(), y, w, h)) { choose(0); return true; }
            if (!msg.worldLawSigned() && inside(mx, my, rightX(), y, w, h)) { choose(1); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public boolean isPauseScreen() { return false; }
}
