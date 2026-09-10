package dev.marrowseal.wardbound.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.CthulhuFieldPacket;
import org.joml.Vector3f;
import team.lodestar.lodestone.registry.common.particle.LodestoneParticleRegistry;
import team.lodestar.lodestone.systems.particle.builder.WorldParticleBuilder;
import team.lodestar.lodestone.systems.particle.data.GenericParticleData;
import team.lodestar.lodestone.systems.particle.data.color.ColorParticleData;

import java.awt.Color;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mod.EventBusSubscriber(modid = Wardbound.MODID, value = Dist.CLIENT)
public final class CthulhuIdolFieldFx {

    private static final List<Field> ACTIVE = new ArrayList<>();
    private static Object levelIdentity;
    private static final Vector3f TEAL = new Vector3f(0.52f, 0.96f, 0.90f);
    private static final Vector3f BLUE = new Vector3f(0.28f, 0.73f, 0.92f);
    private static final Vector3f GOLD = new Vector3f(0.96f, 0.88f, 0.62f);
    private static final Vector3f VIOLET = new Vector3f(0.58f, 0.34f, 0.86f);

    private CthulhuIdolFieldFx() {}

    public static void start(CthulhuFieldPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        ACTIVE.add(new Field(new Vec3(msg.x, msg.y, msg.z), msg.radius, now, now + Math.max(1, msg.durationTicks)));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != levelIdentity) {
            ACTIVE.clear();
            levelIdentity = level;
        }
        if (level == null) return;
        long time = level.getGameTime();
        Iterator<Field> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Field f = it.next();
            if (time >= f.endTick) {
                it.remove();
                continue;
            }
            render(level, f, time);
        }
    }

    private static void render(ClientLevel level, Field f, long time) {
        double t = time - f.startTick;
        double spin = t * 0.14;
        double counter = -t * 0.11;
        double radius = f.radius;

        double[] normalizedBands = {-0.85, -0.45, 0.0, 0.45, 0.85};
        for (int ring = 0; ring < normalizedBands.length; ring++) {
            double yNorm = normalizedBands[ring];
            double y = f.center.y + yNorm * radius;
            double bandRadius = radius * Math.sqrt(Math.max(0.08, 1.0 - yNorm * yNorm));
            int count = 12 + (ring == 2 ? 8 : 4);
            for (int i = 0; i < count; i++) {
                double ang = spin * (ring % 2 == 0 ? 1.0 : -1.0) + i * (Mth.TWO_PI / count);
                double px = f.center.x + Math.cos(ang) * bandRadius;
                double pz = f.center.z + Math.sin(ang) * bandRadius;
                Color col = ring == 2 ? vec(TEAL) : (Math.abs(yNorm) < 0.5 ? vec(BLUE) : vec(GOLD));
                float size = ring == 2 ? 0.22f : (Math.abs(yNorm) > 0.6 ? 0.15f : 0.18f);
                wisp(level, px, y, pz, col, vec(VIOLET), size, 18, 0.0, 0.006, 0.0, 0.01);
            }
        }

        for (int meridian = 0; meridian < 6; meridian++) {
            double baseAng = counter + meridian * (Mth.TWO_PI / 6.0);
            for (int j = 0; j < 7; j++) {
                double lat = -1.0 + (j / 6.0) * 2.0;
                double localRadius = radius * Math.sqrt(Math.max(0.0, 1.0 - lat * lat));
                double px = f.center.x + Math.cos(baseAng) * localRadius;
                double py = f.center.y + lat * radius;
                double pz = f.center.z + Math.sin(baseAng) * localRadius;
                wisp(level, px, py, pz, meridian % 2 == 0 ? vec(TEAL) : vec(VIOLET), vec(GOLD), 0.14f, 16, 0.0, 0.007, 0.0, 0.008);
            }
        }

        for (int i = 0; i < 14; i++) {
            double phase = i / 14.0;
            double lat = Mth.sin((float) ((t * 0.055 + phase) * Mth.TWO_PI));
            double localRadius = radius * (0.22 + 0.48 * Math.sqrt(Math.max(0.0, 1.0 - lat * lat)));
            double angA = spin + phase * Mth.TWO_PI;
            double angB = angA + Math.PI;
            double y = f.center.y + lat * radius;
            wisp(level, f.center.x + Math.cos(angA) * localRadius, y, f.center.z + Math.sin(angA) * localRadius, vec(TEAL), vec(BLUE), 0.16f, 20, 0.0, 0.01, 0.0, 0.01);
            wisp(level, f.center.x + Math.cos(angB) * localRadius, y, f.center.z + Math.sin(angB) * localRadius, vec(VIOLET), vec(GOLD), 0.15f, 20, 0.0, 0.01, 0.0, 0.01);
        }

        if (time % 2 == 0) {
            for (int i = 0; i < 8; i++) {
                double ang = counter + i * (Mth.TWO_PI / 8.0);
                double edgeRadius = radius * 0.82;
                double px = f.center.x + Math.cos(ang) * edgeRadius;
                double pz = f.center.z + Math.sin(ang) * edgeRadius;
                double yCycle = ((time + i * 3) % 26) / 25.0;
                double py = f.center.y - radius + yCycle * (radius * 2.0);
                wisp(level, px, py, pz, vec(GOLD), vec(TEAL), 0.14f, 16, 0.0, 0.012, 0.0, 0.012);
            }
        }

        for (int i = 0; i < 5; i++) {
            double lat = -0.7 + i * 0.35 + Mth.sin((float) (t * 0.18 + i)) * 0.05;
            wisp(level,
                    f.center.x + Math.sin(t * 0.15 + i) * 0.10,
                    f.center.y + lat * radius,
                    f.center.z + Math.cos(t * 0.15 + i) * 0.10,
                    vec(BLUE), vec(TEAL), 0.18f, 22, 0.0, 0.018, 0.0, 0.008);
        }

        if (f.endTick - time < 20) {
            for (int i = 0; i < 10; i++) {
                double ang = spin + i * (Mth.TWO_PI / 10.0);
                double shrink = ((f.endTick - time) / 20.0);
                double r = radius * 0.55 * shrink;
                double py = f.center.y + ((i % 5) - 2) * 0.24 * shrink;
                wisp(level, f.center.x + Math.cos(ang) * r, py, f.center.z + Math.sin(ang) * r, vec(VIOLET), vec(TEAL), 0.22f, 18, 0.0, 0.02, 0.0, 0.02);
            }
        }
    }

    private static Color vec(Vector3f v) {
        return new Color(Mth.clamp(v.x, 0f, 1f), Mth.clamp(v.y, 0f, 1f), Mth.clamp(v.z, 0f, 1f));
    }

    private static void wisp(ClientLevel level, double x, double y, double z, Color start, Color end,
                             float scale, int lifetime, double mx, double my, double mz, double randomMotion) {
        WorldParticleBuilder.create(LodestoneParticleRegistry.WISP_PARTICLE)
                .setColorData(ColorParticleData.create(start, end).build())
                .setTransparencyData(GenericParticleData.create(.88f, 0f).build())
                .setScaleData(GenericParticleData.create(scale, 0f).build())
                .setLifetime(lifetime)
                .addMotion(mx, my, mz)
                .setRandomMotion(randomMotion, randomMotion * .7, randomMotion)
                .enableNoClip()
                .spawn(level, x, y, z);
    }

    private record Field(Vec3 center, float radius, long startTick, long endTick) {}
}
