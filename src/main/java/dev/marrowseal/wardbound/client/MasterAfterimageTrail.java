package dev.marrowseal.wardbound.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.Wardbound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Lightweight afterimage / motion trail layer for the three Masters.
 * It renders themed ghost slices in world-space when a renderer reports fast motion or release recoil.
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID, value = Dist.CLIENT)
public final class MasterAfterimageTrail {
    private static final ResourceLocation G_CARD = tex("gambler/card_back");
    private static final ResourceLocation G_WISP = tex("gambler/wisp");
    private static final ResourceLocation C_PAGE = tex("ashen_curator/page");
    private static final ResourceLocation C_ASH = tex("ashen_curator/ash_ribbon");
    private static final ResourceLocation N_SEAL = tex("mourning_notary/consent_seal");
    private static final ResourceLocation N_SHARD = tex("mourning_notary/seal_shard");

    private static final MultiBufferSource.BufferSource FX = MultiBufferSource.immediate(new BufferBuilder(16384));
    private static final List<Slice> SLICES = new ArrayList<>();
    private static final Map<UUID, Long> LAST_EMIT = new HashMap<>();
    private static Object levelIdentity;

    private record Slice(ResourceLocation texture, Vec3 pos, float w, float h, float yawDeg, float alpha, int age, int life, int ownerId, UUID ownerUuid) {}

    private MasterAfterimageTrail() {}

    private static ResourceLocation tex(String p) {
        return new ResourceLocation(Wardbound.MODID, "textures/vfx/" + p + ".png");
    }

    public static void emitGambler(Entity e, MasterMotionFeel.Sample feel) {
        float intensity = Math.abs(feel.recoil()) + Math.abs(feel.lagY()) * .75f + Math.abs(feel.lagZ()) * .65f;
        if (intensity < .12f) return;
        if (!permit(e, intensity)) return;
        Vec3 c = e.position().add(0, e.getBbHeight() * .60, 0);
        float yaw = e.getYRot();
        push(e, G_CARD, offset(c, yaw, -.75, .18, .22), .85f, 1.45f, yaw - 10f, .38f + intensity * .15f, 9);
        push(e, G_CARD, offset(c, yaw, .75, .18, .22), .85f, 1.45f, yaw + 10f, .34f + intensity * .14f, 9);
        push(e, G_WISP, offset(c, yaw, 0, .25, -.18), 1.55f, 1.55f, yaw, .22f + intensity * .10f, 8);
    }

    public static void emitCurator(Entity e, MasterMotionFeel.Sample feel) {
        float intensity = Math.abs(feel.recoil()) * 1.1f + Math.abs(feel.lagX()) * .45f + Math.abs(feel.lagY()) * .55f;
        if (intensity < .10f) return;
        if (!permit(e, intensity)) return;
        Vec3 c = e.position().add(0, e.getBbHeight() * .58, 0);
        float yaw = e.getYRot();
        push(e, C_PAGE, offset(c, yaw, 0, .35, .55), 1.0f, 1.6f, yaw, .28f + intensity * .12f, 10);
        push(e, C_PAGE, offset(c, yaw, -.55, .12, .15), .8f, 1.25f, yaw - 14f, .24f + intensity * .10f, 9);
        push(e, C_ASH, offset(c, yaw, .45, .22, -.10), 1.8f, 1.8f, yaw + 18f, .22f + intensity * .10f, 10);
    }

    public static void emitNotary(Entity e, MasterMotionFeel.Sample feel) {
        float intensity = Math.abs(feel.recoil()) + Math.abs(feel.lagX()) * .40f + Math.abs(feel.lagZ()) * .75f;
        if (intensity < .10f) return;
        if (!permit(e, intensity)) return;
        Vec3 c = e.position().add(0, e.getBbHeight() * .62, 0);
        float yaw = e.getYRot();
        push(e, N_SEAL, offset(c, yaw, 0, .40, .42), 1.65f, 1.65f, yaw, .26f + intensity * .12f, 10);
        push(e, N_SHARD, offset(c, yaw, -.58, .16, 0), 1.15f, 1.15f, yaw - 12f, .28f + intensity * .10f, 10);
        push(e, N_SHARD, offset(c, yaw, .58, .16, 0), 1.15f, 1.15f, yaw + 12f, .28f + intensity * .10f, 10);
    }

