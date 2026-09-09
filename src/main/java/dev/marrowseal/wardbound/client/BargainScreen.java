package dev.marrowseal.wardbound.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.ForbiddenBargain;
import dev.marrowseal.wardbound.AnomalyCardSystem;
import dev.marrowseal.wardbound.CardEvolution;
import dev.marrowseal.wardbound.ChestValuator;
import dev.marrowseal.wardbound.CardBranches;
import dev.marrowseal.wardbound.MasterSignature;
import dev.marrowseal.wardbound.Sealmakers;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.BargainChoicePacket;
import dev.marrowseal.wardbound.net.OpenBargainPacket;
import dev.marrowseal.wardbound.net.WardLodestoneFxPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * A small, deliberately non-container UI for the one choice the ward actually
 * gives the player. The chest's reward is already banked, so closing the screen
 * is always the safe answer and is treated exactly like choosing Refuse.
 */
@OnlyIn(Dist.CLIENT)
public final class BargainScreen extends Screen {

    private static final int CARD_W = 124;
    private static final int CARD_H = 184;
    private static final int GAP = 8;

    private final OpenBargainPacket msg;
    private final List<Card> cards = new ArrayList<>();
    private float[] lift;
    private boolean[] revealSoundPlayed;
    private int hover = -1;
    private boolean answered;
    private long lastFrame = System.nanoTime();
    private final long openedNanos = System.nanoTime();
    private long selectionNanos;
    private int selectedId = Integer.MIN_VALUE;
    private int pendingChoiceId = Integer.MIN_VALUE;
    private int pendingBranch;
    private boolean lodestoneOpenPlayed;
    private boolean redPenMode;
    private boolean mulliganMode;
    private final MasterSignature signature;
    private final String maker;
    private final boolean looseCard;

    private record Card(int id, String title, String body, float reward, ForbiddenBargain.Kind kind, int variant) {}

    private enum Motif {
        GENERIC, BLOOD, REACH, HARVEST, PROJECTILE, DISTANCE, CROWD, ARMOR, RITUAL, CONTRACT, COVENANT, REMEDY, CURSE, DEATH, DECK, TRAVEL, NIGHT, FIRE, FROST, KNOWLEDGE, ANOMALY
    }

