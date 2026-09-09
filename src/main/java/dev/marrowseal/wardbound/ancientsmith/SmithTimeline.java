package dev.marrowseal.wardbound.ancientsmith;
/** Pure server timeline. All ticks are relative to a scene starting at zero. */
public final class SmithTimeline {
 public enum State {
  IDLE_HEAVY("idle_heavy",true),FORGE_IDLE("forge_idle",true),HAMMER_RAISE("hammer_raise",false),HAMMER_STRIKE("hammer_strike",false),HAMMER_STRIKE_HEAVY("hammer_strike_heavy",false),INSPECT_WEAPON("inspect_weapon",false),PLACE_WEAPON_ON_ANVIL("place_weapon_on_anvil",false),TAKE_WEAPON_FROM_ANVIL("take_weapon_from_anvil",false),THROW_WEAPON_TO_PLAYER("throw_weapon_to_player",false),TURN_TO_PLAYER("turn_to_player",false),TALK_SHORT("talk_short",false),DISMISS_PLAYER("dismiss_player",false),RESUME_FORGING("resume_forging",false),LOOK_AT_NHAL_SUL("look_at_nhal_sul",false),SINGLE_FINAL_STRIKE("single_final_strike",false);
  public final String clip;public final boolean loop;State(String s,boolean l){clip=s;loop=l;}
 }
 public record Beat(int start,State state){}
 public static final int FIRST_HIT=172,SECOND_HIT=272,FINAL_HIT=368,SILENCE=370,OFFER=460,OFFER_RELEASE=480,TIMEOUT=2400;
 private static final Beat[] BEATS={new Beat(0,State.TURN_TO_PLAYER),new Beat(30,State.LOOK_AT_NHAL_SUL),new Beat(70,State.INSPECT_WEAPON),new Beat(100,State.PLACE_WEAPON_ON_ANVIL),new Beat(120,State.HAMMER_RAISE),new Beat(150,State.HAMMER_STRIKE),new Beat(192,State.TALK_SHORT),new Beat(220,State.HAMMER_RAISE),new Beat(250,State.HAMMER_STRIKE),new Beat(292,State.TALK_SHORT),new Beat(320,State.SINGLE_FINAL_STRIKE),new Beat(390,State.TAKE_WEAPON_FROM_ANVIL),new Beat(424,State.THROW_WEAPON_TO_PLAYER),new Beat(OFFER,State.TALK_SHORT),new Beat(490,State.IDLE_HEAVY)};
 public static Beat at(int tick){Beat b=BEATS[0];for(Beat n:BEATS){if(n.start>tick)break;b=n;}return b;}
 public static boolean impact(int tick){return tick==FIRST_HIT||tick==SECOND_HIT||tick==FINAL_HIT;}
 private SmithTimeline(){}
}
