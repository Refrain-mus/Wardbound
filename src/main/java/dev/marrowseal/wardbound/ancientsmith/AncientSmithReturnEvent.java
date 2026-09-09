package dev.marrowseal.wardbound.ancientsmith;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.Event;

/** Posted after the smith scene closes. Teleport/realm cleanup is the integrator's decision. */
public final class AncientSmithReturnEvent extends Event {
    public final ServerPlayer player;
    public final String entryDimension;
    public final Vec3 entryPosition;
    public final float entryYaw, entryPitch;
    public final boolean completed, acquisition;

    public AncientSmithReturnEvent(ServerPlayer player, SmithSceneData.Session session, boolean complete) {
        this.player = player;
        this.entryDimension = session.entryDimension;
        this.entryPosition = session.origin;
        this.entryYaw = session.yaw;
        this.entryPitch = session.pitch;
        this.completed = complete;
        this.acquisition = session.acquisition;
    }
}