    public BargainScreen(OpenBargainPacket msg) {
        super(Component.literal("A Forbidden Bargain"));
        this.msg = msg;
        this.signature = msg.makerSeed == 0L ? MasterSignature.VEILED
                : MasterSignature.ofSeed(msg.makerSeed);
        this.looseCard = msg.presenter != null && !msg.presenter.isBlank();
        this.maker = looseCard
                ? msg.presenter : (msg.makerSeed == 0L ? "Unsigned" : Sealmakers.house(msg.makerSeed));
        if (msg.makerSeed != 0L) Sfx.setHouseTone(Sealmakers.house(msg.makerSeed).hashCode());
        for (int i = 0; i < msg.offerIds.length; i++) {
            ForbiddenBargain b = ForbiddenBargain.byId(msg.offerIds[i]);
            if (b == null) continue;
            float reward = i < msg.rewardAdds.length ? msg.rewardAdds[i] : 0f;
            int variant = i < msg.variantLevels.length ? msg.variantLevels[i] : 0;
            boolean anomaly = AnomalyCardSystem.isAnomaly(b);
            cards.add(new Card(b.id,
                    anomaly ? AnomalyCardSystem.hiddenTitle(b) : CardEvolution.variantTitle(b, variant),
                    anomaly ? AnomalyCardSystem.hiddenBody(b) : CardEvolution.variantText(b, variant),
                    reward, b.kind, anomaly ? 0 : variant));
        }
        // Four-card hands use ESC as the refuse affordance so the cards stay on-screen.
        // Binding chest decks have no refuse path; field-earned hands always may be refused.
        if (!msg.forcedChoice && msg.offerIds.length < 4) {
            cards.add(new Card(-1,
                    looseCard ? "Refuse the Hand" : "Keep What Is Yours",
                    looseCard
                            ? "Leave both signatures unanswered. The sealed hand closes without taking anything."
                            : "Refuse every debt. The reward you already earned remains banked.",
                    0f, null, 0));
        }
        lift = new float[cards.size()];
        revealSoundPlayed = new boolean[cards.size()];
        if (msg.dealMode == 4) {
            Sfx.play(WardSounds.WARD_FAIL, 0.62f, 0.42f);
            Sfx.play(WardSounds.HEARTBEAT, 0.44f, 0.54f);
        }
        else if (msg.dealMode == 2) Sfx.play(WardSounds.WARD_FAIL, 0.52f, 0.58f);
        else if (msg.dealMode == 1) Sfx.play(WardSounds.MASTER_MOTIF, 0.62f, 0.78f);
        else if (msg.dealMode == 3) {
            Sfx.play(WardSounds.EYE_OPEN, 0.38f, 0.63f);
            Sfx.play(WardSounds.MARK, 0.24f, 1.31f);
        }
        else if (msg.dealMode == 5) {
            Sfx.play(WardSounds.MARK, 0.22f, 1.08f);
            Sfx.play(WardSounds.LEDGER_LOOT_AMBIENT, 0.08f, 0.94f);
        }
        else if (msg.dealMode == 6) {
            Sfx.play(WardSounds.EYE_HOVER, 0.16f, 1.12f);
            Sfx.play(WardSounds.LEDGER_GRIMOIRE_AMBIENT, 0.09f, 1.02f);
        }
        else if (msg.dealMode == 7) {
            Sfx.play(WardSounds.MARK_BAD, 0.24f, 0.72f);
            Sfx.play(WardSounds.LEDGER_CHRONICLE_AMBIENT, 0.10f, 0.86f);
        }
        else if (cards.stream().anyMatch(c -> c.kind == ForbiddenBargain.Kind.MASTER))
            Sfx.play(WardSounds.MASTER_MOTIF, 0.48f, 0.96f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int totalW() {
        return cards.size() * CARD_W + Math.max(0, cards.size() - 1) * GAP;
    }

    /** Four-card hands are scaled as a single tabletop so they never leave the screen at GUI scale 4. */
    private float handScale() {
        if (cards.isEmpty()) return 1f;
        float widthScale = (width - 18f) / Math.max(1f, totalW());
        float heightNeed = CARD_H + (looseCard ? 110f : 82f);
        float heightScale = (height - 12f) / Math.max(1f, heightNeed);
        return Math.min(1f, Math.max(0.62f, Math.min(widthScale, heightScale)));
    }

    private int virtualWidth() { return Math.max(1, Math.round(width / handScale())); }
    private int virtualHeight() { return Math.max(1, Math.round(height / handScale())); }

    private int cardX(int i) {
        return virtualWidth() / 2 - totalW() / 2 + i * (CARD_W + GAP);
    }

    private int baseY() {
        return virtualHeight() / 2 - CARD_H / 2 + 12;
    }

    private int cardAt(double mx, double my) {
        float scale = handScale();
        double logicalX = mx / scale;
        double logicalY = my / scale;
        for (int i = 0; i < cards.size(); i++) {
            int x = cardX(i);
            int y = baseY();
            if (logicalX >= x && logicalX < x + CARD_W && logicalY >= y - WardConfig.bargainCardHoverLift
                    && logicalY < y + CARD_H) return i;
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(0, 0, width, height, msg.dealMode == 4 ? 0xE0060205 : 0x9909060B);
        if (redPenMode) {
            g.fill(width / 2 - 150, 7, width / 2 + 150, 27, 0xD0550B13);
            g.drawCenteredString(font, "THE RED PEN // choose one other card to strike", width / 2, 13, 0xFFFFC7C7);
        } else if (mulliganMode) {
            g.fill(width / 2 - 170, 7, width / 2 + 170, 27, 0xD01B3440);
            g.drawCenteredString(font, "CUT THE HAND // choose one card to redraw", width / 2, 13, 0xFFC8EDF1);
        }
        if (!lodestoneOpenPlayed) { lodestoneOpenPlayed = true; playHandLodestone(false); }
        double uiTime = (System.nanoTime() - openedNanos) / 1_000_000_000.0;
        CardUiVfx.ambient(g, width, height, uiTime, signatureAccent(true),
                WardConfig.accessibilityReduceMotion ? 0.32f : WardConfig.accessibilityGuiAnimationIntensity,
                WardConfig.accessibilityReduceMotion);
        if (msg.dealMode == 4) {
            int edge = Math.max(18, Math.min(width, height) / 12);
            g.fill(0, 0, width, edge, 0xEE090005);
            g.fill(0, height - edge, width, height, 0xEE090005);
            g.fill(0, edge, edge, height - edge, 0xDD070004);
            g.fill(width - edge, edge, width, height - edge, 0xDD070004);
            g.fill(edge, edge, width - edge, edge + 1, 0x883B0912);
            g.fill(edge, height - edge - 1, width - edge, height - edge, 0x883B0912);
        }

        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        int newHover = cardAt(mouseX, mouseY);
        if (newHover != hover && newHover >= 0) {
            Sfx.bargainHover(msg.makerSeed, cards.get(newHover).id < 0);
            Card hoveredCard = cards.get(newHover);
            if (hoveredCard.id >= 0) playCardLodestone(hoveredCard, 0.30f);
        }
        hover = newHover;
        for (int i = 0; i < lift.length; i++) {
            float intensity = WardConfig.accessibilityReduceMotion ? 0f
                    : WardConfig.accessibilityGuiAnimationIntensity;
            float target = msg.dealMode == 4 ? 0f : (i == hover ? WardConfig.bargainCardHoverLift * intensity : 0f);
            if (WardConfig.accessibilityReduceMotion) lift[i] = 0f;
            else lift[i] = Mth.lerp(Math.min(1f, dt * WardConfig.bargainCardAnimSpeed), lift[i], target);
        }

        float handScale = handScale();
        int titleY = Math.max(14, Math.round(baseY() * handScale) - 60);
        boolean hasMasterOffer = cards.stream().anyMatch(c -> c.kind == ForbiddenBargain.Kind.MASTER);
        boolean hasAnomalyOffer = cards.stream().anyMatch(this::isAnomaly);
        String heading = msg.dealMode == 4 ? "THE DEATH HAND OPENS"
                : msg.dealMode == 2 ? "THE CURSE HAND CLOSES"
                : msg.dealMode == 1 ? "ONE CARD IS SET APART"
                : msg.dealMode == 3 ? "A LAW WITH NO MAKER"
                : msg.dealMode == 5 ? "THE CONTRACT HAND OPENS"
                : msg.dealMode == 6 ? "THE RITUAL HAND IS WRITTEN"
                : msg.dealMode == 7 ? "A COVENANT ASKS ITS PRICE"
                : hasMasterOffer ? "A PRIVATE LAW ENTERS THE HAND"
                : looseCard ? "A SEALED HAND IS OPENED" : "THE HAND DEALS AGAIN";
        int headingColor = msg.dealMode == 4 ? 0xFF8C1F2D : msg.dealMode == 2 ? 0xFFE26B70 : msg.dealMode == 1 ? 0xFFF1D47A
                : msg.dealMode == 3 ? 0xFF9FE7D9 : msg.dealMode == 5 ? 0xFF87B9D6
                : msg.dealMode == 6 ? 0xFF72B8A7 : msg.dealMode == 7 ? 0xFFB75F65 : signatureAccent(true);
        g.drawCenteredString(font, heading, width / 2, titleY, headingColor);
        String dealerLine = msg.dealMode == 4 ? "NO MAKER · NO WITNESS"
                : looseCard
                ? ("???".equals(maker) ? "??? · UNKNOWN HAND" : maker + " · CARD MASTER")
                : maker + " · " + signature.label().toUpperCase(java.util.Locale.ROOT);
        g.drawCenteredString(font, dealerLine, width / 2, titleY + 12, signatureAccent(false));
        String terms = msg.forcedChoice
                ? (msg.dealMode == 4 ? "One death must be signed. The hand has already counted you." : "One curse must be chosen. The hand will not close empty.")
                : msg.dealMode == 5 ? "Choose one contract. Payment waits until the count is complete."
                : msg.dealMode == 6 ? "Choose one ritual. The book will count the work you perform."
                : msg.dealMode == 7 ? "Choose one covenant, or walk away before the requirement is yours."
                : looseCard ? (msg.offerIds.length + " cards were sealed together. Choose one, or refuse the hand.")
                : String.format("Loot x%.2f is already yours. Choose one card, or owe nothing.", msg.bankedLoot);
        g.drawCenteredString(font, terms, width / 2, titleY + 24, msg.forcedChoice ? 0xFFD88587 : 0xFF9E8A76);
        g.drawCenteredString(font, msg.dealMode == 4 ? "The paper is cold enough to numb the fingers."
                : msg.dealMode == 2 ? "There is no clean margin left on this page."
                : msg.dealMode == 3 ? "No workshop mark appears beneath it."
                : msg.dealMode == 5 ? "The ink does not care how long the work takes."
                : msg.dealMode == 6 ? "The requirement is the ritual; the reward is only the answer."
                : msg.dealMode == 7 ? "The reward is generous because the request should trouble you."
                : hasAnomalyOffer ? "One extra card carries no signature belonging to this hand."
                : looseCard && "???".equals(maker) ? "The signature has been scraped away before it reached you."
                : signatureWhisper(),
                width / 2, titleY + 36, signatureAccent(false));

        g.pose().pushPose();
        g.pose().scale(handScale, handScale, 1f);
        for (int i = 0; i < cards.size(); i++) drawCard(g, i);
        g.pose().popPose();
        if (selectionNanos > 0L) {
            float progress = (float)((System.nanoTime() - selectionNanos) / 420_000_000.0);
            int idx = indexOfCard(selectedId);
            if (idx >= 0) {
                Card selected = cards.get(idx);
                int cx = Math.round((cardX(idx) + CARD_W / 2f) * handScale);
                int cy = Math.round((baseY() - lift[idx] + CARD_H / 2f) * handScale);
                int color = selected.id < 0 ? 0xFFB8AFA4 : isAnomaly(selected) ? 0xFFD7A2FF : kindAccent(selected.kind, true);
                boolean dangerous = selected.kind == ForbiddenBargain.Kind.CURSE || selected.kind == ForbiddenBargain.Kind.DEATH || isAnomaly(selected);
                CardUiVfx.selectionBurst(g, cx, cy, uiTime, progress, color, dangerous, WardConfig.accessibilityReduceMotion);
            }
        }
        int footerY = Math.round((baseY() + CARD_H) * handScale) + (looseCard ? 10 : 24);
        String footer = msg.dealMode == 4 ? "ONE NAME MUST REMAIN ON THE PAGE"
                : msg.forcedChoice ? "THE DECK WILL NOT BE REFUSED"
                : looseCard ? "CHOOSE ONE CARD, OR REFUSE THE HAND." : "ESC refuses the bargain";
        g.drawCenteredString(font, footer, width / 2, footerY,
                msg.forcedChoice ? 0xFFB84E55 : 0xFF6E6270);
        if (canMulligan()) drawMulliganButton(g, mouseX, mouseY, mulliganButtonY(footerY));

        super.render(g, mouseX, mouseY, partialTick);
    }

    private boolean isAnomaly(Card card) {
        return card != null && card.id >= 0 && AnomalyCardSystem.isAnomaly(ForbiddenBargain.byId(card.id));
    }

    private void drawCard(GuiGraphics g, int i) {
        Card c = cards.get(i);
        int x = cardX(i);
        int y = baseY() - Math.round(lift[i]);
        boolean hot = i == hover;
        boolean refuse = c.id < 0;
        boolean anomaly = isAnomaly(c);
        Motif motif = classifyMotif(c, anomaly);
        int accent = refuse ? (hot ? 0xFFC5BDB2 : 0xFF77706A) : anomaly ? (hot ? 0xFFE2A8FF : 0xFF9A5ED0) : kindAccent(c.kind, hot);
        double localTime = (System.nanoTime() - openedNanos) / 1_000_000_000.0;
        float rarity = rarityStrength(c, anomaly);
        float revealDelay = i * 0.070f + (!refuse ? rarity * 0.055f : 0f);
        float revealDuration = refuse ? 0.28f : 0.30f + rarity * 0.24f;
        float enter = WardConfig.accessibilityReduceMotion ? 1f
                : CardUiVfx.smooth01((float)((localTime - revealDelay) / revealDuration));
        y += Math.round((1f - enter) * (34f + rarity * 16f + i * 3f));
        if (!refuse && enter >= 0.52f && !revealSoundPlayed[i]) {
            revealSoundPlayed[i] = true;
            if (!WardConfig.accessibilityReduceMotion || i == 0) playRevealAccent(c, rarity, anomaly);
        }
        if (!WardConfig.accessibilityReduceMotion && !refuse && (c.kind == ForbiddenBargain.Kind.CURSE || c.kind == ForbiddenBargain.Kind.DEATH)) {
            long shake = System.currentTimeMillis() / (hot ? 38L : 72L) + c.id * 5L + i * 17L;
            x += Math.floorMod((int) shake, hot ? 3 : 2) - (hot ? 1 : 0);
            y += Math.floorMod((int) (shake / 2L), hot ? 3 : 2) - (hot ? 1 : 0);
        }

        float hoverAmount = hot ? 1f : (WardConfig.bargainCardHoverLift <= 0.01f ? 0f : Mth.clamp(lift[i] / WardConfig.bargainCardHoverLift, 0f, 1f));
        CardUiVfx.cardAura(g, x, y, CARD_W, CARD_H, localTime, c.id * 97L + i * 31L,
                accent, hoverAmount, rarity, WardConfig.accessibilityReduceMotion);
        if (!refuse) {
            long pulse = msg.dealMode == 4 ? (c.id * 17L + i * 11L) : System.currentTimeMillis() / 90L + i * 11L;
            if (anomaly) {
                long t = System.currentTimeMillis() / (WardConfig.accessibilityReduceMotion ? 240L : 42L) + c.id * 37L;
                int breathe = 3 + (int)Math.abs((t / 4L) % 9L - 4L);
                g.fill(x - breathe - 5, y - breathe - 5, x + CARD_W + breathe + 5, y + CARD_H + breathe + 5,
                        hot ? 0x553A0D62 : 0x3321083C);
                int cx = x + CARD_W / 2, cy = y + CARD_H / 2;
                int particles = WardConfig.accessibilityReduceMotion ? 6 : 14;
                for (int n = 0; n < particles; n++) {
                    double a = (t * 0.045) + n * (Math.PI * 2.0 / particles);
                    double wobble = Math.sin((t + n * 19) * 0.07) * 5.0;
                    int rx = 76 + (n % 3) * 4;
                    int ry = 101 + (n % 2) * 4;
                    int px = cx + (int)(Math.cos(a) * rx);
                    int py = cy + (int)(Math.sin(a) * ry + wobble);
                    int col = (n % 3 == 0) ? 0xAAE0B4FF : (n % 3 == 1) ? 0xAA73D7D0 : 0xAA9D67D8;
                    int sz = hot && n % 4 == 0 ? 3 : 2;
                    g.fill(px, py, px + sz, py + sz, col);
                }
                for (int n = 0; n < 5; n++) {
                    int gy = y + 36 + Math.floorMod((int)(t * (n + 2) + c.id * 11L), CARD_H - 72);
                    int gx = x + 10 + Math.floorMod((int)(t * (n + 5) + c.id * 23L), CARD_W - 20);
                    g.fill(gx, gy, gx + 1 + (n & 1), gy + 5, 0x669A6BC3);
                }
            } else if (c.kind == ForbiddenBargain.Kind.DEATH) {
                int breathe = 3 + (int) Math.abs(pulse % 7L - 3L);
                int aura = hot ? 0x665B0713 : 0x4426040B;
                g.fill(x - breathe - 4, y - breathe - 4, x + CARD_W + breathe + 4, y + CARD_H + breathe + 4, aura);
                for (int n = 0; n < 5; n++) {
                    int ox = Math.floorMod((int) (pulse * (n + 3) + c.id * 17L), CARD_W + 24) - 12;
                    int oy = Math.floorMod((int) (pulse * (n + 5) + c.id * 23L), CARD_H + 18) - 9;
                    g.fill(x + ox, y + oy, x + ox + 2, y + oy + 5, 0x552A0008);
                }
            } else if (c.kind == ForbiddenBargain.Kind.EPIC || c.kind == ForbiddenBargain.Kind.MASTER
                    || c.kind == ForbiddenBargain.Kind.CURSE || c.kind == ForbiddenBargain.Kind.UNIQUE
                    || c.kind == ForbiddenBargain.Kind.COVENANT) {
                int aura = c.kind == ForbiddenBargain.Kind.EPIC ? (hot ? 0x44F0D67A : 0x22C6A758)
                        : c.kind == ForbiddenBargain.Kind.MASTER ? (hot ? 0x446CC1D8 : 0x223E8098)
                        : c.kind == ForbiddenBargain.Kind.CURSE ? (hot ? 0x446E1024 : 0x22360714)
                        : c.kind == ForbiddenBargain.Kind.COVENANT ? (hot ? 0x55530D19 : 0x2A2A0710)
                        : (hot ? 0x44679BDC : 0x223B5C90);
                int breathe = 1 + (int) Math.abs(pulse % 5L - 2L);
                g.fill(x - breathe - 2, y - breathe - 2, x + CARD_W + breathe + 2, y + CARD_H + breathe + 2, aura);
                for (int n = 0; n < 3; n++) {
                    int ox = Math.floorMod((int) (pulse * (n + 4) + c.id * 19L), CARD_W + 14) - 7;
                    int oy = Math.floorMod((int) (pulse * (n + 6) + c.id * 29L), CARD_H + 10) - 5;
                    int spark = c.kind == ForbiddenBargain.Kind.CURSE ? 0x66B33A54
                            : c.kind == ForbiddenBargain.Kind.COVENANT ? 0x667A2637
                            : c.kind == ForbiddenBargain.Kind.EPIC ? 0x66E6CF8C
                            : c.kind == ForbiddenBargain.Kind.MASTER ? 0x6690D5E6 : 0x668DB7F0;
                    g.fill(x + ox, y + oy, x + ox + 2, y + oy + 2, spark);
                }
            }
        }

        // Deep offset shadow + a second thin echo makes the card read like an
        // object sitting above the ward rather than a flat GUI rectangle.
        int shadow = hot ? 9 : 6;
        g.fill(x + shadow, y + shadow, x + CARD_W + shadow, y + CARD_H + shadow, 0x77000000);
        g.fill(x + 3, y + 4, x + CARD_W + 5, y + CARD_H + 6, 0x33000000);

        g.fill(x - 2, y - 2, x + CARD_W + 2, y + CARD_H + 2,
                refuse ? (hot ? 0xFF9A9187 : 0xFF5D5652) : cardEdge(hot));
        g.fill(x, y, x + CARD_W, y + CARD_H, refuse ? 0xFF17171B : cardOuter());
        g.fill(x + 3, y + 3, x + CARD_W - 3, y + CARD_H - 3, refuse ? 0xFF201F22 : cardInner());
        if (!refuse) drawMotifWash(g, x + 10, y + 27, CARD_W - 20, CARD_H - 56, motif, accent, hot, c.id);

        if (!refuse && c.variant > 0 && c.kind != ForbiddenBargain.Kind.MASTER) {
            // Revisions are physical rewrites, not just a subtitle. Each stage
            // adds another visible layer of ink; Palimpsest looks overwritten
            // enough that the original card is only partly recoverable.
            int revInk = c.variant >= CardEvolution.MAX_REVISION ? 0xFFE4C47A
                    : c.variant == 2 ? 0xFFC7B4E8 : 0xFF91C6B8;
            int inset = c.variant >= CardEvolution.MAX_REVISION ? 4 : 5;
            g.fill(x + inset, y + inset, x + CARD_W - inset, y + inset + 1, revInk);
            g.fill(x + inset, y + CARD_H - inset - 1, x + CARD_W - inset, y + CARD_H - inset, revInk);
            if (c.variant >= 2) {
                g.fill(x + 5, y + 34, x + 7, y + CARD_H - 42, (revInk & 0x00FFFFFF) | 0x88000000);
                g.fill(x + CARD_W - 7, y + 34, x + CARD_W - 5, y + CARD_H - 42, (revInk & 0x00FFFFFF) | 0x88000000);
                for (int n = 0; n < 4; n++) {
                    int yy = y + 94 + n * 18 + Math.floorMod(c.id * 5 + n * 7, 5);
                    int ww = 24 + Math.floorMod(c.id * 13 + n * 11, CARD_W - 64);
                    g.fill(x + 24, yy, x + 24 + ww, yy + 1, (revInk & 0x00FFFFFF) | 0x44000000);
                }
            }
            if (c.variant >= CardEvolution.MAX_REVISION) {
                long ghost = System.currentTimeMillis() / 150L + c.id * 19L;
                int gx = x + 18 + Math.floorMod((int) ghost, Math.max(1, CARD_W - 40));
                g.fill(gx, y + 82, gx + 1, y + CARD_H - 47, 0x77E8D18A);
                for (int n = 0; n < 5; n++) {
                    int yy = y + 91 + n * 20;
                    int drift = Math.floorMod((int) (ghost + n * 17L), 9) - 4;
                    g.fill(x + 19 + drift, yy, x + CARD_W - 19 + drift, yy + 1, 0x44584430);
                }
            }
        }

        if (!refuse && c.kind == ForbiddenBargain.Kind.MASTER) {
            // Master cards are not merely rarer cards: they look like a separate
            // private contract laid on top of the hand.
            g.fill(x - 4, y + 18, x - 2, y + CARD_H - 18, accent);
            g.fill(x + CARD_W + 2, y + 18, x + CARD_W + 4, y + CARD_H - 18, accent);
            g.fill(x + 18, y - 4, x + CARD_W - 18, y - 2, signatureAccent(true));
            g.fill(x + 18, y + CARD_H + 2, x + CARD_W - 18, y + CARD_H + 4, signatureAccent(false));
        }

        drawFrameForm(g, x, y, c, accent, hot, anomaly, refuse, motif);

        // Category ribbon. Remedies deliberately read differently from debts so
        // the player can recognize that a rare card is a way back out.
        g.fill(x + 11, y + 11, x + CARD_W - 11, y + 24,
                refuse ? 0xFF343238 : anomaly ? (hot ? 0xFF56316F : 0xFF382347) : kindBand(c.kind, hot));
        String label = refuse ? "REFUSE" : anomaly ? "ANOMALY // ???" : switch (c.kind) {
            case CONTRACT -> "CONTRACT";
            case RITUAL -> "RITUAL";
            case COVENANT -> "COVENANT";
            case MASTER -> "MASTER'S HAND";
            case EPIC -> "EPIC";
            case UNIQUE -> "UNIQUE";
            case CURSE -> "CURSE";
            case DEATH -> "DEATH";
            case REFRESH -> "REDRAW";
            default -> c.kind.name();
        };
        g.drawCenteredString(font, label, x + CARD_W / 2, y + 14,
                refuse ? 0xFFD8D1C8 : accent);
        if (!refuse) {
            String tier = presentationTier(c, anomaly);
            if (!tier.isEmpty()) {
                int tw = font.width(tier) + 8;
                int tx = x + CARD_W - tw - 5;
                g.fill(tx, y + 5, tx + tw, y + 15, CardUiVfx.alpha(accent, hot ? 72 : 46));
                g.drawString(font, tier, tx + 4, y + 6, hot ? 0xFFF8E9CE : 0xFFD0C1AE, false);
            }
        }
        if (!refuse) drawCornerMotifs(g, x, y, motif, accent, hot, c.id);
        if (hot && !refuse) drawHoverFiligree(g, x, y, motif, accent, c.id);

        // Workshop notches and a vertical ink spine make cards from one hand
        // related without making every card graphically identical.
        if (!refuse) {
            int hh = maker.hashCode();
            for (int n = 0; n < 4; n++) {
                int nx = x + 14 + Math.floorMod(hh >>> (n * 5), CARD_W - 28);
                int h = 3 + Math.floorMod(c.id + n, 5);
                g.fill(nx, y + 29, nx + 1, y + 29 + h, signatureAccent(false));
            }
            g.fill(x + 15, y + 31, x + 16, y + CARD_H - 36, 0x442E2630);
            for (int n = 0; n < 5; n++) {
                int ry = y + 40 + n * 21 + Math.floorMod(c.id * 7 + n * 3, 7);
                int rw = 2 + Math.floorMod(c.id + n, 5);
                g.fill(x + 13, ry, x + 13 + rw, ry + 1, accent);
            }
        }

        int sigil = refuse ? 0xFF7E7770 : accent;
        if (anomaly) {
            int cx = x + CARD_W / 2, cy = y + 58;
            long glyph = System.currentTimeMillis() / (WardConfig.accessibilityReduceMotion ? 300L : 70L);
            int drift = Math.floorMod((int)(glyph + c.id), 7) - 3;
            g.fill(cx - 1, cy - 13, cx + 1, cy + 13, 0xFFB98ADF);
            g.fill(cx - 13, cy - 1, cx + 13, cy + 1, 0xFF75D3CD);
            g.fill(cx - 9 + drift, cy - 9, cx - 7 + drift, cy + 9, 0x99C997F1);
            g.fill(cx + 6 - drift, cy - 8, cx + 8 - drift, cy + 8, 0x8873D7D0);
            g.fill(cx - 20 + drift, cy - 4, cx + 20 + drift, cy - 3, 0x669A6BC3);
        } else {
            drawSigil(g, x + CARD_W / 2, y + 58, sigil, c.id);
        }
        // Small halo around the central seal.
        g.fill(x + CARD_W / 2 - 16, y + 73, x + CARD_W / 2 + 16, y + 74,
                refuse ? 0x334D4A48 : (accent & 0x00FFFFFF) | 0x55000000);
        if (!refuse) {
            drawMotifGlyph(g, x + CARD_W / 2, y + 79, motif, accent, hot, c.id);
            drawMajorMotifIcon(g, x + CARD_W / 2, y + 112, motif, accent, hot, c.id);
        }

        List<FormattedCharSequence> titleLines = font.split(Component.literal(c.title), CARD_W - 20);
        int titleShown = Math.min(2, titleLines.size());
        int titleColor = refuse ? (hot ? 0xFFF2E9DE : 0xFFBDB4AB) : anomaly ? (hot ? 0xFFF1D7FF : 0xFFD0B0E8) : signatureText(hot);
        for (int line = 0; line < titleShown; line++) {
            FormattedCharSequence seq = titleLines.get(line);
            g.drawString(font, seq, x + CARD_W / 2 - font.width(seq) / 2, y + 29 + line * 9, titleColor, false);
        }
        if (!refuse && c.variant > 0 && c.kind != ForbiddenBargain.Kind.MASTER) {
            String revisionLabel = c.variant >= CardEvolution.MAX_REVISION ? "PALIMPSEST"
                    : c.variant == 2 ? "REVISION II" : "REVISION I";
            g.drawCenteredString(font, revisionLabel, x + CARD_W / 2, y + 76,
                    hot ? 0xFFF0D8A2 : 0xFFC7B18C);
        }
        if (!refuse && c.kind == ForbiddenBargain.Kind.MASTER) {
            g.drawCenteredString(font, maker + " writes this law for you", x + CARD_W / 2, y + 76,
                    hot ? 0xFFE7CFA1 : 0xFF9D8A70);
        }

        int ty = y + 87;
        int textW = CARD_W - 28;
        List<FormattedCharSequence> lines = font.split(Component.literal(c.body), textW);
        int shown = Math.min(lines.size(), 6);
        for (int line = 0; line < shown; line++) {
            int textColor = refuse ? 0xFFAAA4A0 : anomaly ? 0xFFD9C9E8 : switch (c.kind) {
                case REMEDY -> 0xFFC1D6C8;
                case CONTRACT -> 0xFFB9D3E3;
                case RITUAL -> 0xFFAED6C9;
                case COVENANT -> 0xFFE0B0B2;
                case EPIC -> 0xFFE6D69B;
                case UNIQUE -> 0xFFBDE8E1;
                case CURSE -> 0xFFD8A0A2;
                case DEATH -> 0xFFE2A4AC;
                case MASTER -> 0xFFD6C1A6;
                case REFRESH -> 0xFFB8B0D0;
                default -> 0xFFB8A9A4;
            };
            g.drawString(font, lines.get(line), x + 18, ty + line * 10, textColor, false);
        }

        // Bottom ledger band separates what changes the current chest from what
        // the card will do later.
        g.fill(x + 11, y + CARD_H - 34, x + CARD_W - 11, y + CARD_H - 18,
                refuse ? 0xFF2B2A2E : 0x55110D12);
        String reward;
        if (refuse) reward = "NO DEBT";
        else if (anomaly) reward = "UNWRITTEN OUTCOME";
        else if (Math.abs(c.reward) < 0.0001f) reward = switch (c.kind) {
            case REMEDY -> "RESTORE";
            case CONTRACT -> "COMPLETE THE COUNT";
            case RITUAL -> "COMPLETE THE RITUAL";
            case COVENANT -> "FULFIL THE COVENANT";
            case CURSE -> "A WOUND WITH A GIFT";
            case DEATH -> "A DEATH FOR A LAW";
            case REFRESH -> "BURN & REDRAW";
            case EPIC -> "WORLD LAW";
            case UNIQUE -> "UNIQUE LAW";
            case MASTER -> "PRIVATE LAW";
            default -> "NO IMMEDIATE LOOT";
        };
        else reward = String.format("+%.2f LOOT  ·  x%.2f", c.reward, msg.bankedLoot + c.reward);
        g.drawCenteredString(font, reward, x + CARD_W / 2, y + CARD_H - 30,
                refuse ? 0xFF8D9388 : accent);
        if (hot) {
            g.drawCenteredString(font, refuse ? "CLICK TO WALK AWAY" : anomaly ? "SIGN WITHOUT READING" : "CLICK TO SIGN",
                    x + CARD_W / 2, y + CARD_H - 14,
                    refuse ? 0xFFA7A29A : anomaly ? 0xFFD8B5F4 : 0xFF84C9B7);
        }
        if (selectionNanos > 0L && c.id != selectedId) {
            float p = CardUiVfx.smooth01((float)((System.nanoTime() - selectionNanos) / 420_000_000.0));
            g.fill(x - 3, y - 3, x + CARD_W + 3, y + CARD_H + 3, CardUiVfx.alpha(0xFF050407, Math.round(150 * p)));
        }
        CardUiVfx.revealSweep(g, x, y, CARD_W, CARD_H, enter, accent, rarity,
                anomaly || (!refuse && (c.kind == ForbiddenBargain.Kind.CURSE || c.kind == ForbiddenBargain.Kind.DEATH || c.kind == ForbiddenBargain.Kind.COVENANT)),
                WardConfig.accessibilityReduceMotion);
    }

    private void playRevealAccent(Card c, float rarity, boolean anomaly) {
        if (c == null || c.id < 0) return;
        float pitch = 1.08f - rarity * 0.28f;
        float volume = 0.18f + rarity * 0.22f;
        Sfx.play(WardSounds.GAMBLER_CARD_FLIP, volume, pitch);
        if (anomaly) {
            Sfx.play(WardSounds.EYE_OPEN, 0.22f, 0.64f);
            return;
        }
        if (c.kind == ForbiddenBargain.Kind.DEATH) {
            Sfx.play(WardSounds.HEARTBEAT, 0.20f, 0.70f);
        } else if (c.kind == ForbiddenBargain.Kind.MASTER) {
            Sfx.play(WardSounds.MASTER_MOTIF, 0.20f, 1.04f);
        } else if (c.kind == ForbiddenBargain.Kind.EPIC || c.kind == ForbiddenBargain.Kind.UNIQUE) {
            Sfx.play(WardSounds.MARK, 0.15f, 1.28f);
        } else if (c.kind == ForbiddenBargain.Kind.CURSE || c.kind == ForbiddenBargain.Kind.COVENANT) {
            Sfx.play(WardSounds.MARK_BAD, 0.15f, 0.84f);
        }
    }

    private String presentationTier(Card c, boolean anomaly) {
        if (c == null || c.id < 0) return "";
        if (anomaly) return "ANOMALY";
        return switch (c.kind) {
            case DEATH -> "BLACK LAW";
            case MASTER -> "PRIVATE";
            case UNIQUE -> "SINGULAR";
            case EPIC -> "HIGH LAW";
            case CURSE, COVENANT -> "HOSTILE";
            case RITUAL, CONTRACT -> "BOUND";
            default -> "";
        };
    }

    private float rarityStrength(Card c, boolean anomaly) {
        if (c == null || c.id < 0) return 0f;
        if (anomaly) return 1.0f;
        return switch (c.kind) {
            case DEATH -> 1.0f;
            case MASTER, UNIQUE -> 0.86f;
            case EPIC, CURSE, COVENANT -> 0.72f;
            case RITUAL, CONTRACT -> 0.54f;
            case REMEDY, SCAR -> 0.36f;
            case WAGER, DEBT -> 0.26f;
            case REFRESH -> 0.42f;
        };
    }

    private int indexOfCard(int id) {
        for (int i = 0; i < cards.size(); i++) if (cards.get(i).id == id) return i;
        return -1;
    }

    private int lodestoneKind(Card c) {
        if (c == null || c.id < 0) return WardLodestoneFxPacket.BARGAIN_REFRESH;
        if (isAnomaly(c)) return WardLodestoneFxPacket.BARGAIN_UNIQUE;
        return switch (c.kind) {
            case DEBT -> WardLodestoneFxPacket.BARGAIN_DEBT;
            case WAGER -> WardLodestoneFxPacket.BARGAIN_WAGER;
            case SCAR -> WardLodestoneFxPacket.BARGAIN_SCAR;
            case REMEDY -> WardLodestoneFxPacket.REMEDY;
            case CONTRACT -> WardLodestoneFxPacket.CONTRACT;
            case RITUAL -> WardLodestoneFxPacket.BARGAIN_RITUAL;
            case COVENANT -> WardLodestoneFxPacket.BARGAIN_COVENANT;
            case MASTER -> WardLodestoneFxPacket.BARGAIN_MASTER;
            case EPIC -> WardLodestoneFxPacket.BARGAIN_EPIC;
            case UNIQUE -> WardLodestoneFxPacket.BARGAIN_UNIQUE;
            case CURSE -> WardLodestoneFxPacket.BARGAIN_CURSE;
            case DEATH -> WardLodestoneFxPacket.BARGAIN_DEATH;
            case REFRESH -> WardLodestoneFxPacket.BARGAIN_REFRESH;
        };
    }

    private void playCardLodestone(Card c, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || c == null) return;
        var pos = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(1.8));
        int kind = scale < 0.5f ? WardLodestoneFxPacket.CARD_HOVER : WardLodestoneFxPacket.CARD_SIGN;
        WardLodestoneFx.spawnLocal(pos, kind, scale);
    }

    private void playHandLodestone(boolean strong) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var pos = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(1.8));
        WardLodestoneFx.spawnLocal(pos, WardLodestoneFxPacket.CARD_HAND_OPEN, strong ? 1.15f : 0.72f);
    }

    private int kindAccent(ForbiddenBargain.Kind kind, boolean hot) {
        if (WardConfig.accessibilityHighContrast) return hot ? 0xFFFFFFFF : 0xFFE7D79A;
        return switch (kind) {
            case DEBT -> hot ? 0xFFD5A3E6 : 0xFF9D6BAF;
            case WAGER -> hot ? 0xFFE5C976 : 0xFFAA8E4C;
            case SCAR -> hot ? 0xFFE8898F : 0xFFA85862;
            case REMEDY -> hot ? 0xFF9FE0BF : 0xFF5F9E7E;
            case CONTRACT -> hot ? 0xFFB9E6FF : 0xFF6D9DB9;
            case RITUAL -> hot ? 0xFFA9E9D5 : 0xFF5D9F8C;
            case COVENANT -> hot ? 0xFFF09AA0 : 0xFF9F4E56;
            case MASTER -> hot ? 0xFFF0D29A : 0xFFB68C58;
            case EPIC -> hot ? 0xFFFFE48E : 0xFFD0A94F;
            case UNIQUE -> hot ? 0xFFB9FFF1 : 0xFF67B8A8;
            case CURSE -> hot ? 0xFFFF777D : 0xFFB8434B;
            case DEATH -> hot ? 0xFFFF495B : 0xFF6E1725;
            case REFRESH -> hot ? 0xFFC4AFF0 : 0xFF78649E;
        };
    }

    private int kindBand(ForbiddenBargain.Kind kind, boolean hot) {
        return switch (kind) {
            case DEBT -> hot ? 0xFF4C3155 : 0xFF38263F;
            case WAGER -> hot ? 0xFF51452B : 0xFF3A3323;
            case SCAR -> hot ? 0xFF573039 : 0xFF40252C;
            case REMEDY -> hot ? 0xFF2F4D3E : 0xFF243A31;
            case CONTRACT -> hot ? 0xFF35566A : 0xFF263F4E;
            case RITUAL -> hot ? 0xFF315A4E : 0xFF24443B;
            case COVENANT -> hot ? 0xFF612E34 : 0xFF452328;
            case MASTER -> hot ? 0xFF5E482F : 0xFF493724;
            case EPIC -> hot ? 0xFF615224 : 0xFF493D1F;
            case UNIQUE -> hot ? 0xFF2E5D56 : 0xFF244640;
            case CURSE -> hot ? 0xFF69272D : 0xFF481C21;
            case DEATH -> hot ? 0xFF390710 : 0xFF190207;
            case REFRESH -> hot ? 0xFF493B62 : 0xFF352D48;
        };
    }

    private void drawSigil(GuiGraphics g, int cx, int cy, int color, int id) {
        int r = 12;
        int skew = Math.floorMod(id * 3 + maker.hashCode() + 5, 7) - 3;
        switch (signature) {
            case CROOKED -> {
                g.fill(cx - 1, cy - r, cx + 1, cy + r, color);
                g.fill(cx - r, cy + skew - 1, cx + r, cy + skew + 1, color);
                g.fill(cx - 9, cy - 8 + skew, cx - 6, cy + 8 + skew, color);
                g.fill(cx + 5, cy - 6 - skew, cx + 8, cy + 10 - skew, color);
            }
            case VEILED -> {
                g.fill(cx - 1, cy - r, cx + 1, cy + r, color);
                g.fill(cx - r, cy - 1, cx + r, cy + 1, color);
                g.fill(cx - 8, cy - 8, cx + 8, cy - 6, color);
                g.fill(cx - 8, cy + 6, cx + 8, cy + 8, color);
                g.fill(cx - 4, cy - 4, cx + 4, cy + 4, 0xFF1B121B);
            }
            case EXACTING -> {
                g.fill(cx - 1, cy - r, cx + 1, cy + r, color);
                g.fill(cx - r, cy - 1, cx + r, cy + 1, color);
                g.fill(cx - 8, cy - 8, cx - 6, cy + 8, color);
                g.fill(cx + 6, cy - 8, cx + 8, cy + 8, color);
                g.fill(cx - 5, cy - 5, cx + 5, cy - 3, color);
                g.fill(cx - 5, cy + 3, cx + 5, cy + 5, color);
            }
        }
    }

    private String signatureWhisper() {
        return switch (signature) {
            case CROOKED -> "The margins lean toward a price not yet paid.";
            case VEILED -> "There is more ink beneath the ink you can see.";
            case EXACTING -> "Every debt has been measured before it was offered.";
        };
    }

    private int signatureAccent(boolean hot) {
        if (WardConfig.accessibilityHighContrast) return hot ? 0xFFFFFFFF : 0xFFE7D79A;
        return switch (signature) {
            case CROOKED -> hot ? 0xFFD2C56F : 0xFF8FA36E;
            case VEILED -> hot ? 0xFFD6A8E2 : 0xFF9B718E;
            case EXACTING -> hot ? 0xFFE1C783 : 0xFFA99A78;
        };
    }

    private int signatureText(boolean hot) {
        if (WardConfig.accessibilityHighContrast) return hot ? 0xFFFFFFFF : 0xFFF2E9D8;
        return switch (signature) {
            case CROOKED -> hot ? 0xFFFFF0A9 : 0xFFD7D2A0;
            case VEILED -> hot ? 0xFFF4D8FA : 0xFFD7BCD3;
            case EXACTING -> hot ? 0xFFFFE7AF : 0xFFE0D2B0;
        };
    }

    private int cardEdge(boolean hot) {
        if (WardConfig.accessibilityHighContrast) return hot ? 0xFFFFFFFF : 0xFFB9A873;
        return switch (signature) {
            case CROOKED -> hot ? 0xFFC5B85F : 0xFF5C6950;
            case VEILED -> hot ? 0xFFBD83CA : 0xFF5C465E;
            case EXACTING -> hot ? 0xFFD0B46C : 0xFF655C4B;
        };
    }

    private int cardOuter() {
        if (WardConfig.accessibilityHighContrast) return 0xFF030303;
        return switch (signature) {
            case CROOKED -> 0xFF171B14;
            case VEILED -> 0xFF21131E;
            case EXACTING -> 0xFF1D1A15;
        };
    }

    private int cardInner() {
        if (WardConfig.accessibilityHighContrast) return 0xFF101010;
        return switch (signature) {
            case CROOKED -> 0xFF20271D;
            case VEILED -> 0xFF2A1926;
            case EXACTING -> 0xFF29241B;
        };
    }

    private int cardHeader(boolean hot) {
        if (WardConfig.accessibilityHighContrast) return hot ? 0xFF403A25 : 0xFF252525;
        return switch (signature) {
            case CROOKED -> hot ? 0xFF465039 : 0xFF303A2B;
            case VEILED -> hot ? 0xFF513543 : 0xFF3A2932;
            case EXACTING -> hot ? 0xFF514630 : 0xFF3A3428;
        };
    }

    private Motif classifyMotif(Card c, boolean anomaly) {
        if (c == null) return Motif.GENERIC;
        if (anomaly) return Motif.ANOMALY;
        String lower = (c.title + " " + c.body).toLowerCase(java.util.Locale.ROOT);
        if (c.kind == ForbiddenBargain.Kind.DEATH) return Motif.DEATH;
        if (c.kind == ForbiddenBargain.Kind.CURSE) return Motif.CURSE;
        if (c.kind == ForbiddenBargain.Kind.REMEDY) return Motif.REMEDY;
        if (c.kind == ForbiddenBargain.Kind.CONTRACT) return Motif.CONTRACT;
        if (c.kind == ForbiddenBargain.Kind.RITUAL) return Motif.RITUAL;
        if (c.kind == ForbiddenBargain.Kind.COVENANT) return Motif.COVENANT;
        if (lower.contains("projectile") && (lower.contains("distance") || lower.contains("far "))) return Motif.DISTANCE;
        if (lower.contains("projectile") || lower.contains("arrow") || lower.contains("bolt")) return Motif.PROJECTILE;
        if (lower.contains("crop") || lower.contains("harvest") || lower.contains("wheat")) return Motif.HARVEST;
        if (lower.contains("reach") || lower.contains("block interaction") || lower.contains("long hand")) return Motif.REACH;
        if (lower.contains("armor") || lower.contains("resistance") || lower.contains("shield")) return Motif.ARMOR;
        if (lower.contains("health") || lower.contains("blood") || lower.contains("heart") || lower.contains("wound") || lower.contains("regen")) return Motif.BLOOD;
        if (lower.contains("hostile") || lower.contains("mob") || lower.contains("crowd")) return Motif.CROWD;
        if (lower.contains("deck") || lower.contains("dealer") || lower.contains("hand")) return Motif.DECK;
        if (lower.contains("teleport") || lower.contains("step") || lower.contains("move") || lower.contains("sky")) return Motif.TRAVEL;
        if (lower.contains("daylight") || lower.contains("night") || lower.contains("lantern") || lower.contains("vision")) return Motif.NIGHT;
        if (lower.contains("flame") || lower.contains("fire") || lower.contains("burn")) return Motif.FIRE;
        if (lower.contains("frost") || lower.contains("ice") || lower.contains("cold") || lower.contains("shiver")) return Motif.FROST;
        if (lower.contains("eye") || lower.contains("witness") || lower.contains("read") || lower.contains("ink") || lower.contains("rune")) return Motif.KNOWLEDGE;
        return switch (c.kind) {
            case WAGER -> Motif.DECK;
            case EPIC, UNIQUE, MASTER -> Motif.KNOWLEDGE;
            default -> Motif.GENERIC;
        };
    }

    private void drawMotifWash(GuiGraphics g, int x, int y, int w, int h, Motif motif, int accent, boolean hot, int id) {
        int wash = motifWashColor(motif, accent, hot);
        switch (motif) {
            case BLOOD, FIRE, FROST, NIGHT -> {
                for (int n = 0; n < 5; n++) {
                    int yy = y + 10 + n * 22 + Math.floorMod(id + n * 7, 8);
                    int xx = x + 6 + Math.floorMod(id * (n + 3), Math.max(1, w - 20));
                    g.fill(xx, yy, xx + Math.max(8, w / 4), yy + 1, wash);
                }
            }
            case PROJECTILE, DISTANCE, TRAVEL -> {
                for (int n = 0; n < 4; n++) {
                    int yy = y + 16 + n * 24;
                    int xx = x + 8 + n * 10;
                    g.fill(xx, yy, xx + w - 22, yy + 1, wash);
                    g.fill(xx + w - 26, yy - 2, xx + w - 22, yy + 2, wash);
                }
            }
            case HARVEST, CROWD -> {
                for (int n = 0; n < 7; n++) {
                    int px = x + 10 + Math.floorMod(id * 13 + n * 19, Math.max(1, w - 20));
                    int py = y + 10 + Math.floorMod(id * 5 + n * 23, Math.max(1, h - 20));
                    g.fill(px, py, px + 2, py + 2, wash);
                }
            }
            default -> {
                for (int n = 0; n < 4; n++) {
                    int yy = y + 13 + n * 24;
                    g.fill(x + 10, yy, x + w - 10, yy + 1, wash);
                }
            }
        }
    }

    private int motifWashColor(Motif motif, int accent, boolean hot) {
        return switch (motif) {
            case BLOOD -> hot ? 0x44B85B66 : 0x2A7A3D44;
            case REACH -> hot ? 0x44D9C77E : 0x2A8C7C4A;
            case HARVEST -> hot ? 0x4489B86D : 0x2A5C7C4C;
            case PROJECTILE -> hot ? 0x447ABAD8 : 0x2A4F7C94;
            case DISTANCE -> hot ? 0x44A1DCE2 : 0x2A6B969C;
            case CROWD -> hot ? 0x44D2AC74 : 0x2A8C7245;
            case ARMOR -> hot ? 0x44AAB0BF : 0x2A71788C;
            case RITUAL -> hot ? 0x4480C8B3 : 0x2A547F72;
            case CONTRACT -> hot ? 0x448BB6D1 : 0x2A597286;
            case COVENANT -> hot ? 0x44C47A7F : 0x2A83484D;
            case REMEDY -> hot ? 0x4492D0A7 : 0x2A5E8B70;
            case CURSE -> hot ? 0x44B54858 : 0x2A7A2F3D;
            case DEATH -> hot ? 0x447A1E29 : 0x2A4C131B;
            case DECK -> hot ? 0x44B99C67 : 0x2A756343;
            case TRAVEL -> hot ? 0x448DA7D4 : 0x2A5D6D8C;
            case NIGHT -> hot ? 0x448AA3C8 : 0x2A5A6982;
            case FIRE -> hot ? 0x44C47D54 : 0x2A7F5136;
            case FROST -> hot ? 0x4483C2D6 : 0x2A567E8B;
            case KNOWLEDGE -> hot ? 0x449B8ED4 : 0x2A6B618E;
            case ANOMALY -> hot ? 0x446E3A9C : 0x2A492768;
            default -> (accent & 0x00FFFFFF) | (hot ? 0x33000000 : 0x22000000);
        };
    }

    private void drawCornerMotifs(GuiGraphics g, int x, int y, Motif motif, int accent, boolean hot, int id) {
        int c = hot ? accent : signatureAccent(false);
        drawMotifGlyph(g, x + 18, y + 18, motif, c, hot, id + 1);
        drawMotifGlyph(g, x + CARD_W - 18, y + 18, motif, c, hot, id + 2);
        drawMotifGlyph(g, x + 18, y + CARD_H - 44, motif, c, false, id + 3);
        drawMotifGlyph(g, x + CARD_W - 18, y + CARD_H - 44, motif, c, false, id + 4);
    }

    private void drawHoverFiligree(GuiGraphics g, int x, int y, Motif motif, int accent, int id) {
        int band = (accent & 0x00FFFFFF) | 0x33000000;
        g.fill(x - 6, y + 10, x - 4, y + CARD_H - 10, band);
        g.fill(x + CARD_W + 4, y + 10, x + CARD_W + 6, y + CARD_H - 10, band);
        g.fill(x - 5, y - 5, x + 24, y - 3, accent);
        g.fill(x + CARD_W - 24, y - 5, x + CARD_W + 5, y - 3, accent);
        g.fill(x - 5, y + CARD_H + 3, x + 24, y + CARD_H + 5, accent);
        g.fill(x + CARD_W - 24, y + CARD_H + 3, x + CARD_W + 5, y + CARD_H + 5, accent);
        for (int n = 0; n < 3; n++) {
            int yy = y + 42 + n * 34;
            drawMotifGlyph(g, x - 10, yy, motif, accent, true, id + n * 11);
            drawMotifGlyph(g, x + CARD_W + 10, yy, motif, accent, true, id + n * 13);
        }
    }

    private void drawMotifGlyph(GuiGraphics g, int cx, int cy, Motif motif, int color, boolean hot, int id) {
        switch (motif) {
            case BLOOD -> {
                g.fill(cx - 1, cy - 5, cx + 1, cy + 4, color);
                g.fill(cx - 3, cy - 3, cx + 3, cy - 1, color);
                g.fill(cx - 2, cy + 4, cx + 2, cy + 6, color);
            }
            case REACH -> {
                g.fill(cx - 1, cy - 5, cx + 1, cy + 5, color);
                g.fill(cx - 5, cy, cx + 5, cy + 1, color);
                g.fill(cx - 4, cy - 4, cx - 2, cy + 4, color);
                g.fill(cx + 2, cy - 4, cx + 4, cy + 4, color);
            }
            case HARVEST -> {
                g.fill(cx - 1, cy - 6, cx + 1, cy + 6, color);
                g.fill(cx + 1, cy - 5, cx + 4, cy - 3, color);
                g.fill(cx - 4, cy - 1, cx - 1, cy + 1, color);
                g.fill(cx + 1, cy + 3, cx + 4, cy + 5, color);
            }
            case PROJECTILE -> {
                g.fill(cx - 6, cy, cx + 4, cy + 1, color);
                g.fill(cx + 3, cy - 2, cx + 7, cy + 2, color);
                g.fill(cx - 6, cy - 2, cx - 4, cy + 3, color);
            }
            case DISTANCE -> {
                drawMotifGlyph(g, cx, cy, Motif.PROJECTILE, color, hot, id);
                g.fill(cx - 5, cy - 5, cx + 5, cy - 4, color);
                g.fill(cx - 5, cy + 4, cx + 5, cy + 5, color);
            }
            case CROWD -> {
                g.fill(cx - 5, cy - 1, cx - 2, cy + 2, color);
                g.fill(cx - 1, cy - 4, cx + 2, cy - 1, color);
                g.fill(cx + 3, cy - 1, cx + 6, cy + 2, color);
                g.fill(cx - 1, cy + 2, cx + 2, cy + 5, color);
            }
            case ARMOR -> {
                g.fill(cx - 4, cy - 5, cx + 4, cy - 4, color);
                g.fill(cx - 5, cy - 4, cx - 4, cy + 3, color);
                g.fill(cx + 4, cy - 4, cx + 5, cy + 3, color);
                g.fill(cx - 3, cy + 3, cx + 3, cy + 5, color);
            }
            case RITUAL -> {
                g.fill(cx - 1, cy - 6, cx + 1, cy + 6, color);
                g.fill(cx - 5, cy + 3, cx + 5, cy + 4, color);
                g.fill(cx - 4, cy - 3, cx + 4, cy - 2, color);
            }
            case CONTRACT -> {
                g.fill(cx - 5, cy - 5, cx + 5, cy - 4, color);
                g.fill(cx - 5, cy - 1, cx + 3, cy, color);
                g.fill(cx - 5, cy + 3, cx + 5, cy + 4, color);
                g.fill(cx - 5, cy - 5, cx - 4, cy + 4, color);
            }
            case COVENANT -> {
                g.fill(cx - 5, cy - 4, cx + 5, cy - 3, color);
                g.fill(cx - 5, cy + 3, cx + 5, cy + 4, color);
                g.fill(cx - 5, cy - 3, cx - 4, cy + 4, color);
                g.fill(cx + 4, cy - 3, cx + 5, cy + 4, color);
                g.fill(cx - 1, cy - 1, cx + 1, cy + 1, color);
            }
            case REMEDY -> {
                g.fill(cx - 1, cy - 5, cx + 1, cy + 5, color);
                g.fill(cx - 5, cy - 1, cx + 5, cy + 1, color);
                g.fill(cx - 4, cy + 4, cx + 4, cy + 5, color);
            }
            case CURSE -> {
                g.fill(cx - 5, cy - 5, cx - 3, cy + 2, color);
                g.fill(cx - 3, cy + 1, cx + 3, cy + 3, color);
                g.fill(cx + 2, cy - 1, cx + 5, cy + 5, color);
            }
            case DEATH -> {
                g.fill(cx - 1, cy - 6, cx + 1, cy + 6, color);
                g.fill(cx - 6, cy - 1, cx + 6, cy + 1, color);
                g.fill(cx - 4, cy - 4, cx - 2, cy - 2, color);
                g.fill(cx + 2, cy - 4, cx + 4, cy - 2, color);
            }
            case DECK -> {
                g.fill(cx - 5, cy - 4, cx + 3, cy + 4, color);
                g.fill(cx - 3, cy - 6, cx + 5, cy + 2, (color & 0x00FFFFFF) | 0xBB000000);
            }
            case TRAVEL -> {
                g.fill(cx - 5, cy - 4, cx - 1, cy - 2, color);
                g.fill(cx - 1, cy - 1, cx + 3, cy + 1, color);
                g.fill(cx + 3, cy + 2, cx + 7, cy + 4, color);
            }
            case NIGHT -> {
                g.fill(cx - 4, cy - 5, cx + 2, cy + 5, color);
                g.fill(cx - 1, cy - 4, cx + 4, cy + 4, 0xFF1B121B);
            }
            case FIRE -> {
                g.fill(cx - 1, cy - 6, cx + 1, cy + 4, color);
                g.fill(cx - 4, cy - 1, cx, cy + 3, color);
                g.fill(cx, cy + 1, cx + 4, cy + 5, color);
            }
            case FROST -> {
                g.fill(cx - 1, cy - 6, cx + 1, cy + 6, color);
                g.fill(cx - 6, cy - 1, cx + 6, cy + 1, color);
                g.fill(cx - 4, cy - 4, cx - 2, cy - 2, color);
                g.fill(cx + 2, cy + 2, cx + 4, cy + 4, color);
                g.fill(cx - 4, cy + 2, cx - 2, cy + 4, color);
                g.fill(cx + 2, cy - 4, cx + 4, cy - 2, color);
            }
            case KNOWLEDGE -> {
                g.fill(cx - 5, cy - 1, cx + 5, cy + 1, color);
                g.fill(cx - 3, cy - 3, cx + 3, cy - 2, color);
                g.fill(cx - 3, cy + 2, cx + 3, cy + 3, color);
                g.fill(cx - 1, cy - 1, cx + 1, cy + 1, hot ? 0xFFF2E9D8 : 0xFF1B121B);
            }
            case ANOMALY -> {
                int drift = Math.floorMod((int) (System.currentTimeMillis() / (hot ? 80L : 140L)) + id, 5) - 2;
                g.fill(cx - 1, cy - 6, cx + 1, cy + 6, color);
                g.fill(cx - 6, cy - 1, cx + 6, cy + 1, 0xFF75D3CD);
                g.fill(cx - 5 + drift, cy - 4, cx + 5 + drift, cy - 3, 0xFFB98ADF);
            }
            default -> {
                g.fill(cx - 1, cy - 5, cx + 1, cy + 5, color);
                g.fill(cx - 5, cy - 1, cx + 5, cy + 1, color);
            }
        }
    }

    private void drawFrameForm(GuiGraphics g, int x, int y, Card c, int accent, boolean hot, boolean anomaly, boolean refuse, Motif motif) {
        g.fill(x + 7, y + 7, x + CARD_W - 7, y + 8, accent);
        g.fill(x + 7, y + CARD_H - 8, x + CARD_W - 7, y + CARD_H - 7, accent);
        g.fill(x + 7, y + 8, x + 8, y + 20, accent);
        g.fill(x + CARD_W - 8, y + 8, x + CARD_W - 7, y + 20, accent);
        g.fill(x + 7, y + CARD_H - 20, x + 8, y + CARD_H - 8, accent);
        g.fill(x + CARD_W - 8, y + CARD_H - 20, x + CARD_W - 7, y + CARD_H - 8, accent);
        if (refuse) return;
        int dim = (accent & 0x00FFFFFF) | 0x66000000;
        if (anomaly) {
            long t = System.currentTimeMillis() / (WardConfig.accessibilityReduceMotion ? 260L : 70L) + c.id * 31L;
            int drift = Math.floorMod((int) t, 7) - 3;
            g.fill(x + 20 + drift, y + 6, x + CARD_W - 20 + drift, y + 7, 0xFF73D8CF);
            g.fill(x + 11, y + 30, x + 12, y + CARD_H - 32, 0xFFB78BE2);
            g.fill(x + CARD_W - 12, y + 24, x + CARD_W - 11, y + CARD_H - 26, 0xFF73D8CF);
            return;
        }
        switch (c.kind) {
            case CURSE -> {
                g.fill(x + 12, y + 28, x + 13, y + CARD_H - 16, accent);
                g.fill(x + CARD_W - 13, y + 16, x + CARD_W - 12, y + CARD_H - 28, accent);
                for (int n = 0; n < 4; n++) {
                    int yy = y + 34 + n * 29;
                    g.fill(x + 9, yy, x + 16, yy + 1, dim);
                    g.fill(x + CARD_W - 16, yy + 4, x + CARD_W - 9, yy + 5, dim);
                }
            }
            case DEATH -> {
                g.fill(x + CARD_W / 2 - 11, y + 6, x + CARD_W / 2 + 11, y + 7, accent);
                g.fill(x + CARD_W / 2 - 7, y + CARD_H - 7, x + CARD_W / 2 + 7, y + CARD_H - 6, accent);
                for (int n = 0; n < 5; n++) {
                    int sx = x + 18 + n * 22;
                    g.fill(sx, y + 9, sx + 1, y + 14, dim);
                    g.fill(sx + 2, y + CARD_H - 15, sx + 3, y + CARD_H - 10, dim);
                }
            }
            case REMEDY -> {
                g.fill(x + 14, y + 28, x + CARD_W - 14, y + 29, dim);
                g.fill(x + 14, y + CARD_H - 30, x + CARD_W - 14, y + CARD_H - 29, dim);
                g.fill(x + 22, y + 8, x + 23, y + 18, dim);
                g.fill(x + CARD_W - 23, y + 8, x + CARD_W - 22, y + 18, dim);
            }
            case CONTRACT -> {
                g.fill(x + 10, y + 28, x + 20, y + 29, accent);
                g.fill(x + CARD_W - 20, y + 28, x + CARD_W - 10, y + 29, accent);
                g.fill(x + 10, y + CARD_H - 29, x + 20, y + CARD_H - 28, accent);
                g.fill(x + CARD_W - 20, y + CARD_H - 29, x + CARD_W - 10, y + CARD_H - 28, accent);
                g.fill(x + 13, y + 10, x + 14, y + CARD_H - 10, dim);
                g.fill(x + CARD_W - 14, y + 10, x + CARD_W - 13, y + CARD_H - 10, dim);
            }
            case RITUAL -> {
                g.fill(x + CARD_W / 2 - 1, y + 26, x + CARD_W / 2 + 1, y + CARD_H - 26, dim);
                g.fill(x + 19, y + 44, x + CARD_W - 19, y + 45, dim);
                g.fill(x + 19, y + CARD_H - 45, x + CARD_W - 19, y + CARD_H - 44, dim);
            }
            case COVENANT -> {
                g.fill(x + 14, y + 14, x + CARD_W - 14, y + 15, accent);
                g.fill(x + 14, y + CARD_H - 15, x + CARD_W - 14, y + CARD_H - 14, accent);
                g.fill(x + 14, y + 14, x + 15, y + CARD_H - 14, dim);
                g.fill(x + CARD_W - 15, y + 14, x + CARD_W - 14, y + CARD_H - 14, dim);
            }
            case MASTER -> {
                g.fill(x + 10, y + 10, x + CARD_W - 10, y + 11, signatureAccent(true));
                g.fill(x + 10, y + CARD_H - 11, x + CARD_W - 10, y + CARD_H - 10, signatureAccent(false));
                g.fill(x + 20, y + 13, x + 21, y + CARD_H - 13, accent);
                g.fill(x + CARD_W - 21, y + 13, x + CARD_W - 20, y + CARD_H - 13, accent);
            }
            case EPIC, UNIQUE -> {
                int top = hot ? signatureAccent(true) : dim;
                g.fill(x + 18, y + 9, x + CARD_W - 18, y + 10, top);
                g.fill(x + 18, y + CARD_H - 10, x + CARD_W - 18, y + CARD_H - 9, top);
            }
            default -> {
                if (motif == Motif.PROJECTILE || motif == Motif.DISTANCE || motif == Motif.TRAVEL) {
                    for (int n = 0; n < 3; n++) {
                        int yy = y + 40 + n * 34;
                        g.fill(x + 14, yy, x + CARD_W - 18, yy + 1, dim);
                        g.fill(x + CARD_W - 18, yy - 1, x + CARD_W - 14, yy + 3, dim);
                    }
                }
            }
        }
    }

    private void drawMajorMotifIcon(GuiGraphics g, int cx, int cy, Motif motif, int accent, boolean hot, int id) {
        int wash = (accent & 0x00FFFFFF) | (hot ? 0x44000000 : 0x2A000000);
        int icon = (accent & 0x00FFFFFF) | (hot ? 0x66000000 : 0x44000000);
        g.fill(cx - 18, cy - 14, cx + 18, cy - 13, wash);
        g.fill(cx - 18, cy + 13, cx + 18, cy + 14, wash);
        switch (motif) {
            case BLOOD -> {
                g.fill(cx - 2, cy - 10, cx + 2, cy + 8, icon);
                g.fill(cx - 8, cy - 5, cx + 8, cy - 1, icon);
                g.fill(cx - 5, cy + 8, cx + 5, cy + 12, icon);
            }
            case REACH -> {
                g.fill(cx - 2, cy - 12, cx + 2, cy + 12, icon);
                g.fill(cx - 12, cy - 2, cx + 12, cy + 2, icon);
                g.fill(cx - 9, cy - 10, cx - 5, cy + 10, icon);
                g.fill(cx + 5, cy - 10, cx + 9, cy + 10, icon);
            }
            case HARVEST -> {
                g.fill(cx - 2, cy - 11, cx + 2, cy + 11, icon);
                g.fill(cx + 2, cy - 9, cx + 9, cy - 5, icon);
                g.fill(cx - 9, cy - 2, cx - 2, cy + 2, icon);
                g.fill(cx + 2, cy + 5, cx + 9, cy + 9, icon);
            }
            case PROJECTILE, DISTANCE -> {
                g.fill(cx - 14, cy - 1, cx + 8, cy + 1, icon);
                g.fill(cx + 7, cy - 4, cx + 14, cy + 4, icon);
                g.fill(cx - 14, cy - 5, cx - 9, cy + 6, icon);
                if (motif == Motif.DISTANCE) {
                    g.fill(cx - 10, cy - 10, cx + 10, cy - 9, icon);
                    g.fill(cx - 10, cy + 9, cx + 10, cy + 10, icon);
                }
            }
            case CROWD -> {
                int[] ox={-10,0,10,-5,5}; int[] oy={-4,-4,-4,6,6};
                for (int n=0;n<5;n++) g.fill(cx + ox[n] - 3, cy + oy[n] - 3, cx + ox[n] + 3, cy + oy[n] + 3, icon);
            }
            case ARMOR -> {
                g.fill(cx - 10, cy - 11, cx + 10, cy - 8, icon);
                g.fill(cx - 11, cy - 8, cx - 8, cy + 8, icon);
                g.fill(cx + 8, cy - 8, cx + 11, cy + 8, icon);
                g.fill(cx - 8, cy + 8, cx + 8, cy + 12, icon);
            }
            case CURSE -> {
                g.fill(cx - 12, cy - 10, cx - 7, cy + 5, icon);
                g.fill(cx - 6, cy + 2, cx + 6, cy + 6, icon);
                g.fill(cx + 5, cy - 4, cx + 11, cy + 12, icon);
            }
            case DEATH -> {
                g.fill(cx - 2, cy - 12, cx + 2, cy + 12, icon);
                g.fill(cx - 12, cy - 2, cx + 12, cy + 2, icon);
                g.fill(cx - 8, cy - 8, cx - 4, cy - 4, icon);
                g.fill(cx + 4, cy - 8, cx + 8, cy - 4, icon);
            }
            default -> {
                drawMotifGlyph(g, cx, cy, motif, icon, hot, id);
                drawMotifGlyph(g, cx - 8, cy, motif, wash, false, id + 1);
                drawMotifGlyph(g, cx + 8, cy, motif, wash, false, id + 2);
            }
        }
    }

    private boolean canMulligan() {
        return !answered && !msg.forcedChoice && msg.dealMode == 0 && msg.mulliganReserve > 0;
    }

    private int mulliganButtonX() { return width / 2 - 58; }
    private int mulliganButtonY(int footerY) { return Math.min(height - 22, footerY + 12); }
    private int currentFooterY() {
        return Math.round((baseY() + CARD_H) * handScale()) + (looseCard ? 10 : 24);
    }

    private boolean overMulliganButton(double mx, double my) {
        int x = mulliganButtonX();
        int y = mulliganButtonY(currentFooterY());
        return mx >= x && mx < x + 116 && my >= y && my < y + 18;
    }

    private void drawMulliganButton(GuiGraphics g, int mouseX, int mouseY, int y) {
        int x = mulliganButtonX();
        boolean hot = mouseX >= x && mouseX < x + 116 && mouseY >= y && mouseY < y + 18;
        int fill = mulliganMode ? 0xDD244B5A : hot ? 0xCC203D48 : 0xAA172B33;
        g.fill(x, y, x + 116, y + 18, fill);
        g.fill(x + 1, y + 1, x + 115, y + 2, mulliganMode ? 0xFF9DD9DF : 0xFF547E88);
        g.fill(x + 1, y + 16, x + 115, y + 17, 0xFF162229);
        g.drawCenteredString(font, mulliganMode ? "CHOOSE CARD" : "CUT ×" + msg.mulliganReserve, x + 58, y + 5,
                mulliganMode ? 0xFFE0FAF8 : hot ? 0xFFC7E7E8 : 0xFF88AAB0);
    }

    private int looseRefuseX() { return width / 2 - 44; }
    private int looseRefuseY(int footerY) { return footerY + 12; }

    private void drawLooseRefuseButton(GuiGraphics g, int mouseX, int mouseY, int y) {
        int x = looseRefuseX();
        boolean hot = mouseX >= x && mouseX < x + 88 && mouseY >= y && mouseY < y + 18;
        g.fill(x, y, x + 88, y + 18, hot ? 0xCC4A2228 : 0xAA24191D);
        g.fill(x + 1, y + 1, x + 87, y + 2, hot ? 0xFFB86C73 : 0xFF6B4C50);
        g.fill(x + 1, y + 16, x + 87, y + 17, 0xFF3E2B30);
        g.drawCenteredString(font, hot ? "REFUSE" : "turn away", x + 44, y + 5,
                hot ? 0xFFE6C7C8 : 0xFF9A8586);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && !answered) {
            if (canMulligan() && overMulliganButton(mx, my)) {
                mulliganMode = !mulliganMode;
                redPenMode = false;
                Sfx.play(WardSounds.MARK, 0.30f, mulliganMode ? 1.18f : 0.92f);
                return true;
            }
            int i = cardAt(mx, my);
            if (i >= 0) {
                int id = cards.get(i).id;
                if (mulliganMode) {
                    if (id >= 0) finishMulligan(id);
                    return true;
                }
                choose(id);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void choose(int id) {
        if (answered) return;
        if (redPenMode) {
            if (id == ForbiddenBargain.THE_RED_PEN.id) { redPenMode = false; return; }
            if (id >= 0) { finishRedPen(id); return; }
            return;
        }
        if (id == ForbiddenBargain.THE_RED_PEN.id) {
            redPenMode = true;
            mulliganMode = false;
            Sfx.play(WardSounds.MARK_BAD, 0.38f, 1.36f);
            return;
        }
        for (Card card : cards) {
            ForbiddenBargain bargain = ForbiddenBargain.byId(card.id);
            if (card.id == id && CardBranches.eligible(card.variant, bargain)) {
                Minecraft.getInstance().setScreen(new CardBranchScreen(this, bargain, card.variant, branch -> finishChoice(id, branch)));
                return;
            }
        }
        finishChoice(id, 0);
    }

    private void finishMulligan(int targetId) {
        if (answered || !canMulligan() || targetId < 0) return;
        answered = true;
        selectedId = targetId;
        pendingChoiceId = ChestValuator.MULLIGAN_ACTION_ID;
        pendingBranch = targetId;
        Wardbound.CHANNEL.sendToServer(new BargainChoicePacket(msg.pos, ChestValuator.MULLIGAN_ACTION_ID, targetId));
        ClientWardEventOverlay.show("CUT THE HAND // REDRAW", 1);
        Sfx.play(WardSounds.MARK, 0.46f, 1.28f);
    }

    private void finishRedPen(int targetId) {
        if (answered || targetId < 0 || targetId == ForbiddenBargain.THE_RED_PEN.id) return;
        answered = true;
        selectedId = targetId;
        pendingChoiceId = ForbiddenBargain.THE_RED_PEN.id;
        pendingBranch = ChestValuator.RED_PEN_TARGET_BASE + targetId;
        Wardbound.CHANNEL.sendToServer(new BargainChoicePacket(msg.pos, ForbiddenBargain.THE_RED_PEN.id, pendingBranch));
        ClientWardEventOverlay.show("RED PEN // CARD STRUCK", 2);
        Sfx.play(WardSounds.MARK_BAD, 0.50f, 1.18f);
    }

    private void finishChoice(int id, int branch) {
        if (answered) return;
        answered = true;
        selectedId = id;
        pendingChoiceId = id;
        pendingBranch = branch;
        // Commit the choice to the authoritative server immediately. The short delay below is
        // presentation only, so a screen replacement/disconnect cannot erase a valid click.
        Wardbound.CHANNEL.sendToServer(new BargainChoicePacket(msg.pos, id, branch));
        selectionNanos = System.nanoTime();
        if (Minecraft.getInstance().screen != this) Minecraft.getInstance().setScreen(this);
        Card chosen = null;
        for (Card card : cards) if (card.id == id) { chosen = card; break; }
        if (chosen != null && chosen.id >= 0) {
            int mood = chosen.kind == ForbiddenBargain.Kind.DEATH || chosen.kind == ForbiddenBargain.Kind.CURSE
                    || chosen.kind == ForbiddenBargain.Kind.COVENANT ? 2
                    : chosen.kind == ForbiddenBargain.Kind.EPIC || chosen.kind == ForbiddenBargain.Kind.UNIQUE
                    || chosen.kind == ForbiddenBargain.Kind.RITUAL ? 3 : 1;
            boolean anomaly = isAnomaly(chosen);
            ClientWardEventOverlay.show(anomaly ? "ANOMALY // RESOLVING" : "INK TAKES // " + chosen.title, anomaly ? 3 : mood);
            playCardLodestone(chosen, 1.05f + rarityStrength(chosen, anomaly) * 0.22f);
            if (anomaly) {
                Sfx.play(WardSounds.EYE_OPEN, 0.48f, 0.52f);
                Sfx.play(WardSounds.MARK_BAD, 0.24f, 1.42f);
            } else if (chosen.kind == ForbiddenBargain.Kind.DEATH) {
                Sfx.play(WardSounds.WARD_FAIL, 0.58f, 0.48f);
                Sfx.play(WardSounds.HEARTBEAT, 0.40f, 0.58f);
            } else if (chosen.kind == ForbiddenBargain.Kind.MASTER) {
                Sfx.play(WardSounds.MASTER_MOTIF, 0.54f, 0.88f);
            } else {
                Sfx.play(WardSounds.EYE_CHOOSE, 0.65f, 0.60f);
            }
        } else Sfx.play(WardSounds.EYE_CHOOSE, 0.65f, 0.80f);
    }

    @Override
    public void tick() {
        super.tick();
        if (selectionNanos <= 0L) return;
        if (System.nanoTime() - selectionNanos < 420_000_000L) return;
        selectionNanos = 0L;
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && redPenMode && !answered) {
            redPenMode = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return selectionNanos <= 0L && !msg.forcedChoice;
    }

    @Override
    public void onClose() {
        if (selectionNanos > 0L) return;
        if (msg.forcedChoice && !answered) return;
        if (!answered) choose(-1);
        else super.onClose();
    }
}
