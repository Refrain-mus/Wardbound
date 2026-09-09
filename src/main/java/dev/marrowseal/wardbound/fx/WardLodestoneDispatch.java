package dev.marrowseal.wardbound.fx;

import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.WardLodestoneFxPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

public final class WardLodestoneDispatch {
    private WardLodestoneDispatch() {}

    public static void emit(ServerLevel level, Vec3 pos, int kind, float scale) {
        Wardbound.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                pos.x, pos.y, pos.z, 48.0, level.dimension())), new WardLodestoneFxPacket(pos.x, pos.y, pos.z, kind, scale));
    }

    public static void emit(ServerPlayer player, int kind, float scale) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return;
        emit(level, new Vec3(player.getX(), player.getY() + 1.0, player.getZ()), kind, scale);
    }

    public static void emit(ServerPlayer player, int kind, float scale, double yOffset) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return;
        emit(level, new Vec3(player.getX(), player.getY() + yOffset, player.getZ()), kind, scale);
    }

    public static void emit(Entity entity, int kind, float scale) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) return;
        emit(level, entity.position().add(0, entity.getBbHeight() * 0.55, 0), kind, scale);
    }
}
