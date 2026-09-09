package dev.marrowseal.wardbound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.marrowseal.wardbound.Wardbound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Crisp texture glyphs paired with the generic Lodestone bargain/master particles. */
@Mod.EventBusSubscriber(modid = Wardbound.MODID, value = Dist.CLIENT)
public final class WardMotifTextureFx {
    private record Active(ResourceLocation texture, Vec3 pos, float size, long born, long duration,
                          float rotation, float spin, float alpha) {}
    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final int MAX_ACTIVE = 160;
    private static net.minecraft.client.multiplayer.ClientLevel LEVEL_IDENTITY;

    private WardMotifTextureFx() {}

    private static void syncWorld(Minecraft mc) {
        if (mc == null || LEVEL_IDENTITY == mc.level) return;
        synchronized (ACTIVE) { ACTIVE.clear(); }
        LEVEL_IDENTITY = mc.level;
    }

    public static ResourceLocation motif(String name) {
        return new ResourceLocation(Wardbound.MODID, "textures/vfx/motifs/" + name + ".png");
    }

    public static void ground(ResourceLocation texture, Vec3 pos, float size, int durationMs,
                              float rotation, float spin, float alpha) {
        syncWorld(Minecraft.getInstance());
        synchronized (ACTIVE) {
            while (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.remove(0);
            ACTIVE.add(new Active(texture, pos, size, System.currentTimeMillis(), Math.max(120, durationMs),
                    rotation, spin, alpha));
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        syncWorld(mc);
        if (mc.level == null || ACTIVE.isEmpty()) return;
        long now = System.currentTimeMillis();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        synchronized (ACTIVE) {
            Iterator<Active> it = ACTIVE.iterator();
            while (it.hasNext()) {
                Active fx = it.next();
                long age = now - fx.born;
                if (age < 0 || age >= fx.duration) { it.remove(); continue; }
                float life = age / (float)fx.duration;
                float fade = Math.min(1f, life / .12f) * Math.min(1f, (1f - life) / .25f);
                float pulse = 1f + (float)Math.sin(age * .014) * .045f;
                pose.pushPose();
                pose.translate(fx.pos.x - camera.x, fx.pos.y - camera.y, fx.pos.z - camera.z);
                pose.mulPose(Axis.YP.rotationDegrees(fx.rotation + fx.spin * age / 1000f));
                pose.scale(fx.size * .5f * pulse, 1f, fx.size * .5f * pulse);
                VertexConsumer v = buffers.getBuffer(RenderType.entityTranslucent(fx.texture));
                PoseStack.Pose p = pose.last();
                vertex(v,p,-1,0,-1,0,1,fx.alpha*fade);
                vertex(v,p,-1,0, 1,0,0,fx.alpha*fade);
                vertex(v,p, 1,0, 1,1,0,fx.alpha*fade);
                vertex(v,p, 1,0,-1,1,1,fx.alpha*fade);
                pose.popPose();
            }
        }
        buffers.endBatch();
    }

    private static void vertex(VertexConsumer v, PoseStack.Pose p, float x,float y,float z,float u,float vv,float alpha) {
        v.vertex(p.pose(),x,y,z).color(255,255,255,Math.max(0,Math.min(255,Math.round(alpha*255f))))
                .uv(u,vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880)
                .normal(p.normal(),0,1,0).endVertex();
    }
}
