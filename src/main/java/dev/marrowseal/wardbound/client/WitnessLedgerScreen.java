package dev.marrowseal.wardbound.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import dev.marrowseal.wardbound.ForbiddenBargain;
import dev.marrowseal.wardbound.CardConjunctions;
import dev.marrowseal.wardbound.CardEcology;
import dev.marrowseal.wardbound.CardLineage;
import dev.marrowseal.wardbound.CardMaster;
import dev.marrowseal.wardbound.CardEvolution;
import dev.marrowseal.wardbound.MinigameType;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.UseWitnessLedgerPacket;
import dev.marrowseal.wardbound.net.CloseWitnessLedgerPacket;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Textured, larger witness ledger UI with live status, search/filter, and page-turn feedback. */
public class WitnessLedgerScreen extends Screen {
    private static final String SEP = "";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM  HH:mm");
    private static final ResourceLocation PAGE = new ResourceLocation(Wardbound.MODID, "textures/gui/witness_ledger_page.png");
    private static final ResourceLocation WIDGETS = new ResourceLocation(Wardbound.MODID, "textures/gui/witness_ledger_widgets.png");
    private static final int PANEL_W = 680;
    private static final int PANEL_H = 420;
    private static final int PAGE_TEX_W = 468;
    private static final int PAGE_TEX_H = 320;
    private static final int GRIMOIRE_LEFT_X = 62;
    private static final int GRIMOIRE_LEFT_W = 250;
    private static final int GRIMOIRE_RIGHT_X = 368;
    private static final int GRIMOIRE_RIGHT_W = 250;
    private static final int GRIMOIRE_VISIBLE = 18;

    // High-contrast ink palette for the parchment. The old dark brown ink was
    // aesthetically correct but unreadable on the orange/brown paper texture.
    private static final int INK_PRIMARY = 0xF5EBDD;
    private static final int INK_SECONDARY = 0xDDCEB6;
    private static final int INK_MUTED = 0xBFAF99;
    private static final int INK_HEADER = 0xFFF2D4;
    private static final int INK_LINE = 0xA77E694B;
    private static final int TAB_W = 78;
    private static final int TAB_H = 22;
    private static final int SMALL_W = 104;
    private static final int SMALL_H = 18;
    private static final int TAB_LOOT = 0;
    private static final int TAB_CHRONICLE = 1;
    private static final int TAB_LAST_WARD = 2;
    private static final int TAB_GRIMOIRE = 3;
    private static final int TAB_RULES = 4;
    private static final int TAB_STATUS = 5;
    private static final int TAB_ANALYSIS = 6;
    private static final int TAB_COUNT = 7;
    private static final int TAB_GAP = 6;

    // Session-local navigation memory: reopening or switching pages no longer throws
    // the reader back to the first line of every section. This is UI state only.
    private static int sessionTab = TAB_LOOT;
    private static final int[] SESSION_SCROLL = new int[TAB_COUNT];
    private static final String[] SESSION_SEARCH = new String[TAB_COUNT];
    private static final HistoryFilter[] SESSION_HISTORY = new HistoryFilter[]{HistoryFilter.ALL, HistoryFilter.ALL};
    private static GrimoireFilter SESSION_GRIMOIRE_FILTER = GrimoireFilter.ALL;

    private List<LedgerEntry> lootLines;
    private List<LedgerEntry> recordLines;
    private LedgerEntry lastWard;
    private List<String> grimoireLines;
    private List<StatEntry> statLines;
    private List<StatEntry> analysisLines;
    private List<String> ruleLines;
    private int tab;
    private int scroll;
    private HistoryFilter historyFilter = HistoryFilter.ALL;
    private int selectedLoot;
    private int selectedRecord;
    private int selectedGrimoire;
    private int detailScrollLoot;
    private int detailScrollRecord;
    private int detailScrollGrimoire;
    private String pressedWidget = "";
    private int pressTicks;
    private EditBox searchBox;
    private GrimoireFilter grimoireFilter = GrimoireFilter.ALL;
    private int openCardId = -1;
    private int pageFlipTicks;
    private int pageFlipDirection = -1;
    private int ambientCooldown;
    private boolean cardSynergyMode;

    /** Responsive scale keeps the enlarged 560x348 book fully visible at GUI scale 4. */
    private float ledgerScale() {
        float sx = Math.max(0.50f, (this.width - 8.0f) / PANEL_W);
        float sy = Math.max(0.50f, (this.height - 8.0f) / PANEL_H);
        return Math.min(1.0f, Math.min(sx, sy));
    }

    private int virtualWidth() { return Math.round(this.width / ledgerScale()); }
    private int virtualHeight() { return Math.round(this.height / ledgerScale()); }
    private int panelLeft() { return (virtualWidth() - PANEL_W) / 2; }
    private int panelTop() { return (virtualHeight() - PANEL_H) / 2; }

    public WitnessLedgerScreen(List<String> lootLines, List<String> recordLines, String lastWard, List<String> grimoireLines, List<String> statLines, List<String> ruleLines) {
        super(Component.literal("Witness Ledger"));
        this.lootLines = parse(lootLines);
        this.recordLines = parse(recordLines);
        this.lastWard = LedgerEntry.parse(lastWard);
        this.grimoireLines = grimoireLines == null ? List.of() : List.copyOf(grimoireLines);
        assignStats(statLines);
        this.ruleLines = ruleLines == null ? List.of() : List.copyOf(ruleLines);
        this.tab = Math.max(0, Math.min(TAB_COUNT - 1, sessionTab));
        this.scroll = Math.max(0, SESSION_SCROLL[this.tab]);
        this.grimoireFilter = SESSION_GRIMOIRE_FILTER;
        if (this.tab == TAB_LOOT || this.tab == TAB_CHRONICLE) this.historyFilter = SESSION_HISTORY[this.tab];
    }

    public void refreshFromServer(List<String> loot, List<String> records, String last, List<String> grimoire, List<String> stats, List<String> rules) {
        this.lootLines = parse(loot);
        this.recordLines = parse(records);
        this.lastWard = LedgerEntry.parse(last);
        this.grimoireLines = grimoire == null ? List.of() : List.copyOf(grimoire);
        assignStats(stats);
        this.ruleLines = rules == null ? List.of() : List.copyOf(rules);
        clampSelections();
    }

    @Override
    protected void init() {
        updateSearchBoxLayout(true);
        if (searchBox != null && SESSION_SEARCH[tab] != null) searchBox.setValue(SESSION_SEARCH[tab]);
        scroll = Math.max(0, SESSION_SCROLL[tab]);
        playLedgerOpenSequence();
        playAmbientForTab();
        clampSelections();
    }

    private void updateSearchBoxLayout(boolean initial) {
        int left = panelLeft();
        int top = panelTop();
        String existing = searchBox == null ? "" : searchBox.getValue();
        boolean focused = searchBox != null && searchBox.isFocused();
        if (searchBox == null) {
            searchBox = new EditBox(this.font, left + 34, top + 82, 190, 14, Component.literal("Search"));
            searchBox.setMaxLength(48);
            addRenderableWidget(searchBox);
        }
        boolean grimoireIndex = tab == TAB_GRIMOIRE && openCardId < 0;
        searchBox.setX(grimoireIndex ? left + GRIMOIRE_LEFT_X : left + 34);
        searchBox.setY(grimoireIndex ? top + 86 : top + 82);
        searchBox.setWidth(grimoireIndex ? GRIMOIRE_LEFT_W : 190);
        if (initial) searchBox.setValue(existing);
        searchBox.setHint(Component.literal("type to search...").withStyle(ChatFormatting.ITALIC));
        boolean searchable = tab == TAB_LOOT || tab == TAB_CHRONICLE || grimoireIndex;
        searchBox.setVisible(searchable);
        searchBox.setFocused(searchable && focused);
        searchBox.setEditable(searchable);
        searchBox.setResponder(s -> {
            scroll = 0;
            selectedLoot = 0;
            selectedRecord = 0;
            selectedGrimoire = 0;
            if (tab == TAB_GRIMOIRE && openCardId < 0) {
                List<String> view = grimoireView();
                for (int i = 0; i < view.size(); i++) {
                    if (!grimoireDisplay(view.get(i)).startsWith("—")) { selectedGrimoire = i; break; }
                }
            }
            detailScrollLoot = 0;
            detailScrollRecord = 0;
            detailScrollGrimoire = 0;
            clampSelections();
        });
    }

    /** Keep EditBox focus synchronized with Screen's focused child so charTyped reaches it. */
    private void focusSearchBox() {
        boolean searchable = tab == TAB_LOOT || tab == TAB_CHRONICLE || (tab == TAB_GRIMOIRE && openCardId < 0);
        if (searchBox == null || !searchable) return;
        this.setFocused(searchBox);
        searchBox.setFocused(true);
    }

    private void blurSearchBox() {
        if (searchBox == null) return;
        searchBox.setFocused(false);
        if (this.getFocused() == searchBox) this.setFocused(null);
    }

    @Override
    public void tick() {
        if (pressTicks > 0) pressTicks--;
        if (pageFlipTicks > 0) pageFlipTicks--;
        if (searchBox != null) {
            searchBox.setVisible(tab == TAB_LOOT || tab == TAB_CHRONICLE || (tab == TAB_GRIMOIRE && openCardId < 0));
            searchBox.tick();
        }
        if (this.minecraft != null && this.minecraft.player != null && this.minecraft.player.tickCount % 40 == 0) {
            Wardbound.CHANNEL.sendToServer(new UseWitnessLedgerPacket(false));
        }
        if (--ambientCooldown <= 0) {
            playAmbientForTab();
        }
    }

    @Override
    public void onClose() {
        rememberLedgerView();
        Wardbound.CHANNEL.sendToServer(new CloseWitnessLedgerPacket());
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        float uiScale = ledgerScale();
        int virtualMouseX = Math.round(mouseX / uiScale);
        int virtualMouseY = Math.round(mouseY / uiScale);
        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1.0f);
        int left = panelLeft();
        int top = panelTop();
        int right = left + PANEL_W;
        int bottom = top + PANEL_H;

