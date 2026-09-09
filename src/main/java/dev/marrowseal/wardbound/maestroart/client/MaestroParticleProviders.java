package dev.marrowseal.wardbound.maestroart.client;

import dev.marrowseal.wardbound.maestroart.MaestroParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="wardbound",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class MaestroParticleProviders {
    @SubscribeEvent public static void register(RegisterParticleProvidersEvent event){
        event.registerSpriteSet(MaestroParticles.NOTE,s->(type,w,x,y,z,vx,vy,vz)->new Glyph(w,x,y,z,vx,vy,vz,s,0));
        event.registerSpriteSet(MaestroParticles.ARC,s->(type,w,x,y,z,vx,vy,vz)->new Glyph(w,x,y,z,vx,vy,vz,s,1));
        event.registerSpriteSet(MaestroParticles.RING,s->(type,w,x,y,z,vx,vy,vz)->new Glyph(w,x,y,z,vx,vy,vz,s,2));
        event.registerSpriteSet(MaestroParticles.BURST,s->(type,w,x,y,z,vx,vy,vz)->new Glyph(w,x,y,z,vx,vy,vz,s,3));
    }
    private static final class Glyph extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final int kind;
        private final float size;
        Glyph(ClientLevel w,double x,double y,double z,double vx,double vy,double vz,SpriteSet sprites,int kind){
            super(w,x,y,z,0,0,0);this.sprites=sprites;this.kind=kind;
            xd=vx;yd=vy;zd=vz;hasPhysics=false;friction=.94f;gravity=0;
            lifetime=kind==0?30:kind==1?12:kind==2?22:16;
            size=kind==0?.105f:kind==1?.09f:kind==2?.17f:.14f;
            quadSize=size;setSize(.08f,.08f);setSpriteFromAge(sprites);
        }
        @Override public void tick(){
            super.tick();float f=(float)age/lifetime;alpha=Math.max(0,1-f*f);
            quadSize=size*(kind>=2?1+f*1.7f:1-f*.25f);
            oRoll=roll;if(kind==0)roll+=.012f;
            setSpriteFromAge(sprites);
        }
        @Override public int getLightColor(float partialTick){return 0xF000F0;}
        @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
    }
}
