package dev.marrowseal.wardbound.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.AshenCuratorEntity;
import dev.marrowseal.wardbound.boss.CuratorPatterns;
import dev.marrowseal.wardbound.net.GamblerCinematicPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Short-lived, render-only encounter residue.
 *
 * <p>This layer intentionally renders before the authored attack telegraphs. It never owns
 * collision, hit detection, navigation, particles or server state; it only makes an arena look
 * progressively used without ever winning the readability fight against a danger warning.</p>
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID, value = Dist.CLIENT)
public final class EncounterAftermathFx {
    private static final int MAX_MARKS = 420;
    private static final MultiBufferSource.BufferSource FX = MultiBufferSource.immediate(new BufferBuilder(8192));
    private static final List<Mark> MARKS = new ArrayList<>();
    private static Object levelIdentity;

    private record Mark(ResourceLocation texture, Vec3 pos, float width, float depth,
                        float rotation, long born, long duration, float alpha) {}

    private EncounterAftermathFx() {}

    private static ResourceLocation gambler(String name) {
        return new ResourceLocation(Wardbound.MODID, "textures/vfx/gambler/" + name + ".png");
    }

    private static ResourceLocation curator(String name) {
        return new ResourceLocation(Wardbound.MODID, "textures/vfx/ashen_curator/" + name + ".png");
    }

    private static ResourceLocation notary(String name) {
        return new ResourceLocation(Wardbound.MODID, "textures/vfx/mourning_notary/" + name + ".png");
    }

    private static void add(ResourceLocation texture, Vec3 pos, float width, float depth,
                            float rotation, int durationMs, float alpha) {
        if (texture == null || pos == null || !Double.isFinite(pos.x + pos.y + pos.z)) return;
        width = Mth.clamp(width, .25f, 24f);
        depth = Mth.clamp(depth, .25f, 24f);
        alpha = Mth.clamp(alpha, .04f, .58f); // residue must never compete with danger ink.
        synchronized (MARKS) {
            while (MARKS.size() >= MAX_MARKS) MARKS.remove(0);
            MARKS.add(new Mark(texture, pos, width, depth, rotation, System.currentTimeMillis(),
                    Math.max(800, durationMs), alpha));
        }
    }