    private static boolean permit(Entity entity, float intensity) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == null || mc.level == null || mc.isPaused() || WardConfig.accessibilityReduceMotion) return false;
        long now = System.nanoTime();
        long minGap = intensity > .55f ? 22_000_000L : intensity > .32f ? 34_000_000L : 52_000_000L;
        UUID key = entity.getUUID();
        Long last = LAST_EMIT.get(key);
        if (last != null && now - last < minGap) return false;
        LAST_EMIT.put(key, now);
        return true;
    }

    private static Vec3 offset(Vec3 base, float yawDeg, double x, double y, double z) {
        double r = Math.toRadians(yawDeg + 90.0);
        double fx = Math.cos(r), fz = Math.sin(r);
        double rx = z * fx - x * fz;
        double rz = z * fz + x * fx;
        return base.add(rx, y, rz);
    }

    private static void push(Entity owner, ResourceLocation tex, Vec3 pos, float w, float h, float yaw, float alpha, int life) {
        if (owner == null || SLICES.size() > 256) return;
        SLICES.add(new Slice(tex, pos, w, h, yaw, Mth.clamp(alpha, .08f, .56f), 0, life, owner.getId(), owner.getUUID()));
    }

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || SLICES.isEmpty()) return;
        PoseStack pose = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        try {
            for (Slice s : SLICES) {
                float t = (s.age() + mc.getFrameTime()) / (float) Math.max(1, s.life());
                float a = (1f - t) * (1f - t) * s.alpha();
                if (a <= .01f) continue;
                pose.pushPose();
                pose.translate(s.pos().x - cam.x, s.pos().y - cam.y, s.pos().z - cam.z);
                pose.mulPose(e.getCamera().rotation());
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) Math.sin(t * Math.PI) * 8f + (s.yawDeg() % 18f)));
                float scale = 1f + t * .22f;
                pose.scale((s.w() * .5f) * scale, (s.h() * .5f) * scale, 1);
                quad(pose, s.texture(), a);
                pose.popPose();
            }
        } finally {
            FX.endBatch();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    private static void quad(PoseStack p, ResourceLocation tex, float alpha) {
        Matrix4f m = p.last().pose();
        Matrix3f n = p.last().normal();
        VertexConsumer vc = FX.getBuffer(RenderType.entityTranslucent(tex));
        vc.vertex(m, -1, -1, 0).color(255, 255, 255, (int) (alpha * 255)).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(240).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, 1, -1, 0).color(255, 255, 255, (int) (alpha * 255)).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(240).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, 1, 1, 0).color(255, 255, 255, (int) (alpha * 255)).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(240).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, -1, 1, 0).color(255, 255, 255, (int) (alpha * 255)).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(240).normal(n, 0, 0, 1).endVertex();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (levelIdentity != mc.level) {
            SLICES.clear();
            LAST_EMIT.clear();
            levelIdentity = mc.level;
        }
        if (mc.level == null || mc.isPaused()) return;
        if (!SLICES.isEmpty()) {
            for (int i = SLICES.size() - 1; i >= 0; i--) {
                Slice s = SLICES.get(i);
                Entity owner = mc.level.getEntity(s.ownerId());
                boolean ownerGone = owner == null || !owner.getUUID().equals(s.ownerUuid()) || owner.isRemoved();
                if (ownerGone || s.age() + 1 >= s.life()) SLICES.remove(i);
                else SLICES.set(i, new Slice(s.texture(), s.pos(), s.w(), s.h(), s.yawDeg(), s.alpha(), s.age() + 1, s.life(), s.ownerId(), s.ownerUuid()));
            }
        }
        if (LAST_EMIT.size() > 64) LAST_EMIT.clear();
    }
}
