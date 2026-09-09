package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.GamblerChoicePacket;
import dev.marrowseal.wardbound.boss.GamblerForcedChoice;
import dev.marrowseal.wardbound.net.OpenGamblerChoicePacket;
import dev.marrowseal.wardbound.net.WardLodestoneFxPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * A deliberately non-dismissible boss mechanic. The client is presentation only:
 * the server owns the token, timeout and damage result, so replacing this screen
 * cannot cancel the attack.
 */
public final class GamblerChoiceScreen extends Screen {
    private static final ResourceLocation CUT = new ResourceLocation(Wardbound.MODID, "textures/gui/gambler_cut_card.png");
    private static final ResourceLocation COIN = new ResourceLocation(Wardbound.MODID, "textures/gui/gambler_coin_card.png");
    private static final ResourceLocation BLUNT = new ResourceLocation(Wardbound.MODID, "textures/gui/gambler_blunt_card.png");
    private static final int TEX_W = 128, TEX_H = 192;

    private final OpenGamblerChoicePacket msg;
    private boolean answered;
    private long openedAt;
    private final long openedNanos = System.nanoTime();
    private long lastFrame = System.nanoTime();
    private float leftLift, rightLift;
    private int lastHover = -1;
    private long selectionNanos;
    private int pendingChoice = -1;
    private boolean openFxPlayed;

    public GamblerChoiceScreen(OpenGamblerChoicePacket msg) {
        super(Component.literal("The Pale Gambler's Hand"));
        this.msg = msg;
    }

    public boolean matches(OpenGamblerChoicePacket other) {
        return other != null && msg.entityId() == other.entityId() && msg.token() == other.token();
    }

    @Override
    protected void init() {
        if (openedAt == 0L && minecraft != null && minecraft.level != null) openedAt = minecraft.level.getGameTime();
    }