    /** Called from server-authored Gambler cinematic impacts. */
    public static void recordGambler(GamblerCinematicPacket msg) {
        int fx = msg.effect();
        ResourceLocation tex;
        float size = Mth.clamp(msg.size(), 2.2f, 12f);
        int life = 6200;
        float alpha = .31f;
        switch (fx) {
            case GamblerCinematicPacket.FAN_IMPACT,
                 GamblerCinematicPacket.SWEEP_STRIKE,
                 GamblerCinematicPacket.CAGE_STRIKE,
                 GamblerCinematicPacket.CARD_WALL -> tex = gambler("card_seal_blood");
            case GamblerCinematicPacket.RING_IMPACT,
                 GamblerCinematicPacket.SPIRAL,
                 GamblerCinematicPacket.HOUSE_EDGE -> { tex = gambler("ring_arc"); size = Math.min(14f, size * 1.35f); }
            case GamblerCinematicPacket.FLOOR_CARD_IMPACT,
                 GamblerCinematicPacket.FLOOR_CARD_STRIKE,
                 GamblerCinematicPacket.BURIED_ACE,
                 GamblerCinematicPacket.SUIT_CALL_STRIKE -> { tex = gambler("card_seal_teal"); life = 7600; alpha = .34f; }
            case GamblerCinematicPacket.MARK_RIFT,
                 GamblerCinematicPacket.STARFALL,
                 GamblerCinematicPacket.DEAD_MANS_DRAW -> { tex = gambler("rift_star"); life = 7000; }
            case GamblerCinematicPacket.DEALER_CROSS -> tex = gambler("dealer_cross");
            case GamblerCinematicPacket.CHIP_STORM -> { tex = gambler("chip_rune"); size = Math.min(8f, size); }
            case GamblerCinematicPacket.SPLIT_POT -> tex = gambler("split_pot");
            case GamblerCinematicPacket.MIRROR_DEBT -> tex = gambler("mirror_splinter");
            case GamblerCinematicPacket.DOUBLE_DOWN -> tex = gambler("double_down");
            case GamblerCinematicPacket.ROYAL,
                 GamblerCinematicPacket.ROYAL_FLUSH,
                 GamblerCinematicPacket.LAST_DEAL -> { tex = gambler("sigil_gold"); life = 8600; alpha = .36f; }
            case GamblerCinematicPacket.DANGER_CARD_STRIKE,
                 GamblerCinematicPacket.HOUSE_TEETH -> { tex = gambler("sigil_blood"); life = 7200; alpha = .35f; }
            default -> { return; }
        }

        Random r = new Random(msg.seed() * 1103515245L + fx * 97L);
        Vec3 base = new Vec3(msg.x(), msg.y() - .095, msg.z());
        float rot = r.nextFloat() * 360f;
        float w = Math.max(2.4f, size * (tex.getPath().contains("ring") ? 1.45f : .78f));
        add(tex, base, w, w, rot, life + r.nextInt(1800), alpha);

        // A few discarded seal/card fragments sell accumulation without turning into a new warning.
        if (fx != GamblerCinematicPacket.RING_IMPACT && fx != GamblerCinematicPacket.HOUSE_EDGE) {
            int extras = 1 + (Math.abs(msg.seed()) & 1);
            for (int i = 0; i < extras; i++) {
                double a = r.nextDouble() * Math.PI * 2, rr = .7 + r.nextDouble() * Math.min(3.2, size * .30);
                ResourceLocation chip = (i & 1) == 0 ? gambler("card_seal_gold") : gambler("collapse_shard");
                add(chip, base.add(Math.cos(a) * rr, .002, Math.sin(a) * rr),
                        .75f + r.nextFloat() * .55f, .95f + r.nextFloat() * .65f,
                        r.nextFloat() * 360f, 5000 + r.nextInt(2600), .20f + r.nextFloat() * .10f);
            }
        }
    }

    /** Record the moment a Curator hazard actually becomes dangerous, never during its warning. */
    public static void recordCuratorHazard(AshenCuratorEntity boss, AshenCuratorEntity.Hazard h, int state) {
        if (boss == null || h == null) return;
        Vec3 base = boss.position().add(h.x(), .012, h.z());
        Random r = new Random((long) h.id() * 73471L + boss.getId() * 313L);
        if (h.shape() == CuratorPatterns.RECT) {
            float w = (float) Math.min(11.5, Math.max(2.4, h.a() * 2));
            float d = (float) Math.min(11.5, Math.max(2.4, h.b() * 2));
            add(curator(state == AshenCuratorEntity.SWEEP ? "ash_ring" : "impact_stamp"), base,
                    w, d, r.nextFloat() * 360f, 7800 + r.nextInt(2600), .27f);
            add(curator("break_fracture"), base.add(0, .002, 0), Math.min(5.2f, w), Math.min(5.2f, d),
                    r.nextFloat() * 360f, 6600 + r.nextInt(2200), .18f);
        } else if (h.shape() == CuratorPatterns.RING) {
            float diameter = (float) Math.min(20.0, Math.max(3.2, (h.a() + h.b()) * 2));
            add(curator("ash_ring"), base, diameter, diameter, r.nextFloat() * 360f,
                    9000 + r.nextInt(2600), .26f);
        } else {
            float d = (float) Math.min(7.5, Math.max(3.0, h.a() * 2.05));
            add(curator("errata_mark"), base, d, d, r.nextFloat() * 360f,
                    8800 + r.nextInt(2400), .34f);
        }
    }

    public static void recordCuratorPage(Vec3 pos, int seed) {
        if (pos == null) return;
        Random r = new Random(seed * 8191L + 17L);
        for (int i = 0; i < 1 + (Math.abs(seed) & 1); i++) {
            double a = r.nextDouble() * Math.PI * 2, rr = .12 + r.nextDouble() * .75;
            add(curator("page"), new Vec3(pos.x + Math.cos(a) * rr, pos.y + .012, pos.z + Math.sin(a) * rr),
                    .62f + r.nextFloat() * .24f, .86f + r.nextFloat() * .30f,
                    r.nextFloat() * 360f, 7600 + r.nextInt(3000), .43f);
        }
        add(curator("ash_ring"), pos.add(0, .008, 0), 2.2f, 2.2f,
                r.nextFloat() * 360f, 5400 + r.nextInt(1800), .16f);
    }