        // Scale only the paper mesh. All interactive coordinates remain in the
        // enlarged logical panel so mouse hitboxes and text stay crisp.
        g.pose().pushPose();
        g.pose().translate(left, top, 0.0f);
        g.pose().scale(PANEL_W / (float) PAGE_TEX_W, PANEL_H / (float) PAGE_TEX_H, 1.0f);
        g.blit(PAGE, 0, 0, 0, 0, PAGE_TEX_W, PAGE_TEX_H, PAGE_TEX_W, PAGE_TEX_H);
        g.pose().popPose();
        renderPageFlip(g, left, top, partialTick);

        g.drawCenteredString(this.font,
                Component.literal("THE WITNESS GRIMOIRE").withStyle(ChatFormatting.BOLD),
                (left + right) / 2, top + 18, INK_HEADER);
        g.drawCenteredString(this.font,
                Component.literal(subtitle()).withStyle(ChatFormatting.ITALIC),
                (left + right) / 2, top + 32, INK_SECONDARY);

        int tabY = top + 52;
        int step = TAB_W + TAB_GAP;
        int tabsLeft = left + (PANEL_W - (TAB_W * TAB_COUNT + TAB_GAP * (TAB_COUNT - 1))) / 2;
        drawTab(g, tabsLeft, tabY, TAB_LOOT, "Loot");
        drawTab(g, tabsLeft + step, tabY, TAB_CHRONICLE, "Chronicle");
        drawTab(g, tabsLeft + step * 2, tabY, TAB_LAST_WARD, "Last Ward");
        drawTab(g, tabsLeft + step * 3, tabY, TAB_GRIMOIRE, "Grimoire");
        drawTab(g, tabsLeft + step * 4, tabY, TAB_RULES, "Rules");
        drawTab(g, tabsLeft + step * 5, tabY, TAB_STATUS, "Status");
        drawTab(g, tabsLeft + step * 6, tabY, TAB_ANALYSIS, "Analysis");

        if (tab == TAB_LAST_WARD) renderLastWard(g, left, top, right, bottom);
        else if (tab == TAB_GRIMOIRE) renderGrimoire(g, left, top, right, bottom, virtualMouseX, virtualMouseY);
        else if (tab == TAB_RULES) renderRules(g, left, top, right, bottom);
        else if (tab == TAB_STATUS) renderStatus(g, left, top, right, bottom);
        else if (tab == TAB_ANALYSIS) renderAnalysis(g, left, top, right, bottom);
        else renderHistory(g, left, top, right, bottom, virtualMouseX, virtualMouseY);

        if ((tab == TAB_LOOT || tab == TAB_CHRONICLE) && searchBox != null) {
            g.drawString(this.font, "Search / date / tag", left + 34, top + 71, INK_SECONDARY, true);
        } else if (tab == TAB_GRIMOIRE && openCardId < 0 && searchBox != null) {
            g.drawString(this.font, "CARD & LORE INDEX", left + GRIMOIRE_LEFT_X, top + 72, INK_HEADER, true);
        }