    private int cardW() { return Math.min(128, Math.max(92, (width - 70) / 2)); }
    private int cardH() { return cardW() * 3 / 2; }
    private int gap() { return Math.max(18, Math.min(34, width / 18)); }
    private int leftX() { return width / 2 - gap() / 2 - cardW(); }
    private int rightX() { return width / 2 + gap() / 2; }
    private int topY() { return Math.max(54, height / 2 - cardH() / 2 + 4); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        g.fill(0, 0, width, height, 0x7607060A);
        double uiTime = (System.nanoTime() - openedNanos) / 1_000_000_000.0;
        if (!openFxPlayed) { openFxPlayed = true; lodestone(WardLodestoneFxPacket.CARD_HAND_OPEN, 0.82f); }
        CardUiVfx.ambient(g, width, height, uiTime, 0xFFE2BC5A, WardConfig.accessibilityReduceMotion ? 0.30f : 0.82f, WardConfig.accessibilityReduceMotion);
        long pulseTime = System.currentTimeMillis();
        int pulse = WardConfig.accessibilityReduceMotion ? 50 : 38 + (int)(Math.abs(Math.sin(pulseTime * 0.004)) * 42);
        g.fill(width / 2 - 126, 10, width / 2 + 126, 12, (pulse << 24) | 0x00D8B45E);
        g.fill(width / 2 - 92, 38, width / 2 + 92, 39, 0x553D2354);
        int y = topY();
        int w = cardW(), h = cardH();
        boolean leftHot = inside(mx, my, leftX(), y - Math.round(leftLift), w, h);
        boolean rightHot = inside(mx, my, rightX(), y - Math.round(rightLift), w, h);
        long frameNow = System.nanoTime();
        float dt = Math.min(0.05f, (frameNow - lastFrame) / 1_000_000_000f);
        lastFrame = frameNow;
        float speed = 12f;
        if (WardConfig.accessibilityReduceMotion) { leftLift = 0f; rightLift = 0f; }
        else {
            leftLift = Mth.lerp(Math.min(1f, dt * speed), leftLift, leftHot ? 9f : 0f);
            rightLift = Mth.lerp(Math.min(1f, dt * speed), rightLift, rightHot ? 9f : 0f);
        }
        int hoverNow = leftHot ? 0 : rightHot ? 1 : -1;
        if (hoverNow != lastHover && hoverNow >= 0) {
            Sfx.bargainHover(31L, false);
            lodestone(WardLodestoneFxPacket.CARD_HOVER, 0.34f);
        }
        lastHover = hoverNow;

        g.drawCenteredString(font, "THE HOUSE DEMANDS A CARD", width / 2, 17, 0xFFE5C46A);
        g.drawCenteredString(font, "Two cards are exposed. The third remains in the deck.", width / 2, 31, 0xFFC8BFB0);
        g.drawCenteredString(font, "Choose one consequence before the hand closes.", width / 2, 42, 0xFF9F93A7);

        int ly = y - Math.round(leftLift), ry = y - Math.round(rightLift);
        int leftAccent = titleColor(msg.leftCard()), rightAccent = titleColor(msg.rightCard());
        CardUiVfx.cardAura(g, leftX(), ly, w, h, uiTime, 101L + msg.leftCard(), leftAccent, leftHot ? 1f : leftLift / 9f, .66f, WardConfig.accessibilityReduceMotion);
        CardUiVfx.cardAura(g, rightX(), ry, w, h, uiTime, 211L + msg.rightCard(), rightAccent, rightHot ? 1f : rightLift / 9f, .66f, WardConfig.accessibilityReduceMotion);
        drawCard(g, msg.leftCard(), leftX(), ly, w, h, leftHot);
        drawCard(g, msg.rightCard(), rightX(), ry, w, h, rightHot);

        drawRiskBadge(g,msg.leftCard(),leftX(),ly,w);
        drawRiskBadge(g,msg.rightCard(),rightX(),ry,w);
        drawCenteredWrapped(g, GamblerForcedChoice.title(msg.leftCard()), leftX(), ly + h - 61, w, titleColor(msg.leftCard()));
        drawCenteredWrapped(g, GamblerForcedChoice.description(msg.leftCard()), leftX() + 7, ly + h - 43, w - 14, bodyColor(msg.leftCard()));
        drawCenteredWrapped(g, GamblerForcedChoice.title(msg.rightCard()), rightX(), ry + h - 61, w, titleColor(msg.rightCard()));
        drawCenteredWrapped(g, GamblerForcedChoice.description(msg.rightCard()), rightX() + 7, ry + h - 43, w - 14, bodyColor(msg.rightCard()));

        int remaining = msg.durationTicks();
        if (minecraft != null && minecraft.level != null && openedAt > 0L) {
            remaining = Math.max(0, msg.durationTicks() - (int)(minecraft.level.getGameTime() - openedAt));
        }
        String timer = String.format(java.util.Locale.ROOT, "%.1f s", remaining / 20.0);
        g.drawCenteredString(font, timer, width / 2, Math.min(height - 18, y + h + 10), remaining < 40 ? 0xFFFF6A6A : 0xFFB9AEA0);
        if (selectionNanos > 0L && pendingChoice >= 0) {
            float p = (float)((System.nanoTime() - selectionNanos) / 320_000_000.0);
            int cx = pendingChoice == 0 ? leftX() + w / 2 : rightX() + w / 2;
            int cy = pendingChoice == 0 ? ly + h / 2 : ry + h / 2;
            int accent = pendingChoice == 0 ? leftAccent : rightAccent;
            CardUiVfx.selectionBurst(g, cx, cy, uiTime, p, accent, true, WardConfig.accessibilityReduceMotion);
        }
        super.render(g, mx, my, partial);
    }

