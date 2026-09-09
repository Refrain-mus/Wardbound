package dev.marrowseal.wardbound.champion;
import java.util.UUID;

/** A persistent person plus a replaceable incarnation rejects entities from stale unloaded chunks. */
public final class ChampionIdentity {
    public static boolean accepts(UUID person,long incarnation,boolean dead,UUID incoming,long token){
        return !dead&&person!=null&&person.equals(incoming)&&incarnation==token;
    }
    public static boolean travelReady(long now,long arrival,long lastSeen,long delay,boolean observed,boolean fighting){
        return !observed&&!fighting&&now>=arrival&&now>=lastSeen&&now-arrival>=delay&&now-lastSeen>=delay;
    }
    private ChampionIdentity(){}
}