        super.render(g, virtualMouseX, virtualMouseY, partialTick);
        g.pose().popPose();
    }

    private void renderPageFlip(GuiGraphics g, int left, int top, float partialTick) {
        if (pageFlipTicks <= 0) return;
        float progress = 1.0f - Math.max(0.0f, Math.min(1.0f, (pageFlipTicks - partialTick) / 9.0f));
        // A faux page mesh: a curled paper edge travels right-to-left with curved top/bottom cuts.
        int right = left + PANEL_W;
        int foldX = right - 34 - (int) ((PANEL_W - 68) * progress);
        int curlW = 34;
        for (int i = 0; i < curlW; i++) {
            float t = i / (float) (curlW - 1);
            float curve = (float) Math.sin(t * Math.PI);
            int x = foldX + (pageFlipDirection < 0 ? i : -i);
            int inset = 3 + Math.round(curve * 18.0f);
            int alpha = 58 + Math.round(curve * 95.0f);
            int paper = (alpha << 24) | 0x00E8D3AA;
            g.fill(x, top + 29 + inset, x + 1, top + PANEL_H - 29 - inset, paper);
        }
        // Deep moving shadow under the fold, followed by a narrow worn-paper highlight.
        int shadowX = pageFlipDirection < 0 ? foldX - 15 : foldX + 7;
        g.fill(shadowX, top + 36, shadowX + 12, top + PANEL_H - 36, 0x40170E09);
        g.fill(foldX, top + 39, foldX + 2, top + PANEL_H - 39, 0xA0FFF0CC);
        // Corner curl: the visible tip shrinks at the middle of the turn.
        int corner = Math.max(5, 28 - Math.round((float) Math.sin(progress * Math.PI) * 18.0f));
        g.fill(foldX - 5, top + 29, foldX + corner, top + 35 + corner, 0x55F6E2BC);
        g.fill(foldX - 5, top + PANEL_H - 35 - corner, foldX + corner, top + PANEL_H - 29, 0x321E120A);
    }

    /** A restrained dark ink wash used behind dense or long-form text. */
    private void drawReadingWash(GuiGraphics g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, 0x8A120E0B);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x444E3A2A);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x332A1D15);
    }

    private void drawFooterWash(GuiGraphics g, int left, int right, int bottom) {
        g.fill(left + 22, bottom - 25, right - 22, bottom - 7, 0x74100D0A);
    }

    private String subtitle() {
        return switch (tab) {
            case TAB_LOOT -> "Spoils, fragments and bargains committed to paper.";
            case TAB_CHRONICLE -> historyFilter == HistoryFilter.SAVANT ? "Only the eye's own remarks and intrusions." : "Voices, warnings and things that noticed you.";
            case TAB_LAST_WARD -> "The last seal reduced to one careful testimony.";
            case TAB_GRIMOIRE -> openCardId >= 0
                    ? (cardSynergyMode ? "The card's relationships, responses and documented pairings." : "A signed page opened inside the book.")
                    : "Known laws, masters and encounters. Select a line to inspect it.";
            case TAB_RULES -> "Rules become legible when their systems unlock; sealed margins do not spoil what comes next.";
            case TAB_STATUS -> "A live diagram of what the cards have made of you.";
            case TAB_ANALYSIS -> "Patterns, trends and the House's memory of how you actually play.";
            default -> "The ledger remains open.";
        };
    }

    private void renderHistory(GuiGraphics g, int left, int top, int right, int bottom, int mouseX, int mouseY) {
        List<LedgerEntry> current = filteredCurrent();
        int visible = 11;
        int rowX = left + 34;
        int rowY = top + 108;
        int rowW = right - left - 68;
        int rowH = 17;
        int maxScroll = Math.max(0, current.size() - visible);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        int bx = right - 130;
        int by = top + 75;
        drawSmallButton(g, bx, by, "filter", "Type: " + historyFilter.shortLabel, historyFilter != HistoryFilter.ALL);
        drawReadingWash(g, rowX - 6, rowY - 5, rowW + 12, visible * rowH + 8);

        if (current.isEmpty()) {
            g.drawString(this.font, Component.literal(tab == TAB_LOOT ? "Nothing has been entered here yet." : "No matching chronicle entries yet.")
                    .withStyle(ChatFormatting.ITALIC), left + 35, rowY + 16, INK_MUTED, true);
        } else {
            for (int i = 0; i < visible; i++) {
                int idx = scroll + i;
                if (idx >= current.size()) break;
                int y = rowY + i * rowH;
                LedgerEntry e = current.get(idx);
                boolean selected = idx == selectedIndex();
                drawRow(g, rowX, y, rowW, selected, inside(mouseX, mouseY, rowX, y - 1, rowW, rowH - 1));
                drawKindIcon(g, rowX + 6, y + 3, e.kind);
                String stamp = e.timestamp > 0 ? TIME.format(Instant.ofEpochMilli(e.timestamp).atZone(ZoneId.systemDefault())) : "--.--  --:--";
                g.drawString(this.font, stamp, rowX + 18, y + 3, INK_MUTED, true);
                g.drawString(this.font, clip(e.text, rowW - 118), rowX + 92, y + 3, kindColor(e.kind), true);
            }
        }

        drawDetailPanel(g, left + 34, bottom - 118, right - left - 68, 88, "Selected Entry");
        LedgerEntry selected = selectedEntry();
        if (selected != null) {
            drawKindIcon(g, left + 48, bottom - 101, selected.kind);
            String stamp = selected.timestamp > 0 ? TIME.format(Instant.ofEpochMilli(selected.timestamp).atZone(ZoneId.systemDefault())) : "--.--  --:--";
            g.drawString(this.font, stamp, left + 60, bottom - 100, INK_MUTED, true);
            drawWrappedPaged(g, selected.text, left + 48, bottom - 84, right - left - 96, 7, kindColor(selected.kind), currentDetailScroll());
            g.drawString(this.font, "Scroll over this panel for full text", right - 204, bottom - 100, INK_MUTED, true);
        } else {
            g.drawString(this.font, Component.literal("Click an entry to read it in full.")
                    .withStyle(ChatFormatting.ITALIC), left + 48, bottom - 84, INK_MUTED, true);
        }

        drawFooterWash(g, left, right, bottom);
        drawFooter(g, left, right, bottom, current.size(), visible);
    }

    private void renderGrimoire(GuiGraphics g, int left, int top, int right, int bottom, int mouseX, int mouseY) {
        if (openCardId >= 0) {
            renderCardDetail(g, left, top, right, bottom, mouseX, mouseY);
            return;
        }

        List<String> current = grimoireView();
        int visible = GRIMOIRE_VISIBLE;
        int rowX = left + GRIMOIRE_LEFT_X;
        int rowY = top + 122;
        int rowW = GRIMOIRE_LEFT_W;
        int rowH = 14;
        int max = Math.max(0, current.size() - visible);
        scroll = Math.max(0, Math.min(max, scroll));

        // A translucent ink-wash under the text prevents the parchment's orange
        // highlights from eating thin glyphs while still leaving the book art visible.
        g.fill(left + 48, top + 84, left + 326, bottom - 48, 0x52110F0D);
        g.fill(left + 350, top + 84, right - 48, bottom - 48, 0x52110F0D);
        g.fill(left + 339, top + 84, left + 341, bottom - 48, 0x553A2A1C);

        drawSmallButton(g, left + 190, top + 101, "gfilter", "Filter: " + grimoireFilter.shortLabel, grimoireFilter != GrimoireFilter.ALL);
        g.drawString(this.font, "Search", left + GRIMOIRE_LEFT_X, top + 101, INK_SECONDARY, true);
        g.fill(left + 56, top + 116, left + 320, top + 117, INK_LINE);
        g.fill(left + 360, top + 92, right - 56, top + 93, INK_LINE);

        if (current.isEmpty()) {
            drawWrappedPaged(g, "No entries match the current search and filter.", rowX + 2, rowY + 12, rowW - 8, 8, INK_SECONDARY, 0);
        } else {
            for (int i = 0; i < visible; i++) {
                int idx = scroll + i;
                if (idx >= current.size()) break;
                int y = rowY + i * rowH;
                String raw = current.get(idx);
                String line = grimoireDisplay(raw);
                boolean selected = idx == selectedGrimoire;
                boolean hovered = inside(mouseX, mouseY, rowX, y - 1, rowW, rowH - 1);
                int fill = selected ? 0x806A5541 : hovered ? 0x554B3D31 : 0x00000000;
                if (fill != 0) g.fill(rowX - 3, y - 1, rowX + rowW, y + 11, fill);
                int color = line.contains("???") ? INK_MUTED
                        : line.startsWith("—") ? INK_HEADER
                        : grimoireCardId(raw) >= 0 ? cardKindColor(ForbiddenBargain.byId(grimoireCardId(raw)), true)
                        : grimoireGameId(raw) >= 0 ? 0xFFD6EFF4
                        : grimoireSilasId(raw) != null ? 0xFFD8C6A1
                        : INK_PRIMARY;
                g.drawString(this.font, clip(line, rowW - 8), rowX + 2, y + 2, color, true);
            }
        }

        int px = left + GRIMOIRE_RIGHT_X;
        int pw = GRIMOIRE_RIGHT_W;
        g.drawString(this.font, "SELECTED NOTE", px, top + 88, INK_HEADER, true);
        g.fill(px, top + 99, px + pw, top + 100, INK_LINE);
        String raw = selectedGrimoireLine();
        if (raw == null) {
            drawWrappedPaged(g, "Choose a line on the left page. Card entries open into their own pages.", px, top + 112, pw, 12, INK_SECONDARY, 0);
        } else {
            String display = grimoireDisplay(raw);
            int cardId = grimoireCardId(raw);
            if (cardId >= 0) {
                ForbiddenBargain card = ForbiddenBargain.byId(cardId);
                if (card != null) {
                    String state = grimoireCardState(raw);
                    g.drawString(this.font, clip(card.title, pw), px, top + 112, cardKindColor(card, true), true);
                    g.drawString(this.font, card.kind.name() + " · " + state, px, top + 126, INK_SECONDARY, true);
                    g.drawString(this.font, "Current", px, top + 145, INK_HEADER, true);
                    drawWrappedPaged(g, grimoireCardRuntime(raw), px, top + 157, pw, 5, INK_PRIMARY, 0);
                    g.drawString(this.font, "Terms", px, top + 216, INK_HEADER, true);
                    drawWrappedPaged(g, grimoireCardTerms(raw, card), px, top + 228, pw, 5, INK_PRIMARY, 0);
                    if ("SIGNED".equals(state)) drawSmallButton(g, px + 45, bottom - 58, "openpage", "Open page", false);
                    else if ("SEEN".equals(state)) g.drawString(this.font, "Sign it to identify this page.", px, bottom - 50, INK_MUTED, true);
                }
            } else {
                String silasId = grimoireSilasId(raw);
                if (silasId != null) {
                    g.drawString(this.font, "UNRESOLVED TESTIMONY", px, top + 112, 0xFFD8C6A1, true);
                    g.drawString(this.font, clip(grimoireSilasFamily(raw), pw), px, top + 126, INK_SECONDARY, true);
                    g.fill(px, top + 141, px + pw, top + 142, INK_LINE);
                    drawWrappedPaged(g, grimoireSilasBody(raw), px, top + 153, pw, 14, INK_PRIMARY, detailScrollGrimoire);
                } else {
                int gameId = grimoireGameId(raw);
                if (gameId >= 0) {
                    MinigameType game = MinigameType.byOrdinal(gameId);
                    g.drawString(this.font, clip(prettify(game.id), pw), px, top + 112, 0xFFD6EFF4, true);
                    g.drawString(this.font, clip(grimoireGameState(raw), pw), px, top + 126, INK_SECONDARY, true);
                    g.drawString(this.font, "Performance record", px, top + 146, INK_HEADER, true);
                    drawWrappedPaged(g, grimoireGameRuntime(raw), px, top + 159, pw, 10, INK_PRIMARY, detailScrollGrimoire);
                    g.drawString(this.font, "Mastery never weakens a seal.", px, bottom - 63, INK_MUTED, true);
                    g.drawString(this.font, "It permits stranger forms to notice you.", px, bottom - 50, INK_MUTED, true);
                } else {
                    g.drawString(this.font, clip(display, pw), px, top + 112, display.contains("???") ? INK_MUTED : INK_PRIMARY, true);
                    drawWrappedPaged(g, display, px, top + 132, pw, 13, INK_PRIMARY, detailScrollGrimoire);
                }
                }
            }
        }

        String footer = current.isEmpty() ? "0 entries" : (scroll + 1) + "-" + Math.min(current.size(), scroll + visible) + " / " + current.size();
        g.fill(left + 46, bottom - 40, right - 46, bottom - 24, 0x74100D0A);
        g.drawString(this.font, footer, left + GRIMOIRE_LEFT_X, bottom - 34, INK_MUTED, true);
        g.drawString(this.font, "Wheel / arrows", left + 166, bottom - 34, INK_MUTED, true);
        g.drawString(this.font, "Ctrl+F search · Left/Right tabs", left + 314, bottom - 34, INK_MUTED, true);
    }

    private void renderCardDetail(GuiGraphics g, int left, int top, int right, int bottom, int mouseX, int mouseY) {
        ForbiddenBargain card = ForbiddenBargain.byId(openCardId);
        if (card == null) {
            openCardId = -1;
            return;
        }
        String raw = rawCardLine(openCardId);
        String state = raw == null ? "SIGNED" : grimoireCardState(raw);
        String runtime = raw == null ? "No live state was returned." : grimoireCardRuntime(raw);
        int lx = left + GRIMOIRE_LEFT_X;
        int rx = left + GRIMOIRE_RIGHT_X;
        int pageW = GRIMOIRE_RIGHT_W;

        g.fill(left + 44, top + 78, left + 258, bottom - 42, 0x52110F0D);
        g.fill(left + 286, top + 78, right - 44, bottom - 42, 0x52110F0D);
        drawSmallButton(g, lx, top + 82, "backpage", "< Index", false);
        drawSmallButton(g, rx + pageW - SMALL_W, top + 82, "synergy", cardSynergyMode ? "Terms" : "Synergy", cardSynergyMode);
        g.drawString(this.font, cardSynergyMode ? "CARD SYNERGY" : "IDENTIFIED CARD", rx, top + 87, INK_HEADER, true);
        g.fill(lx, top + 107, lx + pageW, top + 108, INK_LINE);
        g.fill(rx, top + 107, rx + pageW, top + 108, INK_LINE);

        if (cardSynergyMode) {
            renderCardSynergy(g, card, state, lx, rx, top, bottom, pageW);
            renderCardPager(g, left, right, bottom, lx, rx);
            return;
        }

        int accent = cardKindColor(card, true);
        List<String> titleLines = wrap(card.title, pageW);
        for (int i = 0; i < Math.min(2, titleLines.size()); i++)
            g.drawString(this.font, titleLines.get(i), lx, top + 118 + i * 12, accent, false);
        g.drawString(this.font, card.kind.name() + " · " + state, lx, top + 145, INK_SECONDARY, true);
        if (card.minResolved > 0)
            g.drawString(this.font, "EMERGENCE · " + card.minResolved + " wards", lx, top + 155, 0xFFD8C9A8, false);

        g.drawString(this.font, "WHAT IT DOES", lx, top + 166, INK_HEADER, true);
        drawWrappedPaged(g, grimoireCardTerms(raw, card), lx, top + 178, pageW, 10, INK_PRIMARY, detailScrollGrimoire);

        g.drawString(this.font, "CURRENT STATE", rx, top + 118, INK_HEADER, true);
        drawWrappedPaged(g, runtime, rx, top + 132, pageW, 6, INK_PRIMARY, 0);
        g.drawString(this.font, "HOW IT ENDS", rx, top + 204, INK_HEADER, true);
        drawWrappedPaged(g, card.reliefText(), rx, top + 218, pageW, 8, INK_PRIMARY, 0);

        if (grimoireCardTerms(raw, card).toLowerCase(Locale.ROOT).contains("weakness") || runtime.toLowerCase(Locale.ROOT).contains("weakness")) {
            g.drawString(this.font, "Weakness source: this card", rx, bottom - 62, 0xFFF08A98, true);
        }
        g.drawString(this.font, "Grimoire > Cards > " + clip(card.title, 190), lx, bottom - 76, INK_MUTED, true);
        renderCardPager(g, left, right, bottom, lx, rx);
    }

    private void renderCardSynergy(GuiGraphics g, ForbiddenBargain card, String state,
                                   int lx, int rx, int top, int bottom, int pageW) {
        int accent = cardKindColor(card, true);
        CardEcology.Family family = CardEcology.family(card);
        CardMaster lineage = CardLineage.affinity(card);
        List<CardConjunctions.Conjunction> related = CardConjunctions.related(card);

        g.drawString(this.font, clip(card.title, pageW), lx, top + 118, accent, false);
        g.drawString(this.font, card.kind.name() + " · " + state, lx, top + 132, INK_SECONDARY, true);
        g.drawString(this.font, "FAMILY", lx, top + 153, INK_HEADER, true);
        g.drawString(this.font, family.title, lx, top + 166, 0xFFD6EFF4, true);
        drawWrappedPaged(g, family.detail, lx, top + 178, pageW, 3, INK_SECONDARY, 0);

        g.drawString(this.font, "MASTER LINEAGE", lx, top + 220, INK_HEADER, true);
        String lineageText = lineage == null ? "No authored master lineage is recorded." : lineage.title + " affinity";
        drawWrappedPaged(g, lineageText, lx, top + 233, pageW, 3, lineage == null ? INK_MUTED : 0xFFD8C6A1, 0);

        g.drawString(this.font, "HOUSE RESPONSE", lx, top + 273, INK_HEADER, true);
        List<CardEcology.Family> responses = CardEcology.responsesTo(family);
        StatEntry liveFamily = analysisStat(family.title);
        String live = liveFamily == null ? "" : "Current: " + liveFamily.value + ". ";
        String responseText = live + (responses.isEmpty() ? "No dedicated counter-family."
                : "At high Heat the House may answer with " + joinFamilyTitles(responses) + ".");
        drawWrappedPaged(g, responseText, lx, top + 286, pageW, 4, INK_PRIMARY, 0);

        g.drawString(this.font, "CONJUNCTIONS", rx, top + 118, INK_HEADER, true);
        if (related.isEmpty()) {
            drawWrappedPaged(g, "No documented conjunction uses this card. It may still interact through ordinary card rules.",
                    rx, top + 134, pageW, 6, INK_MUTED, 0);
        } else {
            int yy = top + 134;
            for (CardConjunctions.Conjunction conjunction : related) {
                boolean known = conjunctionKnown(conjunction);
                ForbiddenBargain partner = conjunction.partnerOf(card);
                String title = known ? "[KNOWN] " + conjunction.title : "[UNDOCUMENTED] ???";
                g.drawString(this.font, clip(title, pageW), rx, yy, known ? 0xFFD9B5F4 : INK_MUTED, true);
                yy += 12;
                String pair;
                if (known && partner != null) {
                    String partnerRaw = rawCardLine(partner.id);
                    String partnerState = partnerRaw == null ? "UNSEEN" : grimoireCardState(partnerRaw);
                    pair = "Partner: " + partner.title + " · " + partnerState;
                } else pair = "Partner remains unreadable until discovery.";
                drawWrappedPaged(g, pair, rx, yy, pageW, 2, INK_SECONDARY, 0);
                yy += 25;
                if (known) {
                    drawWrappedPaged(g, conjunction.effect, rx, yy, pageW, 3, INK_PRIMARY, 0);
                    yy += 38;
                }
                if (yy > bottom - 118) break;
            }
        }

        g.drawString(this.font, "REVISION PATH", rx, bottom - 105, INK_HEADER, true);
        String revision = CardEvolution.branchable(card)
                ? "Revised copies can split into alternate clauses; conjunction identity stays attached to the card."
                : "No alternate revision branch is documented for this law.";
        drawWrappedPaged(g, revision, rx, bottom - 92, pageW, 4, INK_SECONDARY, 0);
        g.drawString(this.font, "S toggles Terms / Synergy", rx, bottom - 48, INK_MUTED, true);
    }

    private void renderCardPager(GuiGraphics g, int left, int right, int bottom, int lx, int rx) {
        List<Integer> ids = identifiedCardIds();
        int index = ids.indexOf(openCardId);
        String counter = index >= 0 ? (index + 1) + " / " + ids.size() : "";
        g.drawCenteredString(this.font, counter, (left + right) / 2, bottom - 36, INK_MUTED);
        if (ids.size() > 1) {
            drawSmallButton(g, lx, bottom - 58, "prevpage", "< Previous", false);
            drawSmallButton(g, rx + 60, bottom - 58, "nextpage", "Next >", false);
        }
    }

    private boolean conjunctionKnown(CardConjunctions.Conjunction conjunction) {
        if (conjunction == null) return false;
        for (String raw : grimoireLines) {
            String display = grimoireDisplay(raw);
            if (display != null && !display.contains("???") && display.contains(conjunction.title)) return true;
        }
        return false;
    }

    private StatEntry analysisStat(String label) {
        if (label == null) return null;
        for (StatEntry entry : analysisLines) if (label.equalsIgnoreCase(entry.label)) return entry;
        return null;
    }

    private static String joinFamilyTitles(List<CardEcology.Family> families) {
        if (families == null || families.isEmpty()) return "none";
        if (families.size() == 1) return families.get(0).title;
        if (families.size() == 2) return families.get(0).title + " and " + families.get(1).title;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < families.size(); i++) {
            if (i > 0) out.append(i == families.size() - 1 ? ", and " : ", ");
            out.append(families.get(i).title);
        }
        return out.toString();
    }

    private void renderRules(GuiGraphics g, int left, int top, int right, int bottom) {
        int x = left + 62;
        int y = top + 94;
        int w = right - left - 124;
        int contentBottom = bottom - 50;
        drawReadingWash(g, x - 14, y - 10, w + 28, contentBottom - y + 14);

        if (ruleLines.isEmpty()) {
            g.drawCenteredString(this.font, Component.literal("No rules have been written yet.").withStyle(ChatFormatting.ITALIC),
                    (left + right) / 2, y + 40, INK_MUTED);
            return;
        }

        scroll = Math.max(0, Math.min(scroll, Math.max(0, ruleLines.size() - 1)));
        int drawn = 0;
        for (int idx = scroll; idx < ruleLines.size(); idx++) {
            String raw = ruleLines.get(idx);
            if (raw == null || raw.isBlank()) continue;
            boolean header = raw.startsWith("—");
            List<String> wrapped = wrap(raw, w - (header ? 0 : 14));
            int blockH = header ? 22 : wrapped.size() * 12 + 9;
            if (y + blockH > contentBottom) break;
            if (header) {
                if (drawn > 0) y += 5;
                g.fill(x, y + 13, x + w, y + 14, INK_LINE);
                g.drawString(this.font, raw, x, y, INK_HEADER, true);
                y += 22;
            } else {
                int color = raw.startsWith("[LOCKED]") ? INK_MUTED : raw.startsWith("[OPEN]") ? 0xFFB9DFC0 : INK_PRIMARY;
                g.fill(x + 1, y + 1, x + 4, y + Math.max(10, wrapped.size() * 12 - 2), raw.startsWith("[LOCKED]") ? 0x55715A45 : 0x556B8B75);
                for (int line = 0; line < wrapped.size(); line++)
                    g.drawString(this.font, wrapped.get(line), x + 12, y + line * 12, color, true);
                y += blockH;
            }
            drawn++;
        }

        drawFooterWash(g, left, right, bottom);
        g.drawString(this.font, "Rules index " + (scroll + 1) + " / " + ruleLines.size(), left + 36, bottom - 18, INK_MUTED, true);
        g.drawString(this.font, "Wheel / ↑ ↓ · unlocked rules only", right - 206, bottom - 18, INK_MUTED, true);
    }

    private void renderStatus(GuiGraphics g, int left, int top, int right, int bottom) {
        int visible = 8;
        int max = Math.max(0, statLines.size() - visible);
        scroll = Math.max(0, Math.min(max, scroll));
        int startX = left + 36;
        int startY = top + 94;
        int cardW = (right - left - 80) / 2;
        int cardH = 66;
        for (int i = 0; i < visible; i++) {
            int idx = scroll + i;
            if (idx >= statLines.size()) break;
            int col = i & 1;
            int row = i / 2;
            int x = startX + col * (cardW + 8);
            int y = startY + row * 70;
            drawStatCard(g, x, y, cardW, cardH, statLines.get(idx));
        }
        drawFooterWash(g, left, right, bottom);
        g.drawString(this.font, "Live sync: ~2 seconds", left + 28, bottom - 18, INK_MUTED, true);
        g.drawString(this.font, "Wheel / ↑ ↓", right - 92, bottom - 18, INK_MUTED, true);
    }

    private void renderAnalysis(GuiGraphics g, int left, int top, int right, int bottom) {
        int visible = 8;
        int max = Math.max(0, analysisLines.size() - visible);
        scroll = Math.max(0, Math.min(max, scroll));
        int startX = left + 36;
        int startY = top + 94;
        int cardW = (right - left - 80) / 2;
        int cardH = 66;
        for (int i = 0; i < visible; i++) {
            int idx = scroll + i;
            if (idx >= analysisLines.size()) break;
            int col = i & 1;
            int row = i / 2;
            int x = startX + col * (cardW + 8);
            int y = startY + row * 70;
            drawStatCard(g, x, y, cardW, cardH, analysisLines.get(idx));
        }
        drawFooterWash(g, left, right, bottom);
        g.drawString(this.font, "Rolling form · ecology · mastery · relationships", left + 28, bottom - 18, INK_MUTED, true);
        g.drawString(this.font, "Wheel / ↑ ↓", right - 92, bottom - 18, INK_MUTED, true);
    }

    private void drawStatCard(GuiGraphics g, int x, int y, int w, int h, StatEntry stat) {
        int accent = switch (stat.category) {
            case "CURSE", "DEATH", "RISK" -> 0xFFF09AAA;
            case "MASTER", "DEALER", "RELIC" -> 0xFFF1D19A;
            case "CONTRACT" -> 0xFFA7D9EC;
            case "RITUAL" -> 0xFFAFE2D3;
            case "COVENANT" -> 0xFFEFA6B4;
            case "WAGER" -> 0xFFD3B8F0;
            case "OBJECTIVE" -> 0xFFB8DAEE;
            case "EFFECT" -> 0xFFF2B0BA;
            case "LAW", "ACTIVE", "CONDITION" -> 0xFFA9DDD2;
            case "NEXT" -> 0xFFF3D58B;
            case "CHARGE", "TIMER" -> 0xFFD5C0ED;
            case "SHARD" -> 0xFFD8B8F3;
            case "SCAR" -> 0xFFF0A8B0;
            case "ANALYSIS" -> 0xFFC4E2D0;
            default -> 0xFFD7DDC9;
        };
        g.fill(x, y, x + w, y + h, 0x8A241A13);
        g.fill(x, y, x + 3, y + h, accent);
        g.fill(x + 5, y + 4, x + w - 4, y + 5, 0x335A4734);
        g.drawString(this.font, stat.label, x + 8, y + 7, INK_HEADER, true);
        g.drawString(this.font, clip(stat.value, w - 16), x + 8, y + 18, accent, false);
        List<String> detail = wrap(stat.detail, w - 16);
        if (!detail.isEmpty()) g.drawString(this.font, detail.get(0), x + 8, y + 30, INK_SECONDARY, true);
        if (detail.size() > 1) g.drawString(this.font, detail.get(1), x + 8, y + 40, INK_SECONDARY, true);
        int[] progress = parseProgress(stat.value);
        if (progress != null) {
            int barX = x + 8;
            int barY = y + 52;
            int barW = Math.max(12, w - 16);
            g.fill(barX, barY, barX + barW, barY + 4, 0x443A2D24);
            int filled = progress[1] <= 0 ? 0 : Math.min(barW, Math.round(barW * (progress[0] / (float) progress[1])));
            if (filled > 0) g.fill(barX, barY, barX + filled, barY + 4, accent);
        }
    }

    private static int[] parseProgress(String value) {
        if (value == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d+)\\s*/\\s*(\\d+)\\b").matcher(value);
        if (!m.find()) return null;
        try { return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))}; }
        catch (NumberFormatException ignored) { return null; }
    }

    private void renderLastWard(GuiGraphics g, int left, int top, int right, int bottom) {
        drawDetailPanel(g, left + 46, top + 94, right - left - 92, 242, "Last Seal Testimony");
        int cx = (left + right) / 2;
        int y = top + 120;
        if (lastWard == null || lastWard.text.isBlank()) {
            g.drawCenteredString(this.font, Component.literal("No ward has been summarized while this book was present.")
                    .withStyle(ChatFormatting.ITALIC), cx, y + 42, INK_MUTED);
            return;
        }
        drawKindIcon(g, cx - 8, y + 2, lastWard.kind);
        g.drawCenteredString(this.font,
                Component.literal("SUMMARY_WIN".equals(lastWard.kind) ? "WARD RESOLVED" : "WARD LOST")
                        .withStyle(ChatFormatting.BOLD), cx, y + 18, "SUMMARY_WIN".equals(lastWard.kind) ? 0xFFCDE5A6 : 0xFFF0A0A7);
        if (lastWard.timestamp > 0) {
            String stamp = TIME.format(Instant.ofEpochMilli(lastWard.timestamp).atZone(ZoneId.systemDefault()));
            g.drawCenteredString(this.font, stamp, cx, y + 34, INK_MUTED);
        }
        List<String> lines = wrap(lastWard.text, right - left - 110);
        for (int i = 0; i < Math.min(10, lines.size()); i++) {
            g.drawCenteredString(this.font, lines.get(i), cx, y + 58 + i * 14, INK_PRIMARY);
        }
        g.fill(left + 42, bottom - 43, right - 42, bottom - 27, 0x74100D0A);
        g.drawCenteredString(this.font, Component.literal("The ledger records only while carried, held, or worn.").withStyle(ChatFormatting.ITALIC),
                cx, bottom - 36, INK_MUTED);
    }

    private void drawFooter(GuiGraphics g, int left, int right, int bottom, int total, int visible) {
        String footer = total == 0 ? "0 entries" : (scroll + 1) + "-" + Math.min(total, scroll + visible) + " / " + total;
        g.drawString(this.font, footer, left + 28, bottom - 18, INK_MUTED, true);
        g.drawString(this.font, "Wheel / ↑ ↓ / PgUp PgDn", right - 136, bottom - 18, INK_MUTED, true);
    }

    private void drawDetailPanel(GuiGraphics g, int x, int y, int w, int h, String title) {
        g.blit(WIDGETS, x, y, 0, 128, 184, 72, 256, 256);
        // Long testimony sits on a dedicated ink wash instead of directly on
        // the illustrated parchment. The book art remains visible at the edge.
        g.fill(x + 7, y + 5, x + w - 7, y + h - 5, 0x94120E0B);
        g.fill(x + 10, y + 7, x + w - 10, y + 8, 0x4A70533B);
        g.drawString(this.font, title, x + 10, y + 8, INK_HEADER, true);
    }

    private void drawRow(GuiGraphics g, int x, int y, int w, boolean selected, boolean hovered) {
        int v = pressed("row") && hovered ? 178 : selected ? 162 : hovered ? 146 : 128;
        g.blit(WIDGETS, x, y - 1, 0, v, Math.min(184, w), 16, 256, 256);
        if (w > 184) {
            int fill = selected ? 0x4BB59B72 : hovered ? 0x2CBCA57B : 0x1B8D6E4F;
            g.fill(x + 10, y + 1, x + w - 10, y + 13, fill);
        }
    }

    private void drawTab(GuiGraphics g, int x, int y, int index, String label) {
        boolean active = tab == index;
        boolean pressed = pressed("tab" + index);
        int v = pressed ? 44 : active ? 22 : 0;
        g.blit(WIDGETS, x, y, 0, v, TAB_W, TAB_H, 256, 256);
        int color = pressed ? 0xFFF1D8 : active ? 0xF6E5C2 : 0xD2C0A1;
        g.drawCenteredString(this.font, label, x + TAB_W / 2, y + 7, color);
    }

    private void drawSmallButton(GuiGraphics g, int x, int y, String key, String label, boolean active) {
        boolean pressed = pressed(key);
        int v = pressed ? 104 : active ? 86 : 68;
        g.blit(WIDGETS, x, y, 0, v, SMALL_W, SMALL_H, 256, 256);
        int color = pressed ? 0xFFF1D8 : active ? 0xECDCBD : 0xD4C2A4;
        g.drawCenteredString(this.font, clip(label, SMALL_W - 10), x + SMALL_W / 2, y + 5, color);
    }

    private List<LedgerEntry> filteredCurrent() {
        List<LedgerEntry> src = tab == TAB_LOOT ? lootLines : recordLines;
        List<LedgerEntry> out = new ArrayList<>();
        String needle = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        for (LedgerEntry e : src) {
            if (!historyFilter.matches(tab, e.kind)) continue;
            if (!needle.isEmpty()) {
                String stamp = e.timestamp > 0 ? TIME.format(Instant.ofEpochMilli(e.timestamp).atZone(ZoneId.systemDefault())) : "";
                String hay = (stamp + " " + e.kind + " " + e.text).toLowerCase(Locale.ROOT);
                if (!hay.contains(needle)) continue;
            }
            out.add(e);
        }
        return out;
    }

    private List<LedgerEntry> recordView() {
        int oldTab = tab;
        tab = TAB_CHRONICLE;
        List<LedgerEntry> out = filteredCurrent();
        tab = oldTab;
        return out;
    }

    private List<String> grimoireView() {
        List<String> out = new ArrayList<>();
        String needle = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        String currentHeader = null;
        boolean headerAdded = false;
        for (String raw : grimoireLines) {
            if (raw == null || raw.isBlank()) continue;
            String display = grimoireDisplay(raw);
            if (display.startsWith("—")) {
                currentHeader = display;
                headerAdded = false;
                continue;
            }
            if (!grimoireFilter.matches(currentHeader)) continue;

            int cardId = grimoireCardId(raw);
            ForbiddenBargain card = cardId >= 0 ? ForbiddenBargain.byId(cardId) : null;
            if (!matchesGrimoireKindFilter(card)) continue;

            if (!needle.isEmpty()) {
                StringBuilder hay = new StringBuilder(display.toLowerCase(Locale.ROOT));
                if (card != null) {
                    hay.append(' ').append(card.title.toLowerCase(Locale.ROOT));
                    hay.append(' ').append(card.kind.name().toLowerCase(Locale.ROOT));
                    hay.append(' ').append(card.debtText.toLowerCase(Locale.ROOT));
                    hay.append(' ').append(card.reliefText().toLowerCase(Locale.ROOT));
                    hay.append(' ').append(grimoireCardRuntime(raw).toLowerCase(Locale.ROOT));
                } else if (grimoireGameId(raw) >= 0) {
                    hay.append(' ').append(grimoireGameState(raw).toLowerCase(Locale.ROOT));
                    hay.append(' ').append(grimoireGameRuntime(raw).toLowerCase(Locale.ROOT));
                } else if (grimoireSilasId(raw) != null) {
                    hay.append(' ').append(grimoireSilasFamily(raw).toLowerCase(Locale.ROOT));
                    hay.append(' ').append(grimoireSilasBody(raw).toLowerCase(Locale.ROOT));
                }
                if (!hay.toString().contains(needle)) continue;
            }
            if (currentHeader != null && !headerAdded) {
                out.add(currentHeader);
                headerAdded = true;
            }
            out.add(raw);
        }
        return out;
    }

    private boolean matchesGrimoireKindFilter(ForbiddenBargain card) {
        return switch (grimoireFilter) {
            case CURSE -> card != null && card.kind == ForbiddenBargain.Kind.CURSE;
            case DEATH -> card != null && card.kind == ForbiddenBargain.Kind.DEATH;
            case MASTER_CARDS -> card != null && card.kind == ForbiddenBargain.Kind.MASTER;
            case CONTRACT -> card != null && card.kind == ForbiddenBargain.Kind.CONTRACT;
            case RITUAL -> card != null && card.kind == ForbiddenBargain.Kind.RITUAL;
            case COVENANT -> card != null && card.kind == ForbiddenBargain.Kind.COVENANT;
            default -> true;
        };
    }

    private String grimoireDisplay(String raw) {
        if (raw == null) return "";
        int i = raw.indexOf(SEP);
        return i < 0 ? raw : raw.substring(0, i);
    }

    private String grimoireSilasId(String raw) {
        if (raw == null || !raw.contains(SEP)) return null;
        String[] p = raw.split(SEP, 5);
        return p.length >= 5 && "SILAS".equals(p[1]) ? p[2] : null;
    }

    private String grimoireSilasFamily(String raw) {
        if (raw == null || !raw.contains(SEP)) return "Unresolved source";
        String[] p = raw.split(SEP, 5);
        return p.length >= 5 && "SILAS".equals(p[1]) ? p[3] : "Unresolved source";
    }

    private String grimoireSilasBody(String raw) {
        if (raw == null || !raw.contains(SEP)) return "";
        String[] p = raw.split(SEP, 5);
        return p.length >= 5 && "SILAS".equals(p[1]) ? Component.translatable(p[4]).getString() : "";
    }

    private int grimoireCardId(String raw) {
        if (raw == null || !raw.contains(SEP)) return -1;
        String[] p = raw.split(SEP, 6);
        if (p.length < 4 || !"CARD".equals(p[1])) return -1;
        try { return Integer.parseInt(p[2]); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private String grimoireCardState(String raw) {
        if (raw == null) return "UNKNOWN";
        String[] p = raw.split(SEP, 6);
        return p.length >= 4 && "CARD".equals(p[1]) ? p[3] : "UNKNOWN";
    }

    private String grimoireCardTerms(String raw, ForbiddenBargain card) {
        String[] fields = raw == null ? new String[0] : raw.split(SEP, 6);
        return fields.length > 5 ? fields[5] : card.debtText;
    }

    private String grimoireCardRuntime(String raw) {
        if (raw == null) return "No live state available.";
        String[] p = raw.split(SEP, 6);
        return p.length >= 5 && "CARD".equals(p[1]) ? p[4] : "No live state available.";
    }

    private static String prettify(String raw) {
        if (raw == null || raw.isBlank()) return "Unknown";
        StringBuilder out = new StringBuilder();
        for (String part : raw.replace('_', ' ').split(" ")) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private int grimoireGameId(String raw) {
        if (raw == null || !raw.contains(SEP)) return -1;
        String[] p = raw.split(SEP, 6);
        if (p.length < 4 || !"GAME".equals(p[1])) return -1;
        try { return Integer.parseInt(p[2]); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private String grimoireGameState(String raw) {
        if (raw == null) return "UNKNOWN";
        String[] p = raw.split(SEP, 6);
        return p.length >= 4 && "GAME".equals(p[1]) ? p[3] : "UNKNOWN";
    }

    private String grimoireGameRuntime(String raw) {
        if (raw == null) return "No mastery record available.";
        String[] p = raw.split(SEP, 6);
        return p.length >= 5 && "GAME".equals(p[1]) ? p[4] : "No mastery record available.";
    }

    private String rawCardLine(int cardId) {
        for (String raw : grimoireLines) if (grimoireCardId(raw) == cardId) return raw;
        return null;
    }

    private List<Integer> identifiedCardIds() {
        List<Integer> ids = new ArrayList<>();
        for (String raw : grimoireLines) {
            int id = grimoireCardId(raw);
            if (id < 0 || !"SIGNED".equals(grimoireCardState(raw))) continue;
            if (!ids.contains(id)) ids.add(id);
        }
        return ids;
    }

    private int cardKindColor(ForbiddenBargain card, boolean bright) {
        if (card == null) return bright ? 0x6A5948 : 0x4C4036;
        return switch (card.kind) {
            case DEATH -> bright ? 0xF29AAA : 0x6D2631;
            case CURSE, COVENANT -> bright ? 0xEFA6B4 : 0x773640;
            case EPIC -> bright ? 0xF3D985 : 0x6F5D2F;
            case UNIQUE -> bright ? 0x8FDCD7 : 0x3E6663;
            case MASTER -> bright ? 0xB1C9ED : 0x4F6077;
            case REMEDY -> bright ? 0xB4DEA9 : 0x496347;
            case CONTRACT -> bright ? 0xA7D9EC : 0x496A78;
            case RITUAL -> bright ? 0xAFE2D3 : 0x49695F;
            default -> bright ? 0xE7D6C0 : 0x59483A;
        };
    }

    private void stepCardPage(int delta) {
        List<Integer> ids = identifiedCardIds();
        if (ids.isEmpty()) { openCardId = -1; return; }
        int at = ids.indexOf(openCardId);
        if (at < 0) at = 0;
        openCardId = ids.get(Math.floorMod(at + delta, ids.size()));
        detailScrollGrimoire = 0;
        pageFlipDirection = delta >= 0 ? -1 : 1;
        triggerPageFlip();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, delta >= 0 ? 1.03f : 0.88f));
        Sfx.play(WardSounds.MARK, 0.10f, delta >= 0 ? 1.18f : 0.94f);
    }

    private LedgerEntry selectedEntry() {
        List<LedgerEntry> current = filteredCurrent();
        if (current.isEmpty()) return null;
        int idx = Math.max(0, Math.min(selectedIndex(), current.size() - 1));
        return current.get(idx);
    }

    private String selectedGrimoireLine() {
        List<String> current = grimoireView();
        if (current.isEmpty()) return null;
        int idx = Math.max(0, Math.min(selectedGrimoire, current.size() - 1));
        return current.get(idx);
    }

    private int selectedIndex() {
        return tab == TAB_LOOT ? selectedLoot : selectedRecord;
    }

    private void setSelectedIndex(int value) {
        if (tab == TAB_LOOT) selectedLoot = value;
        else selectedRecord = value;
    }

    private void clampSelections() {
        selectedLoot = Math.max(0, Math.min(selectedLoot, Math.max(0, lootLines.size() - 1)));
        selectedRecord = Math.max(0, Math.min(selectedRecord, Math.max(0, recordView().size() - 1)));
        selectedGrimoire = Math.max(0, Math.min(selectedGrimoire, Math.max(0, grimoireView().size() - 1)));
    }

    private void setPressed(String id) {
        pressedWidget = id;
        pressTicks = 4;
    }

    private boolean pressed(String id) {
        return pressTicks > 0 && pressedWidget.equals(id);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float uiScale = ledgerScale();
        mouseX /= uiScale;
        mouseY /= uiScale;
        // A click outside the edit box returns keyboard navigation to the ledger.
        // Clicking the edit box below immediately focuses it again.
        if (searchBox != null && searchBox.isFocused()) blurSearchBox();
        int left = panelLeft();
        int top = panelTop();
        int right = left + PANEL_W;
        int bottom = top + PANEL_H;
        int tabY = top + 52;
        int step = TAB_W + TAB_GAP;
        int tabsLeft = left + (PANEL_W - (TAB_W * TAB_COUNT + TAB_GAP * (TAB_COUNT - 1))) / 2;
        if (clickTab(mouseX, mouseY, tabsLeft, tabY, TAB_LOOT)) return true;
        if (clickTab(mouseX, mouseY, tabsLeft + step, tabY, TAB_CHRONICLE)) return true;
        if (clickTab(mouseX, mouseY, tabsLeft + step * 2, tabY, TAB_LAST_WARD)) return true;
        if (clickTab(mouseX, mouseY, tabsLeft + step * 3, tabY, TAB_GRIMOIRE)) return true;
        if (clickTab(mouseX, mouseY, tabsLeft + step * 4, tabY, TAB_RULES)) return true;
        if (clickTab(mouseX, mouseY, tabsLeft + step * 5, tabY, TAB_STATUS)) return true;
        if (clickTab(mouseX, mouseY, tabsLeft + step * 6, tabY, TAB_ANALYSIS)) return true;

        if (tab == TAB_GRIMOIRE && openCardId >= 0) {
            int lx = left + GRIMOIRE_LEFT_X;
            int rx = left + GRIMOIRE_RIGHT_X;
            if (inside(mouseX, mouseY, lx, top + 82, SMALL_W, SMALL_H)) {
                openCardId = -1;
                detailScrollGrimoire = 0;
                cardSynergyMode = false;
                updateSearchBoxLayout(false);
                triggerPageFlip();
                setPressed("backpage");
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.88f));
                return true;
            }
            if (inside(mouseX, mouseY, rx + GRIMOIRE_RIGHT_W - SMALL_W, top + 82, SMALL_W, SMALL_H)) {
                cardSynergyMode = !cardSynergyMode;
                detailScrollGrimoire = 0;
                setPressed("synergy");
                triggerPageFlip();
                Sfx.play(WardSounds.MARK, 0.18f, cardSynergyMode ? 1.24f : 0.96f);
                return true;
            }
            List<Integer> ids = identifiedCardIds();
            if (ids.size() > 1 && inside(mouseX, mouseY, lx, bottom - 58, SMALL_W, SMALL_H)) {
                setPressed("prevpage");
                stepCardPage(-1);
                return true;
            }
            if (ids.size() > 1 && inside(mouseX, mouseY, rx + 60, bottom - 58, SMALL_W, SMALL_H)) {
                setPressed("nextpage");
                stepCardPage(1);
                return true;
            }
            return true;
        }

        if ((tab == TAB_LOOT || tab == TAB_CHRONICLE) && inside(mouseX, mouseY, right - 130, top + 75, SMALL_W, SMALL_H)) {
            historyFilter = historyFilter.nextFor(tab);
            SESSION_HISTORY[tab] = historyFilter;
            selectedLoot = 0;
            selectedRecord = 0;
            scroll = 0;
            resetCurrentDetailScroll();
            setPressed("filter");
            Sfx.play(WardSounds.EYE_CHOOSE, 0.26f, 0.86f + historyFilter.ordinal() * 0.035f);
            return true;
        }

        if (tab == TAB_GRIMOIRE && inside(mouseX, mouseY, left + 190, top + 101, SMALL_W, SMALL_H)) {
            grimoireFilter = grimoireFilter.next();
            SESSION_GRIMOIRE_FILTER = grimoireFilter;
            selectedGrimoire = 0;
            detailScrollGrimoire = 0;
            scroll = 0;
            clampSelections();
            setPressed("gfilter");
            Sfx.play(WardSounds.EYE_CHOOSE, 0.24f, 1.04f);
            return true;
        }

        if ((tab == TAB_LOOT || tab == TAB_CHRONICLE || (tab == TAB_GRIMOIRE && openCardId < 0)) && searchBox != null
                && searchBox.mouseClicked(mouseX, mouseY, button)) {
            focusSearchBox();
            return true;
        }

        if (tab == TAB_LOOT || tab == TAB_CHRONICLE) {
            List<LedgerEntry> current = filteredCurrent();
            int visible = 11;
            int rowX = left + 34;
            int rowY = top + 108;
            int rowW = right - left - 68;
            int rowH = 17;
            for (int i = 0; i < visible; i++) {
                int idx = scroll + i;
                if (idx >= current.size()) break;
                int y = rowY + i * rowH;
                if (inside(mouseX, mouseY, rowX, y - 1, rowW, rowH - 1)) {
                    setSelectedIndex(idx);
                    resetCurrentDetailScroll();
                    setPressed("row");
                    Sfx.play(WardSounds.MARK, 0.18f, 1.0f);
                    return true;
                }
            }
        } else if (tab == TAB_GRIMOIRE) {
            List<String> current = grimoireView();
            int visible = GRIMOIRE_VISIBLE;
            int rowX = left + GRIMOIRE_LEFT_X;
            int rowY = top + 122;
            int rowW = GRIMOIRE_LEFT_W;
            int rowH = 14;
            for (int i = 0; i < visible; i++) {
                int idx = scroll + i;
                if (idx >= current.size()) break;
                int y = rowY + i * rowH;
                if (inside(mouseX, mouseY, rowX, y - 1, rowW, rowH - 1)) {
                    boolean same = selectedGrimoire == idx;
                    selectedGrimoire = idx;
                    detailScrollGrimoire = 0;
                    setPressed("row");
                    Sfx.play(WardSounds.MARK, 0.18f, 1.0f);
                    int cardId = grimoireCardId(current.get(idx));
                    if (same && cardId >= 0 && "SIGNED".equals(grimoireCardState(current.get(idx)))) {
                        openCardId = cardId;
                        cardSynergyMode = false;
                        updateSearchBoxLayout(false);
                        triggerPageFlip();
                    }
                    return true;
                }
            }
            String raw = selectedGrimoireLine();
            if (raw != null && inside(mouseX, mouseY, left + GRIMOIRE_RIGHT_X + 45, bottom - 58, SMALL_W, SMALL_H)) {
                int cardId = grimoireCardId(raw);
                if (cardId >= 0 && "SIGNED".equals(grimoireCardState(raw))) {
                    openCardId = cardId;
                    detailScrollGrimoire = 0;
                    cardSynergyMode = false;
                    updateSearchBoxLayout(false);
                    triggerPageFlip();
                    setPressed("openpage");
                    Sfx.play(WardSounds.MARK, 0.14f, 1.12f);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        float uiScale = ledgerScale();
        return super.mouseReleased(mouseX / uiScale, mouseY / uiScale, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        float uiScale = ledgerScale();
        return super.mouseDragged(mouseX / uiScale, mouseY / uiScale, button, dragX / uiScale, dragY / uiScale);
    }

    private boolean clickTab(double mouseX, double mouseY, int x, int y, int idx) {
        if (!inside(mouseX, mouseY, x, y, TAB_W, TAB_H)) return false;
        switchToTab(idx);
        return true;
    }

    private void switchToTab(int idx) {
        if (tab != idx) {
            rememberLedgerView();
            pageFlipDirection = idx > tab ? -1 : 1;
            tab = idx;
            sessionTab = idx;
            openCardId = -1;
            cardSynergyMode = false;
            if (tab == TAB_LOOT || tab == TAB_CHRONICLE) historyFilter = SESSION_HISTORY[tab];
            grimoireFilter = SESSION_GRIMOIRE_FILTER;
            detailScrollGrimoire = 0;
            updateSearchBoxLayout(false);
            if (searchBox != null) {
                blurSearchBox();
                searchBox.setValue(SESSION_SEARCH[tab] == null ? "" : SESSION_SEARCH[tab]);
            }
            scroll = Math.max(0, SESSION_SCROLL[tab]);
            clampSelections();
            triggerPageFlip();
        }
        setPressed("tab" + idx);
        Minecraft mc = Minecraft.getInstance();
        float pagePitch = 0.90f + idx * 0.045f;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, pagePitch));
        if ((idx & 1) == 0) Sfx.play(WardSounds.STONE_TURN, 0.14f, 1.02f + idx * 0.03f);
        else Sfx.play(WardSounds.MARK, 0.10f, 1.08f + idx * 0.025f);
        playAmbientForTab();
    }

    private void rememberLedgerView() {
        sessionTab = tab;
        SESSION_SCROLL[tab] = Math.max(0, scroll);
        if (searchBox != null) SESSION_SEARCH[tab] = searchBox.getValue();
        if (tab == TAB_LOOT || tab == TAB_CHRONICLE) SESSION_HISTORY[tab] = historyFilter;
        SESSION_GRIMOIRE_FILTER = grimoireFilter;
    }

    private void triggerPageFlip() {
        pageFlipTicks = 9;
    }

    private void playLedgerOpenSequence() {
        if (this.minecraft == null) return;
        int variant = Math.floorMod((int) (System.nanoTime() >>> 12), 5);
        float pagePitch = 0.86f + variant * 0.055f;
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, pagePitch));
        switch (variant) {
            case 0 -> { Sfx.play(WardSounds.LEDGER_GRIMOIRE_AMBIENT, 0.20f, 0.96f); Sfx.play(WardSounds.MARK, 0.10f, 1.22f); }
            case 1 -> { Sfx.play(WardSounds.LEDGER_CHRONICLE_AMBIENT, 0.19f, 1.05f); Sfx.play(WardSounds.EYE_OPEN, 0.12f, 1.08f); }
            case 2 -> { Sfx.play(WardSounds.LEDGER_LOOT_AMBIENT, 0.20f, 0.92f); this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.92f)); }
            case 3 -> { Sfx.play(WardSounds.LEDGER_STATUS_AMBIENT, 0.18f, 1.08f); Sfx.play(WardSounds.WARD_OPEN, 0.10f, 1.32f); }
            default -> { Sfx.play(WardSounds.LEDGER_LAST_WARD_AMBIENT, 0.19f, 0.90f); Sfx.play(WardSounds.STONE_TURN, 0.10f, 1.16f); }
        }
    }

    private void playAmbientForTab() {
        ambientCooldown = 150 + Math.floorMod((int) (System.nanoTime() >>> 15), 80);
        float drift = 0.94f + Math.floorMod((int) (System.nanoTime() >>> 9), 13) * 0.01f;
        switch (tab) {
            case TAB_LOOT -> Sfx.play(WardSounds.LEDGER_LOOT_AMBIENT, 0.10f, drift);
            case TAB_CHRONICLE -> Sfx.play(WardSounds.LEDGER_CHRONICLE_AMBIENT, 0.10f, drift + 0.03f);
            case TAB_LAST_WARD -> Sfx.play(WardSounds.LEDGER_LAST_WARD_AMBIENT, 0.10f, drift - 0.03f);
            case TAB_GRIMOIRE -> Sfx.play(WardSounds.LEDGER_GRIMOIRE_AMBIENT, 0.11f, drift);
            case TAB_RULES -> Sfx.play(WardSounds.LEDGER_GRIMOIRE_AMBIENT, 0.09f, drift - 0.02f);
            case TAB_STATUS -> Sfx.play(WardSounds.LEDGER_STATUS_AMBIENT, 0.10f, drift + 0.02f);
            case TAB_ANALYSIS -> Sfx.play(WardSounds.LEDGER_STATUS_AMBIENT, 0.09f, drift - 0.01f);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        float uiScale = ledgerScale();
        mouseX /= uiScale;
        mouseY /= uiScale;
        if (tab == TAB_LAST_WARD) return true;
        if (tab == TAB_GRIMOIRE && openCardId >= 0) {
            if (!cardSynergyMode) adjustDetailScroll(delta > 0 ? -1 : 1);
            return true;
        }
        int left = panelLeft();
        int top = panelTop();
        int right = left + PANEL_W;
        int bottom = top + PANEL_H;
        if (tab != TAB_STATUS && tab != TAB_ANALYSIS && tab != TAB_RULES && tab != TAB_GRIMOIRE && inside(mouseX, mouseY, left + 34, bottom - 118, right - left - 68, 88)) {
            if (adjustDetailScroll(delta > 0 ? -1 : 1)) return true;
        }
        adjustScroll(delta > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown() && keyCode == 70) { // Ctrl+F
            if (tab != TAB_LOOT && tab != TAB_CHRONICLE && tab != TAB_GRIMOIRE) switchToTab(TAB_GRIMOIRE);
            if (openCardId >= 0) { openCardId = -1; cardSynergyMode = false; updateSearchBoxLayout(false); }
            if (searchBox != null) { searchBox.setVisible(true); focusSearchBox(); }
            return true;
        }
        if (searchBox != null && searchBox.isFocused() && searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (tab == TAB_GRIMOIRE && openCardId >= 0) {
            if (keyCode == 83) { cardSynergyMode = !cardSynergyMode; detailScrollGrimoire = 0; triggerPageFlip(); return true; }
            if (keyCode == 259) { // backspace
                openCardId = -1;
                cardSynergyMode = false;
                updateSearchBoxLayout(false);
                triggerPageFlip();
                return true;
            }
            if (keyCode == 263) { stepCardPage(-1); return true; }
            if (keyCode == 262) { stepCardPage(1); return true; }
            if (keyCode == 264) { adjustDetailScroll(1); return true; }
            if (keyCode == 265) { adjustDetailScroll(-1); return true; }
        }
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == 263) return switchRelativeTab(-1);
        if (keyCode == 262) return switchRelativeTab(1);
        if (keyCode == 268 && tab != TAB_LAST_WARD) { scroll = 0; return true; } // Home
        if (keyCode == 269 && tab != TAB_LAST_WARD) { scroll = Integer.MAX_VALUE / 4; adjustScroll(0); return true; } // End
        if (tab != TAB_LAST_WARD) {
            if (keyCode == 264) { adjustScroll(1); return true; }
            if (keyCode == 265) { adjustScroll(-1); return true; }
            if (keyCode == 266) { adjustScroll(-9); return true; }
            if (keyCode == 267) { adjustScroll(9); return true; }
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.isFocused() && searchBox.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    private boolean switchRelativeTab(int amount) {
        int next = Math.floorMod(tab + amount, TAB_COUNT);
        switchToTab(next);
        return true;
    }

    private void adjustScroll(int amount) {
        if (tab == TAB_GRIMOIRE && openCardId >= 0) return;
        int max;
        if (tab == TAB_STATUS) max = Math.max(0, statLines.size() - 8);
        else if (tab == TAB_ANALYSIS) max = Math.max(0, analysisLines.size() - 8);
        else if (tab == TAB_RULES) max = Math.max(0, ruleLines.size() - 1);
        else if (tab == TAB_GRIMOIRE) max = Math.max(0, grimoireView().size() - GRIMOIRE_VISIBLE);
        else max = Math.max(0, filteredCurrent().size() - 11);
        int before = scroll;
        scroll = Math.max(0, Math.min(max, scroll + amount));
        if (scroll != before) Sfx.play(WardSounds.PIN_DROP, 0.12f, 1.18f);
    }

    private void drawWrappedPaged(GuiGraphics g, String text, int x, int y, int width, int maxLines, int color, int startLine) {
        List<String> lines = wrap(text, width);
        int clampedStart = Math.max(0, Math.min(startLine, Math.max(0, lines.size() - maxLines)));
        for (int i = 0; i < maxLines; i++) {
            int idx = clampedStart + i;
            if (idx >= lines.size()) break;
            g.drawString(this.font, lines.get(idx), x, y + i * 11, color, true);
        }
        if (lines.size() > maxLines) {
            String page = (clampedStart + 1) + "-" + Math.min(lines.size(), clampedStart + maxLines) + "/" + lines.size();
            g.drawString(this.font, page, x + width - this.font.width(page), y + maxLines * 11, INK_MUTED, true);
        }
    }

    private int currentDetailScroll() {
        return tab == TAB_LOOT ? detailScrollLoot : detailScrollRecord;
    }

    private void resetCurrentDetailScroll() {
        if (tab == TAB_LOOT) detailScrollLoot = 0;
        else detailScrollRecord = 0;
    }

    private boolean adjustDetailScroll(int amount) {
        if (tab == TAB_GRIMOIRE) {
            String text;
            int maxLines;
            int width;
            if (openCardId >= 0) {
                ForbiddenBargain card = ForbiddenBargain.byId(openCardId);
                if (card == null) return false;
                text = card.debtText;
                maxLines = 10;
                width = GRIMOIRE_RIGHT_W;
            } else {
                String line = selectedGrimoireLine();
                if (line == null) return false;
                text = grimoireDisplay(line);
                maxLines = 10;
                width = GRIMOIRE_RIGHT_W;
            }
            int max = Math.max(0, wrap(text, width).size() - maxLines);
            int before = detailScrollGrimoire;
            detailScrollGrimoire = Math.max(0, Math.min(max, detailScrollGrimoire + amount));
            if (detailScrollGrimoire != before) {
                Sfx.play(WardSounds.PIN_DROP, 0.10f, 1.24f);
                return true;
            }
            return false;
        }
        LedgerEntry selected = selectedEntry();
        if (selected == null) return false;
        int max = Math.max(0, wrap(selected.text, PANEL_W - 80).size() - 6);
        if (tab == TAB_LOOT) {
            int before = detailScrollLoot;
            detailScrollLoot = Math.max(0, Math.min(max, detailScrollLoot + amount));
            if (detailScrollLoot != before) {
                Sfx.play(WardSounds.PIN_DROP, 0.10f, 1.24f);
                return true;
            }
        } else {
            int before = detailScrollRecord;
            detailScrollRecord = Math.max(0, Math.min(max, detailScrollRecord + amount));
            if (detailScrollRecord != before) {
                Sfx.play(WardSounds.PIN_DROP, 0.10f, 1.24f);
                return true;
            }
        }
        return false;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private String clip(String input, int maxWidth) {
        if (input == null) return "";
        if (this.font.width(input) <= maxWidth) return input;
        String ellipsis = "...";
        int allowed = Math.max(0, maxWidth - this.font.width(ellipsis));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            if (this.font.width(out.toString() + input.charAt(i)) > allowed) break;
            out.append(input.charAt(i));
        }
        return out + ellipsis;
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (this.font.width(candidate) <= maxWidth || current.length() == 0) {
                current.setLength(0);
                current.append(candidate);
            } else {
                out.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) out.add(current.toString());
        return out;
    }

    private static List<LedgerEntry> parse(List<String> raw) {
        List<LedgerEntry> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw) {
            LedgerEntry e = LedgerEntry.parse(s);
            if (e != null) out.add(e);
        }
        return out;
    }

    private void drawKindIcon(GuiGraphics g, int x, int y, String kind) {
        switch (kind) {
            case "LOOT" -> {
                g.fill(x + 3, y, x + 5, y + 2, 0xFFE2C36B);
                g.fill(x + 1, y + 2, x + 7, y + 5, 0xFFE2C36B);
                g.fill(x + 3, y + 5, x + 5, y + 7, 0xFFE2C36B);
            }
            case "SHARD" -> {
                g.fill(x + 3, y, x + 5, y + 8, 0xFFB77CF0);
                g.fill(x, y + 3, x + 8, y + 5, 0xFFB77CF0);
            }
            case "SAVANT" -> {
                g.fill(x + 1, y + 2, x + 7, y + 6, 0xFF73508E);
                g.fill(x + 3, y + 3, x + 5, y + 5, 0xFF69D8D0);
            }
            case "DANGER", "SUMMARY_LOSS" -> {
                for (int i = 0; i < 7; i++) {
                    g.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFF9B413D);
                    g.fill(x + 6 - i, y + i, x + 7 - i, y + i + 1, 0xFF9B413D);
                }
            }
            case "GLITCH" -> {
                g.fill(x, y + 1, x + 5, y + 3, 0xFF7E4A9A);
                g.fill(x + 3, y + 4, x + 8, y + 6, 0xFFAD71C9);
            }
            case "SUMMARY_WIN" -> {
                g.fill(x + 3, y, x + 5, y + 8, 0xFF718552);
                g.fill(x, y + 3, x + 8, y + 5, 0xFF718552);
            }
            default -> {
                g.fill(x + 3, y, x + 5, y + 2, 0xFF568E89);
                g.fill(x + 1, y + 2, x + 7, y + 6, 0xFF568E89);
                g.fill(x + 3, y + 6, x + 5, y + 8, 0xFF568E89);
            }
        }
    }

    private int kindColor(String kind) {
        return switch (kind) {
            case "LOOT" -> 0xE7D394;
            case "SHARD" -> 0xD7B9EA;
            case "SAVANT" -> 0xD9C6E8;
            case "DANGER", "SUMMARY_LOSS" -> 0xF0A0A7;
            case "GLITCH" -> 0xD8B5E8;
            case "SUMMARY_WIN" -> 0xC3D99B;
            default -> 0xC8DCD7;
        };
    }

    private void assignStats(List<String> raw) {
        List<StatEntry> parsed = parseStats(raw);
        List<StatEntry> status = new ArrayList<>();
        List<StatEntry> analysis = new ArrayList<>();
        for (StatEntry entry : parsed) {
            if ("ANALYSIS".equals(entry.category)) analysis.add(entry);
            else status.add(entry);
        }
        this.statLines = List.copyOf(status);
        this.analysisLines = List.copyOf(analysis);
    }

    private static List<StatEntry> parseStats(List<String> raw) {
        List<StatEntry> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            String[] p = s.split(SEP, 4);
            if (p.length == 4) out.add(new StatEntry(p[0], p[1], p[2], p[3]));
        }
        return out;
    }

    private enum HistoryFilter {
        ALL("All"), LOOT("Loot"), SHARD("Shard"), SAVANT("Savant"), DANGER("Danger"), GLITCH("Glitch"), WARD("Ward");

        final String shortLabel;
        HistoryFilter(String shortLabel) { this.shortLabel = shortLabel; }

        boolean matches(int tab, String kind) {
            if (this == ALL) return true;
            if (tab == TAB_LOOT) {
                return this == LOOT ? "LOOT".equals(kind) : this == SHARD && "SHARD".equals(kind);
            }
            return switch (this) {
                case SAVANT -> "SAVANT".equals(kind);
                case DANGER -> "DANGER".equals(kind) || "SUMMARY_LOSS".equals(kind);
                case GLITCH -> "GLITCH".equals(kind);
                case WARD -> "WARD".equals(kind) || "SUMMARY_WIN".equals(kind);
                default -> true;
            };
        }

        HistoryFilter nextFor(int tab) {
            HistoryFilter[] cycle = tab == TAB_LOOT
                    ? new HistoryFilter[]{ALL, LOOT, SHARD}
                    : new HistoryFilter[]{ALL, SAVANT, DANGER, GLITCH, WARD};
            for (int i = 0; i < cycle.length; i++) if (cycle[i] == this) return cycle[(i + 1) % cycle.length];
            return cycle[0];
        }
    }

    private enum GrimoireFilter {
        ALL("All") {
            @Override boolean matches(String header) { return true; }
        },
        DISCIPLINES("Seals") {
            @Override boolean matches(String header) { return "— SEAL DISCIPLINES —".equals(header); }
        },
        MASTERS("Masters") {
            @Override boolean matches(String header) { return "— MASTER HANDS —".equals(header); }
        },
        ENCOUNTERS("Events") {
            @Override boolean matches(String header) { return "— ENCOUNTERS —".equals(header); }
        },
        DEALERS("Dealers") {
            @Override boolean matches(String header) { return "— CARD MASTERS —".equals(header); }
        },
        BARGAINS("Cards") {
            @Override boolean matches(String header) { return "— BARGAINS & LAWS —".equals(header); }
        },
        CURSE("Curse") {
            @Override boolean matches(String header) { return "— BARGAINS & LAWS —".equals(header); }
        },
        DEATH("Death") {
            @Override boolean matches(String header) { return "— BARGAINS & LAWS —".equals(header); }
        },
        MASTER_CARDS("Master") {
            @Override boolean matches(String header) { return "— BARGAINS & LAWS —".equals(header); }
        },
        CONTRACT("Contract") {
            @Override boolean matches(String header) { return "— BARGAINS & LAWS —".equals(header); }
        },
        RITUAL("Ritual") {
            @Override boolean matches(String header) { return "— BARGAINS & LAWS —".equals(header); }
        },
        COVENANT("Covenant") {
            @Override boolean matches(String header) { return "— BARGAINS & LAWS —".equals(header); }
        },
        CONJUNCTIONS("Conj.") {
            @Override boolean matches(String header) { return "— CONJUNCTIONS —".equals(header); }
        },
        LORE("Lore") {
            @Override boolean matches(String header) {
                return "— ATTENTION RECORD —".equals(header)
                        || "— BEHAVIOURAL TRACES —".equals(header)
                        || "— MASTER HISTORIES —".equals(header)
                        || "— CARD REVISIONS —".equals(header)
                        || "— CURSE CORRUPTIONS —".equals(header)
                        || "— DEATH RESONANCE —".equals(header)
                        || "— OCCULT CHAINS —".equals(header)
                        || "— GUARDIAN TAXONOMY —".equals(header)
                        || "— SAVANT OBSERVATIONS —".equals(header)
                        || "— UNRESOLVED TESTIMONIES —".equals(header);
            }
        },
        PASSAGES("Passages") {
            @Override boolean matches(String header) { return "— CTHULHU PASSAGES —".equals(header); }
        };

        final String shortLabel;
        GrimoireFilter(String shortLabel) { this.shortLabel = shortLabel; }
        abstract boolean matches(String header);
        GrimoireFilter next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private record StatEntry(String category, String label, String value, String detail) {}

    private record LedgerEntry(long timestamp, String kind, String text) {
        static LedgerEntry parse(String raw) {
            if (raw == null || raw.isBlank()) return null;
            String[] parts = raw.split(SEP, 3);
            if (parts.length != 3) return null;
            try {
                return new LedgerEntry(Long.parseLong(parts[0]), parts[1], parts[2]);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}