    private void drawCard(GuiGraphics g, int card, int x, int y, int w, int h, boolean hot) {
        ResourceLocation texture = card == GamblerForcedChoice.COINS_EDGE ? COIN : card == GamblerForcedChoice.BLUNT_HAND ? BLUNT : CUT;
        int glow = card == GamblerForcedChoice.BLUNT_HAND ? 0x99B06BE3 : card == GamblerForcedChoice.COINS_EDGE ? 0x99E2BC5A : 0x99D85A72;
        if (hot) {
            int hover = card == GamblerForcedChoice.BLUNT_HAND ? 0xA8C985FF : card == GamblerForcedChoice.COINS_EDGE ? 0xA8FFE398 : 0xA8FF879A;
            g.fill(x - 5, y - 5, x + w + 5, y + h + 5, hover);
            g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xCC171018);
        }
        g.blit(texture, x, y, 0, 0, w, h, TEX_W, TEX_H);
        g.fill(x, y, x + w, y + 2, hot ? 0xFFFFE398 : glow);
        g.fill(x, y + h - 2, x + w, y + h, hot ? 0xFFFFE398 : (card == GamblerForcedChoice.BLUNT_HAND ? 0x99552D67 : 0x994D3B26));
    }

    private void drawRiskBadge(GuiGraphics g,int card,int x,int y,int w){
        String badge = switch(card){
            case GamblerForcedChoice.THIRTY_CUT -> "LOSE 30% MAX HP";
            case GamblerForcedChoice.COINS_EDGE -> "50/50 // 0% OR 50%";
            case GamblerForcedChoice.BLUNT_HAND -> "20% + WEAKNESS IV";
            default -> "UNKNOWN DEBT";
        };
        int c = card==GamblerForcedChoice.BLUNT_HAND?0xFFD8A7FF:card==GamblerForcedChoice.COINS_EDGE?0xFFFFDEA0:0xFFFFA0AE;
        g.drawCenteredString(font,badge,x+w/2,y+8,c);
    }

    private static int titleColor(int card) {
        return card == GamblerForcedChoice.BLUNT_HAND ? 0xFFE2B8FF : card == GamblerForcedChoice.COINS_EDGE ? 0xFFFFE7A8 : 0xFFFFD7D7;
    }

    private static int bodyColor(int card) {
        return card == GamblerForcedChoice.BLUNT_HAND ? 0xFFD6C0E8 : card == GamblerForcedChoice.COINS_EDGE ? 0xFFE8D8A8 : 0xFFF2C3C3;
    }

    private void drawCenteredWrapped(GuiGraphics g, String text, int x, int y, int w, int color) {
        var lines = font.split(Component.literal(text), Math.max(20, w));
        int yy = y;
        for (var line : lines) {
            g.drawCenteredString(font, line, x + w / 2, yy, color);
            yy += 10;
        }
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void choose(int choice) {
        if (answered || choice < 0 || choice > 1) return;
        answered = true;
        pendingChoice = choice;
        selectionNanos = System.nanoTime();
        Sfx.play(WardSounds.GAMBLER_CARD_SLAM, 0.70f, choice == 0 ? 0.92f : 1.04f);
        lodestone(WardLodestoneFxPacket.CARD_SIGN, 1.08f);
        Wardbound.CHANNEL.sendToServer(new GamblerChoicePacket(msg.entityId(), msg.token(), choice));
    }

    @Override
    public void tick() {
        super.tick();
        if (selectionNanos <= 0L || System.nanoTime() - selectionNanos < 320_000_000L) return;
        selectionNanos = 0L;
        if (minecraft != null) minecraft.setScreen(null);
    }

    private void lodestone(int kind, float scale) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) return;
        var pos = minecraft.player.getEyePosition().add(minecraft.player.getLookAngle().scale(1.8));
        WardLodestoneFx.spawnLocal(pos, kind, scale);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && !answered) {
            int y = topY(), w = cardW(), h = cardH();
            if (inside(mx, my, leftX(), y - Math.round(leftLift), w, h)) { choose(0); return true; }
            if (inside(mx, my, rightX(), y - Math.round(rightLift), w, h)) { choose(1); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_1 || keyCode == GLFW.GLFW_KEY_KP_1) { choose(0); return true; }
        if (keyCode == GLFW.GLFW_KEY_2 || keyCode == GLFW.GLFW_KEY_KP_2) { choose(1); return true; }
        // ESC, inventory and all other screen-closing shortcuts are consumed.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_E) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void onClose() { /* server timeout owns refusal; do not close locally */ }
    @Override public boolean isPauseScreen() { return false; }
}