    public static void recordCuratorSeal(Vec3 pos, int seed) {
        if (pos == null) return;
        Random r = new Random(seed * 1299709L + 31L);
        add(curator("break_fracture"), pos.add(0, .010, 0), 4.8f, 4.8f,
                r.nextFloat() * 360f, 9600 + r.nextInt(2200), .38f);
    }

    public static void recordNotaryStamp(Vec3 pos, int seed, boolean severe) {
        if (pos == null) return;
        Random r = new Random(seed * 65537L + 47L);
        float size = severe ? 4.1f : 3.25f;
        add(notary("stamp_impact"), pos.add(0, .012, 0), size, size,
                r.nextFloat() * 360f, severe ? 8800 : 6500, severe ? .40f : .31f);
        add(notary("seal_shard"), pos.add(0, .014, 0), size * .72f, size * .72f,
                r.nextFloat() * 360f, severe ? 7200 : 5200, severe ? .27f : .20f);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        Object current = mc.level;
        if (current == null) {
            synchronized (MARKS) { MARKS.clear(); }
            levelIdentity = null;
            return;
        }
        if (levelIdentity != current) {
            synchronized (MARKS) { MARKS.clear(); }
            levelIdentity = current;
        }
        long now = System.currentTimeMillis();
        synchronized (MARKS) { MARKS.removeIf(m -> now - m.born >= m.duration || now < m.born); }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        // Explicitly below danger telegraphs (those render AFTER_PARTICLES in the authored layers).
        // AFTER_TRIPWIRE_BLOCKS is also the Forge-recommended pre-particle stage for translucent custom geometry.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = FX;
        Set<RenderType> used = new HashSet<>();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        try {
            synchronized (MARKS) {
                Iterator<Mark> it = MARKS.iterator();
                while (it.hasNext()) {
                    Mark m = it.next();
                    long age = now - m.born;
                    if (age < 0 || age >= m.duration) { it.remove(); continue; }
                    if (m.pos.distanceToSqr(camera) > 110 * 110) continue;
                    float life = age / (float) m.duration;
                    float fadeIn = Math.min(1f, life / .08f);
                    float fadeOut = Math.min(1f, (1f - life) / .34f);
                    float alpha = m.alpha * Math.max(0f, Math.min(fadeIn, fadeOut));
                    if (alpha <= .005f) continue;
                    pose.pushPose();
                    pose.translate(m.pos.x - camera.x, m.pos.y - camera.y, m.pos.z - camera.z);
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(m.rotation));
                    pose.scale(m.width * .5f, 1f, m.depth * .5f);
                    RenderType rt = RenderType.entityTranslucent(m.texture);
                    used.add(rt);
                    VertexConsumer v = buffers.getBuffer(rt);
                    quadXZ(v, pose, alpha);
                    pose.popPose();
                }
            }
            for (RenderType rt : used) buffers.endBatch(rt);
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    private static void quadXZ(VertexConsumer v, PoseStack pose, float alpha) {
        PoseStack.Pose p = pose.last();
        vertex(v, p, -1, 0, -1, 0, 1, alpha);
        vertex(v, p, -1, 0,  1, 0, 0, alpha);
        vertex(v, p,  1, 0,  1, 1, 0, alpha);
        vertex(v, p,  1, 0, -1, 1, 1, alpha);
    }

    private static void vertex(VertexConsumer v, PoseStack.Pose p, float x, float y, float z,
                               float u, float vv, float alpha) {
        v.vertex(p.pose(), x, y, z)
                .color(255, 255, 255, Math.max(0, Math.min(255, Math.round(alpha * 255f))))
                .uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880)
                .normal(p.normal(), 0, 1, 0).endVertex();
    }
}
