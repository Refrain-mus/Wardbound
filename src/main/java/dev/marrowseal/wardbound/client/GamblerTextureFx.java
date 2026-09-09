package dev.marrowseal.wardbound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.boss.GamblerPatterns;
import dev.marrowseal.wardbound.boss.PaleGamblerEntity;
import dev.marrowseal.wardbound.net.MasterCuesPacket;
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
import team.lodestar.lodestone.registry.common.particle.LodestoneParticleRegistry;
import team.lodestar.lodestone.systems.particle.builder.WorldParticleBuilder;
import team.lodestar.lodestone.systems.particle.data.GenericParticleData;
import team.lodestar.lodestone.systems.particle.data.color.ColorParticleData;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;

/**
 * Texture-backed world VFX for The Pale Gambler. Lodestone handles the living
 * particle field; this layer gives major beats crisp, authored pixel-art seals,
 * cards and impact glyphs that particles alone cannot communicate.
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID, value = Dist.CLIENT)
public final class GamblerTextureFx {
    public static final ResourceLocation SIGIL_GOLD = tex("sigil_gold.png");
    public static final ResourceLocation SIGIL_BLOOD = tex("sigil_blood.png");
    public static final ResourceLocation SIGIL_TEAL = tex("sigil_teal.png");
    public static final ResourceLocation CARD_GOLD = tex("card_seal_gold.png");
    public static final ResourceLocation CARD_BLOOD = tex("card_seal_blood.png");
    public static final ResourceLocation CARD_TEAL = tex("card_seal_teal.png");
    public static final ResourceLocation IMPACT = tex("impact_star.png");
    public static final ResourceLocation RIFT = tex("rift_star.png");
    public static final ResourceLocation HALO = tex("halo.png");
    public static final ResourceLocation WISP = tex("wisp.png");
    public static final ResourceLocation SHARD = tex("collapse_shard.png");
    public static final ResourceLocation RING_ARC = tex("ring_arc.png");
    public static final ResourceLocation CROSS_FLARE = tex("cross_flare.png");
    public static final ResourceLocation RUNE_PILLAR = tex("rune_pillar.png");
    public static final ResourceLocation VOID_EYE = tex("void_eye.png");
    public static final ResourceLocation DEALER_CROSS = tex("dealer_cross.png");
    public static final ResourceLocation CHIP_RUNE = tex("chip_rune.png");
    public static final ResourceLocation SPLIT_POT = tex("split_pot.png");
    public static final ResourceLocation CHOICE_CHAIN = tex("choice_chain.png");
    public static final ResourceLocation CARD_BACK = tex("card_back.png");
    public static final ResourceLocation FLOOR_DANGER = tex("floor_danger.png");
    public static final ResourceLocation TELEGRAPH_RED = tex("telegraph_red.png");
    public static final ResourceLocation TELEGRAPH_VIOLET = tex("telegraph_violet.png");
    public static final ResourceLocation TELEGRAPH_GOLD = tex("telegraph_gold.png");
    public static final ResourceLocation TELEGRAPH_SAFE = tex("telegraph_safe.png");
    public static final ResourceLocation TELEGRAPH_RING_RED = tex("telegraph_ring_red.png");
    public static final ResourceLocation TELEGRAPH_RING_VIOLET = tex("telegraph_ring_violet.png");
    public static final ResourceLocation TELEGRAPH_RING_GOLD = tex("telegraph_ring_gold.png");
    public static final ResourceLocation TELEGRAPH_RING_SAFE = tex("telegraph_ring_safe.png");
    public static final ResourceLocation HOUSE_EDGE = tex("house_edge.png");
    public static final ResourceLocation DOUBLE_DOWN = tex("double_down.png");
    public static final ResourceLocation FORWARD_CAST = tex("forward_cast.png");
    public static final ResourceLocation HOUSE_TELL = tex("house_tell.png");
    public static final ResourceLocation MIRROR_SPLINTER = tex("mirror_splinter.png");
    public static final ResourceLocation STACKED_SEAL = tex("stacked_seal.png");
    public static final ResourceLocation DEAD_MANS_DRAW = tex("dead_mans_draw.png");
    public static final ResourceLocation FALSE_TELL = tex("false_tell.png");
    public static final ResourceLocation SUIT_SPADE = tex("suit_spade.png");
    public static final ResourceLocation SUIT_HEART = tex("suit_heart.png");
    public static final ResourceLocation SUIT_CLUB = tex("suit_club.png");
    public static final ResourceLocation SUIT_DIAMOND = tex("suit_diamond.png");
    public static final ResourceLocation ROYAL_FLUSH = tex("royal_flush.png");
    public static final ResourceLocation LAST_DEAL = tex("last_deal.png");
    public static final ResourceLocation ALL_IN = tex("all_in.png");
    public static final ResourceLocation HOUSE_READ = tex("house_read.png");
    public static final ResourceLocation FINAL_TELL = tex("final_tell.png");

    private record Active(ResourceLocation texture, Vec3 pos, float width, float height,
                          long born, long duration, float rotation, float rotationSpeed,
                          boolean billboard, boolean rise, float alpha) {}
    private record Active3D(ResourceLocation texture, Vec3 pos, float width, float height,
                            long born, long duration, float yaw, float pitch, float roll,
                            float yawSpeed, float pitchSpeed, float rollSpeed, float alpha) {}
    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final List<Active3D> ACTIVE_3D = new ArrayList<>();
    // Hard safety ceiling only: full authored telegraphs may briefly overlap between server snapshots.
    // If a future attack accidentally runs away, discard the oldest cosmetic FX instead of
    // allowing an unbounded client-side list to grow.
    private static final int MAX_ACTIVE = 24576;
    private static final int MAX_ACTIVE_3D = 8192;
    private record BoundaryImpulse(long bornMs, long durationMs, float directionDeg, float strength, boolean reverse) {}
    private static final Map<Integer, BoundaryImpulse> BOUNDARY_IMPULSES = new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel LEVEL_IDENTITY;

    private GamblerTextureFx() {}

    private static void syncWorld(Minecraft mc) {
        if (mc == null || LEVEL_IDENTITY == mc.level) return;
        synchronized (ACTIVE) { ACTIVE.clear(); }
        synchronized (ACTIVE_3D) { ACTIVE_3D.clear(); }
        synchronized (BOUNDARY_IMPULSES) { BOUNDARY_IMPULSES.clear(); }
        LEVEL_IDENTITY = mc.level;
    }

    public static void triggerBoundaryImpulse(int entityId, float strength, float directionDeg, boolean reverse, int durationMs) {
        syncWorld(Minecraft.getInstance());
        if (entityId < 0 || durationMs <= 0) return;
        synchronized (BOUNDARY_IMPULSES) {
            BOUNDARY_IMPULSES.put(entityId, new BoundaryImpulse(System.currentTimeMillis(), durationMs, directionDeg, Math.max(0.05f, strength), reverse));
        }
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(Wardbound.MODID, "textures/vfx/gambler/" + name);
    }

    private static void add(Active effect) {
        syncWorld(Minecraft.getInstance());
        synchronized (ACTIVE) {
            while (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.remove(0);
            ACTIVE.add(effect);
        }
    }

    public static void ground(ResourceLocation texture, Vec3 pos, float width, float depth,
                              int durationMs, float rotation, float rotationSpeed, float alpha) {
        add(new Active(texture, pos, width, depth, System.currentTimeMillis(), durationMs,
                rotation, rotationSpeed, false, false, alpha));
    }

    public static void billboard(ResourceLocation texture, Vec3 pos, float width, float height,
                                 int durationMs, float rotation, float rotationSpeed, boolean rise, float alpha) {
        add(new Active(texture, pos, width, height, System.currentTimeMillis(), durationMs,
                rotation, rotationSpeed, true, rise, alpha));
    }

    /** A true world-space card plane: it does not rotate to face the camera. */
    public static void card3D(ResourceLocation texture, Vec3 pos, float width, float height, int durationMs,
                              float yaw, float pitch, float roll, float yawSpeed, float pitchSpeed, float rollSpeed, float alpha) {
        syncWorld(Minecraft.getInstance());
        synchronized (ACTIVE_3D) {
            while (ACTIVE_3D.size() >= MAX_ACTIVE_3D) ACTIVE_3D.remove(0);
            ACTIVE_3D.add(new Active3D(texture,pos,width,height,System.currentTimeMillis(),durationMs,
                    yaw,pitch,roll,yawSpeed,pitchSpeed,rollSpeed,alpha));
        }
    }

    private static boolean isTableCardTexture(ResourceLocation texture) {
        return texture.equals(CARD_GOLD) || texture.equals(CARD_BLOOD) || texture.equals(CARD_TEAL);
    }

    /** Removes the long-lived authored preview at a card position before its impact/dissolve pass. */
    private static void clearCardPreviewAt(Vec3 pos) {
        synchronized (ACTIVE) {
            ACTIVE.removeIf(fx -> !fx.billboard && isTableCardTexture(fx.texture)
                    && fx.pos.distanceToSqr(pos) < 0.36D);
        }
    }

    /** Clears table-sized card/felt previews when a hand ends or the encounter changes state. */
    public static void clearTablePreviews() {
        synchronized (ACTIVE) {
            ACTIVE.removeIf(fx -> !fx.billboard && (isTableCardTexture(fx.texture) || fx.texture.equals(FLOOR_DANGER)));
        }
    }

    public static void card(Vec3 pos, float halfHeight, double angle, int suit, boolean strike) {
        if (strike) clearCardPreviewAt(pos.add(0, .035, 0));
        ResourceLocation texture = strike ? CARD_TEAL : ((suit & 1) == 0 ? CARD_GOLD : CARD_BLOOD);
        float depth = Math.max(2.8f, halfHeight * 2.0f);
        float width = depth * .62f;
        // Preview must outlive the slowest configured floor-card windup; strike replaces it explicitly.
        ground(texture, pos.add(0, .035, 0), width, depth, strike ? 1450 : 9000,
                (float) Math.toDegrees(angle), strike ? 22f : 3.5f, strike ? .98f : .86f);
        if (strike) {
            billboard(IMPACT, pos.add(0, 1.1, 0), 2.3f, 2.3f, 520, 0, 120f, true, .95f);
            ground(RING_ARC, pos.add(0, .04, 0), width * 1.3f, depth * 1.3f, 520, (float) Math.toDegrees(angle), 28f, .88f);
            billboard(CROSS_FLARE, pos.add(0, 1.25, 0), 1.8f, 1.8f, 440, 0, 90f, true, .90f);
        }
    }

    public static void dangerCard(Vec3 pos, float halfHeight, double angle, boolean strike) {
        if(strike) clearCardPreviewAt(pos.add(0,.038,0));
        float depth=Math.max(2.8f,halfHeight*2.0f),width=depth*.62f;
        // Stacked Deck may run multiple waves, so danger cards persist until the hand resolves.
        ground(CARD_BLOOD,pos.add(0,.038,0),width,depth,strike?1500:9500,(float)Math.toDegrees(angle),strike?34f:5f,strike?.98f:.88f);
        ground(strike?TELEGRAPH_RING_RED:TELEGRAPH_RING_VIOLET,pos.add(0,.02,0),width*1.22f,depth*1.18f,strike?580:900,(float)Math.toDegrees(angle),strike?26f:8f,strike?.82f:.58f);
        if(strike){
            billboard(IMPACT,pos.add(0,1.05,0),2.5f,2.5f,500,0,135f,true,.96f);
            billboard(CROSS_FLARE,pos.add(0,1.4,0),1.8f,1.8f,440,0,-105f,true,.88f);
        }
    }

    /** Crisp texture-backed complement to the Lodestone particles used for server-owned danger geometry. */
    public static void telegraph(MasterCuesPacket packet) {
        if (packet == null || packet.cues().isEmpty()) return;
        // Do not sample authored geometry here. Floor Cards can contain ~150-200 short rails;
        // sampling them made a valid safe card look like scattered disconnected marks. The
        // server only refreshes cues every three ticks, so a short ~230 ms texture lifetime keeps
        // the full shape continuous without building an unbounded trail.
        int limit=Math.min(MasterCuesPacket.MAX_CUES,packet.cues().size());
        for (int i=0;i<limit;i++) {
            MasterCuesPacket.Cue c = packet.cues().get(i);
            ResourceLocation strip = stripFor(c.color());
            ResourceLocation ring = ringFor(c.color());
            if (c.circle()) {
                float diameter = (float)Math.max(.4, c.a() * 2.0);
                ground(ring, new Vec3(c.x(), packet.y() + .018, c.z()), diameter, diameter,
                        235, 0f, 0f, c.color() == 0x55FFCA ? .82f : .70f);
            } else {
                double dx = c.a() - c.x(), dz = c.b() - c.z();
                double len = Math.hypot(dx, dz);
                if (len < .08) continue;
                double mx = (c.x() + c.a()) * .5, mz = (c.z() + c.b()) * .5;
                float angle = (float)Math.toDegrees(Math.atan2(dz, dx));
                float thickness = (float)Math.max(.20, Math.min(1.05, c.width() * .62));
                ground(strip, new Vec3(mx, packet.y() + .014, mz), (float)len, thickness,
                        230, angle, 0f, c.color() == 0x55FFCA ? .86f : .74f);
            }
        }
    }

    private static ResourceLocation stripFor(int color) {
        if (color == 0xFF4264) return TELEGRAPH_RED;
        if (color == 0x55FFCA) return TELEGRAPH_SAFE;
        if (color == 0xEDC16B) return TELEGRAPH_GOLD;
        return TELEGRAPH_VIOLET;
    }

    private static ResourceLocation ringFor(int color) {
        if (color == 0xFF4264) return TELEGRAPH_RING_RED;
        if (color == 0x55FFCA) return TELEGRAPH_RING_SAFE;
        if (color == 0xEDC16B) return TELEGRAPH_RING_GOLD;
        return TELEGRAPH_RING_VIOLET;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        syncWorld(mc);
        if (mc.level == null || (ACTIVE.isEmpty() && ACTIVE_3D.isEmpty())) return;

        long now = System.currentTimeMillis();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        Set<RenderType> usedTypes = new HashSet<>();
        synchronized (ACTIVE) {
            Iterator<Active> it = ACTIVE.iterator();
            while (it.hasNext()) {
                Active fx = it.next();
                long age = now - fx.born;
                if (age < 0 || age >= fx.duration) { it.remove(); continue; }
                float life = age / (float) fx.duration;
                float fadeIn = Math.min(1f, life / .12f);
                float fadeOut = Math.min(1f, (1f - life) / .22f);
                float alpha = fx.alpha * Math.max(0f, Math.min(fadeIn, fadeOut));
                float pulse = 1f + (float)Math.sin(age * .012) * .035f;
                float rot = fx.rotation + fx.rotationSpeed * age / 1000f;
                double rise = fx.rise ? life * .8 : 0;

                pose.pushPose();
                pose.translate(fx.pos.x - camera.x, fx.pos.y + rise - camera.y, fx.pos.z - camera.z);
                RenderType renderType = RenderType.entityTranslucent(fx.texture);
                usedTypes.add(renderType);
                VertexConsumer v = buffers.getBuffer(renderType);
                if (fx.billboard) {
                    pose.mulPose(event.getCamera().rotation());
                    pose.mulPose(Axis.ZP.rotationDegrees(rot));
                    pose.scale(fx.width * pulse * .5f, fx.height * pulse * .5f, 1f);
                    quadXY(v, pose, alpha);
                } else {
                    pose.mulPose(Axis.YP.rotationDegrees(rot));
                    pose.scale(fx.width * pulse * .5f, 1f, fx.height * pulse * .5f);
                    quadXZ(v, pose, alpha);
                }
                pose.popPose();
            }
        }
        synchronized (ACTIVE_3D) {
            Iterator<Active3D> it = ACTIVE_3D.iterator();
            while (it.hasNext()) {
                Active3D fx = it.next();
                long age = now - fx.born;
                if (age < 0 || age >= fx.duration) { it.remove(); continue; }
                float life=age/(float)fx.duration;
                float fadeIn=Math.min(1f,life/.10f),fadeOut=Math.min(1f,(1f-life)/.20f);
                float alpha=fx.alpha*Math.max(0f,Math.min(fadeIn,fadeOut));
                float yaw=fx.yaw+fx.yawSpeed*age/1000f,pitch=fx.pitch+fx.pitchSpeed*age/1000f,roll=fx.roll+fx.rollSpeed*age/1000f;
                pose.pushPose();
                pose.translate(fx.pos.x-camera.x,fx.pos.y-camera.y,fx.pos.z-camera.z);
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                pose.mulPose(Axis.XP.rotationDegrees(pitch));
                pose.mulPose(Axis.ZP.rotationDegrees(roll));
                pose.scale(fx.width*.5f,fx.height*.5f,1f);
                RenderType rt=RenderType.entityTranslucent(fx.texture);usedTypes.add(rt);VertexConsumer v=buffers.getBuffer(rt);
                quadXY(v,pose,alpha);
                pose.mulPose(Axis.YP.rotationDegrees(180f));
                quadXY(v,pose,alpha*.92f);
                pose.popPose();
            }
        }

        for (var ent : mc.level.entitiesForRendering()) {
            if (!(ent instanceof PaleGamblerEntity boss) || boss.isRemoved() || boss.distanceToSqr(mc.player) > 6400) continue;
            renderBoundaryWall(event, pose, buffers, usedTypes, camera, boss);
        }
        for(RenderType renderType:usedTypes) buffers.endBatch(renderType);
    }

    private static void renderBoundaryWall(RenderLevelStageEvent event, PoseStack pose, MultiBufferSource.BufferSource buffers, Set<RenderType> usedTypes, Vec3 camera, PaleGamblerEntity boss) {
        int deathClock=boss.deathClock();
        float deathT=deathClock<0?0f:Mth.clamp((deathClock+event.getPartialTick())/36f,0f,1f);
        if(deathClock>=36)return;
        float boundaryAlpha=1f-deathT*.82f;
        double radius = (GamblerPatterns.BOUNDARY_RADIUS + 0.68) * (1.0-deathT*.955);
        RenderType cardType = RenderType.entityTranslucent(CARD_BACK);
        RenderType ringType = RenderType.entityTranslucent(TELEGRAPH_RING_VIOLET);
        RenderType fieldType = RenderType.entityTranslucent(FLOOR_DANGER);
        RenderType wispType = RenderType.entityTranslucent(WISP);
        usedTypes.add(cardType); usedTypes.add(ringType); usedTypes.add(fieldType); usedTypes.add(wispType);
        VertexConsumer cards = buffers.getBuffer(cardType), rings = buffers.getBuffer(ringType), field = buffers.getBuffer(fieldType), wisps=buffers.getBuffer(wispType);
        double cx=boss.getX(), cy=boss.getY(), cz=boss.getZ();
        float time=(boss.tickCount+event.getPartialTick())*.9f;
        float gust = 0f, gustDir = 0f;
        boolean reverse = false;
        long nowMs = System.currentTimeMillis();
        synchronized (BOUNDARY_IMPULSES) {
            BoundaryImpulse impulse = BOUNDARY_IMPULSES.get(boss.getId());
            if (impulse != null) {
                long age = nowMs - impulse.bornMs();
                if (age < 0 || age >= impulse.durationMs()) {
                    BOUNDARY_IMPULSES.remove(boss.getId());
                } else {
                    float t = age / (float) impulse.durationMs();
                    float envelope = (float) Math.sin(Math.PI * Math.min(1f, t));
                    gust = impulse.strength() * envelope;
                    gustDir = impulse.directionDeg();
                    reverse = impulse.reverse();
                    if (reverse && t > .45f) gust *= -1.55f;
                    else if (reverse && t <= .45f) gust *= .35f;
                }
            }
        }

        float ringTilt = gust * 16f;
        float driftX = (float) Math.cos(Math.toRadians(gustDir)) * gust * 1.15f;
        float driftZ = (float) Math.sin(Math.toRadians(gustDir)) * gust * 1.15f;

        // Violet death floor and doubled occult rings.
        pose.pushPose();pose.translate(cx-camera.x,cy+.035-camera.y,cz-camera.z);pose.scale((float)((radius+.35)*2),1f,(float)((radius+.35)*2));quadXZ(field,pose,(.24f + Math.abs(gust)*.08f)*boundaryAlpha);pose.popPose();
        pose.pushPose();pose.translate(cx-camera.x,cy+.028-camera.y,cz-camera.z);pose.mulPose(Axis.ZP.rotationDegrees(ringTilt));pose.mulPose(Axis.YP.rotationDegrees(time*1.8f));pose.scale((float)(radius*2),1f,(float)(radius*2));quadXZ(rings,pose,.70f*boundaryAlpha);pose.popPose();
        pose.pushPose();pose.translate(cx-camera.x+driftX*.08,cy+.055-camera.y,cz-camera.z+driftZ*.08);pose.mulPose(Axis.ZP.rotationDegrees(-ringTilt*.75f));pose.mulPose(Axis.YP.rotationDegrees(-time*1.1f));double innerRadius=Math.max(.08,radius-.45);pose.scale((float)(innerRadius*2),1f,(float)(innerRadius*2));quadXZ(rings,pose,(.38f + Math.abs(gust)*.10f)*boundaryAlpha);pose.popPose();

        // Five stacked walls of cards. Adjacent layers counter-rotate so the boundary feels alive.
        int perLayer=88, layers=5;
        for(int layer=0;layer<layers;layer++){
            double rr=radius+(layer%2==0?.02:.18);
            double phase=(layer%2==0?1:-1)*time*.0027 + layer*.028 + gust*.09*(layer+1);
            float baseY=(.75f+layer*1.22f)*(1f-deathT*.90f);
            for(int i=0;i<perLayer;i++){
                double ang=i*Math.PI*2.0/perLayer+phase;
                double x=cx+Math.cos(ang)*rr + driftX*(.18+layer*.06),z=cz+Math.sin(ang)*rr + driftZ*(.18+layer*.06);
                float yaw=(float)(-Math.toDegrees(ang)+90.0);
                float bob=(float)Math.sin(time*.10+i*.43+layer)*.18f + Math.abs(gust)*.12f;
                float sweepRoll=(float)Math.sin(time*.44+i*.63+layer)*13f + gust*24f;
                float sweepPitch=(float)Math.sin(i*.37+layer)*7f + gust*9f;
                pose.pushPose();pose.translate(x-camera.x,cy+baseY+bob-camera.y,z-camera.z);
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                pose.mulPose(Axis.XP.rotationDegrees(sweepPitch));
                pose.mulPose(Axis.ZP.rotationDegrees(sweepRoll));
                float sc=layer==2?.82f:.72f;pose.scale(sc,1.08f + Math.abs(gust)*.08f,1f);
                quadXY(cards,pose,.94f*boundaryAlpha);pose.mulPose(Axis.YP.rotationDegrees(180f));quadXY(cards,pose,.88f*boundaryAlpha);pose.popPose();
            }
        }

        // Purple energy lattice between card layers.
        int motes=144;
        for(int i=0;i<motes;i++){
            double ang=i*Math.PI*2.0/motes+time*.0105 + gust*.08,rr=radius+.12+Math.sin(i*.47+time*.02)*.12;
            double x=cx+Math.cos(ang)*rr+driftX*.22,z=cz+Math.sin(ang)*rr+driftZ*.22;
            float y=.25f+(i%14)*.43f+(float)Math.sin(time*.08+i)*.12f + Math.abs(gust)*.05f;
            pose.pushPose();pose.translate(x-camera.x,cy+y-camera.y,z-camera.z);pose.mulPose(event.getCamera().rotation());pose.mulPose(Axis.ZP.rotationDegrees(time*2.4f+i*9+gust*30f));pose.scale(.22f+.02f*Math.abs(gust),.22f+.02f*Math.abs(gust),1f);quadXY(wisps,pose,(.60f+.08f*Math.abs(gust))*boundaryAlpha);pose.popPose();
        }

        // Floating card crown above the wall warns airborne escape attempts too.
        int crown=72;
        for(int i=0;i<crown;i++){
            double ang=i*Math.PI*2.0/crown-time*.004 + gust*.05,rr=Math.max(.06,radius-.25);
            double x=cx+Math.cos(ang)*rr+driftX*.30,z=cz+Math.sin(ang)*rr+driftZ*.30;
            pose.pushPose();pose.translate(x-camera.x,cy+(7.05*(1f-deathT*.93f))+Math.sin(time*.07+i*.5)*.28+Math.abs(gust)*.18-camera.y,z-camera.z);pose.mulPose(Axis.YP.rotationDegrees((float)(-Math.toDegrees(ang)+90)));pose.mulPose(Axis.ZP.rotationDegrees(time*1.7f+i*11+gust*40f));pose.scale(.62f,.92f,1f);quadXY(cards,pose,.80f*boundaryAlpha);pose.mulPose(Axis.YP.rotationDegrees(180));quadXY(cards,pose,.70f*boundaryAlpha);pose.popPose();
        }
    }

    @SubscribeEvent
    public static void boundaryLodestone(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();syncWorld(mc);if(mc.level==null||mc.player==null||mc.isPaused())return;
        boolean reduced=WardConfig.accessibilityReduceMotion;long now=mc.level.getGameTime();int cadence=reduced?5:2;if(now%cadence!=0)return;
        Color violet=new Color(174,92,255), deep=new Color(66,20,105), magenta=new Color(222,75,213);
        int owners=0;
        for(var ent:mc.level.entitiesForRendering()){
            if(!(ent instanceof PaleGamblerEntity boss)||boss.isRemoved()||boss.distanceToSqr(mc.player)>6400||++owners>3)continue;
            int deathClock=boss.deathClock();float deathT=deathClock<0?0f:Mth.clamp(deathClock/36f,0f,1f);if(deathClock>=36)continue;
            double radius=(GamblerPatterns.BOUNDARY_RADIUS+.72)*(1.0-deathT*.955);int count=Math.max(4,Math.round((reduced?18:52)*(1f-deathT*.68f)));
            for(int i=0;i<count;i++){
                double ang=(i*2.399963229728653)+(now*.019)+(boss.getId()*.11),h=.2+(i%15)*.43+((i/15)%2)*.14;
                double rr=radius+(i%3-1)*.12,x=boss.getX()+Math.cos(ang)*rr,z=boss.getZ()+Math.sin(ang)*rr;
                double tx=-Math.sin(ang)*.018*(i%2==0?1:-1),tz=Math.cos(ang)*.018*(i%2==0?1:-1);
                WorldParticleBuilder.create(LodestoneParticleRegistry.WISP_PARTICLE)
                        .setColorData(ColorParticleData.create(i%4==0?magenta:violet,deep).build())
                        .setTransparencyData(GenericParticleData.create(.92f,0f).build())
                        .setScaleData(GenericParticleData.create(reduced?.10f:.15f,0f).build())
                        .setLifetime(reduced?24:34).addMotion(tx,.012+(i%5)*.002,tz)
                        .setRandomMotion(.014,.010,.014).enableNoClip().spawn(mc.level,x,boss.getY()+h,z);
            }
        }
    }

    private static void quadXY(VertexConsumer v, PoseStack pose, float alpha) {
        PoseStack.Pose p = pose.last();
        vertex(v,p,-1,-1,0,0,1,alpha,0,0,1);
        vertex(v,p, 1,-1,0,1,1,alpha,0,0,1);
        vertex(v,p, 1, 1,0,1,0,alpha,0,0,1);
        vertex(v,p,-1, 1,0,0,0,alpha,0,0,1);
    }

    private static void quadXZ(VertexConsumer v, PoseStack pose, float alpha) {
        PoseStack.Pose p = pose.last();
        vertex(v,p,-1,0,-1,0,1,alpha,0,1,0);
        vertex(v,p,-1,0, 1,0,0,alpha,0,1,0);
        vertex(v,p, 1,0, 1,1,0,alpha,0,1,0);
        vertex(v,p, 1,0,-1,1,1,alpha,0,1,0);
    }

    private static void vertex(VertexConsumer v, PoseStack.Pose p, float x,float y,float z,
                               float u,float vv,float alpha,float nx,float ny,float nz) {
        v.vertex(p.pose(),x,y,z).color(255,255,255,Math.max(0,Math.min(255,Math.round(alpha*255f))))
                .uv(u,vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880)
                .normal(p.normal(),nx,ny,nz).endVertex();
    }
}
