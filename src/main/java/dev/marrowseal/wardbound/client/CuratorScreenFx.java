package dev.marrowseal.wardbound.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.AshenCuratorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/** Non-blocking archive callouts driven directly by the synchronized Curator state. */
@Mod.EventBusSubscriber(modid = Wardbound.MODID, value = Dist.CLIENT)
public final class CuratorScreenFx {
    private static final Map<UUID,Integer> LAST = new HashMap<>();
    private static String title = "", subtitle = "", texture = "manifest_archive";
    private static long started, duration;
    private static int mood;

    private CuratorScreenFx() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { LAST.clear(); return; }
        Set<UUID> live = new HashSet<>();
        for (var ent : mc.level.entitiesForRendering()) if (ent instanceof AshenCuratorEntity boss && !boss.isRemoved()) {
            live.add(boss.getUUID());
            int state = boss.view().getInt("state");
            Integer before = LAST.put(boss.getUUID(), state);
            if (before == null) {
                if (state == AshenCuratorEntity.MANIFEST) trigger("THE ASHEN CURATOR", "THE ARCHIVE OPENS", "manifest_archive", 0, 1250);
                continue;
            }
            if (before == state) continue;
            switch (state) {
                case AshenCuratorEntity.SWEEP -> trigger("ASH SHELVES", "THE BLANK AISLE SURVIVES", "sweep_shelf", 0, 780);
                case AshenCuratorEntity.RINGS -> trigger("CINDER FOLIOS", "READ THE EXPANDING EDGE", "folio_ring", 1, 820);
                case AshenCuratorEntity.ERRATA -> trigger("ERRATA", "LEAVE THE ADDRESS", "errata_mark", 1, 820);
                case AshenCuratorEntity.ARCHIVE -> trigger("FINAL INDEX", "PRESERVE OR BREAK", "index_seal", 2, 1050);
                case AshenCuratorEntity.OPEN -> trigger("OPEN BINDING", "STRIKE THE EXPOSED PAGE", "open_binding_glow", 3, 900);
                case AshenCuratorEntity.TRANSITION -> trigger("REDACTION", "THE SHELVES WILL NOT HOLD STILL", "redaction_phase", 2, 1200);
                case AshenCuratorEntity.COLLAPSE -> trigger("THE RECORD IS RELEASED", "ASH REMEMBERS NOTHING", "collapse_archive", 3, 1400);
                default -> { }
            }
        }
        LAST.keySet().retainAll(live);
    }

    private static void trigger(String a, String b, String tex, int m, long ms) {
        title = a; subtitle = b; texture = tex; mood = m; started = System.currentTimeMillis(); duration = Math.max(400, ms);
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post e) {
        if (title.isEmpty()) return;
        long age = System.currentTimeMillis() - started;
        if (age < 0 || age >= duration) { title = ""; subtitle = ""; return; }
        Minecraft mc = Minecraft.getInstance(); if (mc.player == null) return;
        float fade = Math.min(1f, age / 130f) * Math.min(1f, (duration - age) / 220f);
        if (WardConfig.accessibilityReduceMotion) fade *= .78f;
        GuiGraphics g = e.getGuiGraphics(); int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int plateA = Math.round(145 * fade), lineA = Math.round(225 * fade), textA = Math.round(255 * fade);
        int plate = (plateA << 24) | 0x151712;
        int gold = (lineA << 24) | 0xB99A64;
        int accent = (lineA << 24) | (mood >= 3 ? 0x9BC9A9 : mood == 2 ? 0xB56643 : mood == 1 ? 0xD1B276 : 0x8EA79A);
        int top = 12;
        g.fill(w/2-116, top, w/2+116, top+28, plate);
        g.fill(w/2-100, top-1, w/2+100, top, gold);
        g.fill(w/2-72, top+28, w/2+72, top+29, accent);
        ResourceLocation rl = AshenCuratorTextureFx.tex(texture);
        RenderSystem.setShaderColor(1,1,1,Math.max(.18f,fade));
        g.blit(rl,w/2-17,top-4,0,0,34,34,128,128);
        RenderSystem.setShaderColor(1,1,1,1);
        if (fade > .18f) {
            g.drawCenteredString(mc.font,title,w/2,top+4,(textA<<24)|0xE9DCC0);
            g.drawCenteredString(mc.font,subtitle,w/2,top+16,(Math.round(textA*.9f)<<24)|0xAAB7A9);
        }
        if (mood >= 2) {
            int edge = Math.round(42 * fade);
            g.fill(0,0,3,h,(edge<<24)|0x7F432E);
            g.fill(w-3,0,w,h,(edge<<24)|0x7F432E);
        }
    }
}
