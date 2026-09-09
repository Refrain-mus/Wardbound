package dev.marrowseal.wardbound.boss;

import java.util.ArrayList;
import java.util.List;

public final class GamblerPatterns {
    private GamblerPatterns() {}
    public static final double RADIUS=16;
    /** Visual/kill boundary follows the authored arena, while attack geometry keeps its original 16-block balance. */
    public static final double BOUNDARY_RADIUS=RADIUS+3.0;
    public static final int FAN=1,RINGS=2,MARKS=3,SWEEP=4,CAGE=5,ROYAL=6,FLOOR_CARDS=7,FORCED_CHOICE=8,
            HOUSE_TEETH=9,STARFALL=10,CARD_WALL=11,SPIRAL=12,DEALER_CROSS=13,CHIP_STORM=14,SPLIT_POT=15,
            STACKED_DECK=16,BURIED_ACE=17,MIRROR_DEBT=18,HOUSE_EDGE=19,DOUBLE_DOWN=20,
            DEAD_MANS_DRAW=21,FALSE_TELL=22,SUIT_CALL=23,ROYAL_FLUSH=24,LAST_DEAL=25;
    private static final int[] ORDER={
            FAN,MARKS,STARFALL,FLOOR_CARDS,SWEEP,HOUSE_TEETH,CAGE,RINGS,CARD_WALL,FORCED_CHOICE,
            SPIRAL,ROYAL,DEALER_CROSS,MARKS,STARFALL,FAN,CHIP_STORM,CARD_WALL,FLOOR_CARDS,HOUSE_TEETH,
            SWEEP,SPLIT_POT,CAGE,SPIRAL,RINGS,FORCED_CHOICE,ROYAL,CHIP_STORM,DEALER_CROSS,SPLIT_POT,
            BURIED_ACE,STACKED_DECK,MIRROR_DEBT,HOUSE_EDGE,DOUBLE_DOWN,DEALER_CROSS,BURIED_ACE,STACKED_DECK,
            HOUSE_EDGE,DOUBLE_DOWN,MIRROR_DEBT,DEAD_MANS_DRAW,FALSE_TELL,SUIT_CALL,ROYAL_FLUSH,
            DEALER_CROSS,SPLIT_POT,DOUBLE_DOWN,DEAD_MANS_DRAW,FALSE_TELL,ROYAL_FLUSH
    };

    /** Rectangle in arena-local coordinates. width/height are half-extents. */
    public record CardZone(double x,double z,double halfWidth,double halfHeight,double angle,int suit) {}

    public static int phase(float f){return f>.67f?1:f>.34f?2:3;}
    public static int attack(int turn){return ORDER[Math.floorMod(turn,ORDER.length)];}
    public static int windup(int phase){return phase==1?34:phase==2?30:26;}
    public static int lanes(int phase){return phase==1?7:phase==2?9:11;}
    public static double laneAngle(double aim,int lane,int phase,int wave){return aim+(lane-(lanes(phase)-1)/2.0)*Math.toRadians(22)+wave*Math.toRadians(11);}
    public static boolean onRay(double x,double z,double angle){double along=x*Math.cos(angle)+z*Math.sin(angle);return along>=2.2 && along<=RADIUS && Math.abs(-x*Math.sin(angle)+z*Math.cos(angle))<=.8;}
    public static double ring(int wave,int phase){return phase==3?15-wave*3:3+wave*3;}
    public static boolean onRing(double x,double z,double radius){return Math.abs(Math.hypot(x,z)-radius)<=1;}
    public static boolean onMark(double x,double z,double mx,double mz){return Math.hypot(x-mx,z-mz)<=2.3;}
    public static boolean onGrid(double x,double z,int wave){for(int i=-2;i<=2;i++){double k=i*5+(wave%2)*2.5;if(Math.abs(x-k)<.7 || Math.abs(z-k)<.7)return true;}return false;}
    public static boolean inSanctuary(double x,double z,int wave){for(int i=0;i<4;i++){double a=i*Math.PI/2+wave*Math.PI/4;if(Math.hypot(x-10*Math.cos(a),z-10*Math.sin(a))<=3.2)return true;}return false;}

    public static int teethLanes(int phase){return phase==1?5:phase==2?6:7;}
    public static double teethAngle(double aim,int lane,int phase,int wave){
        int n=teethLanes(phase);
        return aim+(lane-(n-1)/2.0)*Math.toRadians(26)+wave*Math.toRadians(13);
    }

    public static int spiralSpokes(int phase){return phase==1?4:phase==2?5:6;}
    public static int spiralWaves(int phase){return phase==1?4:5;}
    public static double spiralAngle(double aim,int spoke,int phase,int wave){
        return aim+spoke*(Math.PI*2.0/spiralSpokes(phase))+wave*Math.toRadians(15+phase*2);
    }

    public static int wallWaves(int phase){return phase==1?4:5;}
    public static double wallOffset(int wave,int phase){
        int n=wallWaves(phase);
        return n<=1?0:-9.5+wave*(19.0/(n-1));
    }
    public static double wallGap(int wave,int phase){
        if(phase==3 && wave==wallWaves(phase)-1)return 0;
        return (wave&1)==0?-6.0:6.0;
    }
    public static boolean wallVertical(int turn){return (turn&1)==0;}
    public static boolean onWall(double x,double z,int wave,int phase,int turn){
        double offset=wallOffset(wave,phase),gap=wallGap(wave,phase);
        if(wallVertical(turn))return Math.abs(x-offset)<=.9 && Math.abs(z-gap)>2.7;
        return Math.abs(z-offset)<=.9 && Math.abs(x-gap)>2.7;
    }

    /**
     * Large card-shaped safe zones for the arena attack. The layout rotates from
     * hand to hand so memorising one set of coordinates is not enough.
     */
    public static List<CardZone> floorCards(int turn,int phase) {
        int count = phase == 1 ? 4 : 3;
        double rotation = Math.floorMod(turn, 8) * Math.PI / 16.0;
        double radius = phase == 3 ? 8.6 : 9.2;
        double halfW = phase == 3 ? 2.15 : 2.45;
        double halfH = phase == 3 ? 3.55 : 3.95;
        List<CardZone> out = new ArrayList<>(count);
        for (int i=0;i<count;i++) {
            double a = rotation + i * Math.PI * 2.0 / count;
            double cx = Math.cos(a) * radius;
            double cz = Math.sin(a) * radius;
            out.add(new CardZone(cx, cz, halfW, halfH, a + Math.PI / 2.0, Math.floorMod(turn+i,4)));
        }
        return List.copyOf(out);
    }

    public static boolean insideCard(double x,double z,CardZone c) {
        double dx=x-c.x(), dz=z-c.z();
        double cos=Math.cos(c.angle()), sin=Math.sin(c.angle());
        double localX=dx*cos+dz*sin;
        double localZ=-dx*sin+dz*cos;
        return Math.abs(localX)<=c.halfWidth() && Math.abs(localZ)<=c.halfHeight();
    }


    public static int crossSpokes(int phase){return phase==1?4:phase==2?5:6;}
    public static int crossWaves(int phase){return phase==1?2:3;}
    public static double crossAngle(double aim,int spoke,int phase,int wave){
        return aim + spoke*(Math.PI*2.0/crossSpokes(phase)) + wave*Math.toRadians(27+phase*4);
    }

    public static int chipCount(int phase){return phase==1?4:phase==2?5:6;}
    public static double chipRadius(int phase){return phase==3?2.8:2.55;}
    public static List<double[]> chipMarks(int turn,int phase){
        int count=chipCount(phase);
        double rot=Math.floorMod(turn,12)*Math.PI/18.0;
        double orbit=phase==3?9.0:10.0;
        List<double[]> out=new ArrayList<>(count);
        for(int i=0;i<count;i++){
            double a=rot+i*Math.PI*2.0/count;
            double r=orbit+(i%2==0?-1.15:1.15);
            out.add(new double[]{Math.cos(a)*r,Math.sin(a)*r});
        }
        return List.copyOf(out);
    }

    public static boolean chipHit(double x,double z,double mx,double mz,int phase){
        return Math.hypot(x-mx,z-mz)<=chipRadius(phase);
    }

    /** Alternating inner / outer / middle pot. True means that distance is struck this wave. */
    public static boolean splitPotUnsafe(double distance,int wave,int phase){
        int mode=Math.floorMod(wave,3);
        if(mode==0)return distance<=6.3;
        if(mode==1)return distance>=10.2;
        double inner=phase==3?5.2:5.8, outer=phase==3?11.8:11.2;
        return distance>=inner && distance<=outer;
    }

    public static int splitPotWaves(int phase){return phase==1?2:3;}


    /** Hostile card rectangles: unlike Floor Cards these are the places the player must NOT occupy. */
    public static List<CardZone> dangerCards(int turn,int phase) {
        int count=phase==1?3:phase==2?4:5;
        double rotation=Math.floorMod(turn,10)*Math.PI/20.0;
        double orbit=phase==3?7.6:8.4;
        double halfW=phase==3?1.85:2.05,halfH=phase==3?3.15:3.35;
        List<CardZone> out=new ArrayList<>(count);
        for(int i=0;i<count;i++){
            double a=rotation+i*Math.PI*2.0/count;
            double r=orbit+((i&1)==0?-1.0:1.0);
            out.add(new CardZone(Math.cos(a)*r,Math.sin(a)*r,halfW,halfH,a+Math.PI/2.0,Math.floorMod(turn+i+1,4)));
        }
        return List.copyOf(out);
    }

    public static int stackedWaves(int phase){return phase==3?2:1;}

    /** Four seals on the felt. One is the buried ace/safe seal; everything else is a bluff. */
    public static List<double[]> buriedAceMarks(int turn){
        double rot=Math.floorMod(turn,8)*Math.PI/16.0;
        List<double[]> out=new ArrayList<>(4);
        for(int i=0;i<4;i++){double a=rot+i*Math.PI/2.0;out.add(new double[]{Math.cos(a)*9.5,Math.sin(a)*9.5});}
        return List.copyOf(out);
    }
    public static double buriedAceRadius(){return 2.75;}
    public static boolean inBuriedAce(double x,double z,double mx,double mz){return Math.hypot(x-mx,z-mz)<=buriedAceRadius();}

    public static int mirrorWaves(int phase){return 2;}


    /** House Edge: a rotating annulus plus card-blade spokes. */
    public static int houseEdgeWaves(int phase){return phase==1?1:phase==2?2:3;}
    public static int houseEdgeSpokes(int phase){return phase==1?4:phase==2?5:6;}
    public static double houseEdgeRadius(int wave,int phase){return 5.8 + Math.floorMod(wave+phase,3)*3.25;}
    public static double houseEdgeAngle(int turn,int spoke,int phase,int wave){
        return Math.floorMod(turn,16)*Math.PI/32.0 + spoke*(Math.PI*2.0/houseEdgeSpokes(phase)) + wave*Math.toRadians(17+phase*3);
    }
    public static boolean houseEdgeUnsafe(double x,double z,int turn,int phase,int wave){
        double r=houseEdgeRadius(wave,phase);
        if(Math.abs(Math.hypot(x,z)-r)<=1.0)return true;
        for(int s=0;s<houseEdgeSpokes(phase);s++)if(onRay(x,z,houseEdgeAngle(turn,s,phase,wave)))return true;
        return false;
    }

    /** Double Down: two opposite safe circles rotate between waves; everything else is collected by the House. */
    public static int doubleDownWaves(int phase){return phase==3?3:2;}
    public static double doubleDownRadius(){return 3.15;}
    public static List<double[]> doubleDownSafe(int turn,int wave){
        double a=Math.floorMod(turn,12)*Math.PI/24.0 + wave*Math.PI/4.0;
        double r=9.4;
        return List.of(new double[]{Math.cos(a)*r,Math.sin(a)*r},new double[]{-Math.cos(a)*r,-Math.sin(a)*r});
    }
    public static boolean inDoubleDownSafe(double x,double z,double sx,double sz){return Math.hypot(x-sx,z-sz)<=doubleDownRadius();}



    /** Dead Man's Draw: four coffin-like cards around the table are collected in sequence. */
    public static List<CardZone> deadMansCards(int turn) {
        double rotation=Math.floorMod(turn,12)*Math.PI/24.0;
        List<CardZone> out=new ArrayList<>(4);
        for(int i=0;i<4;i++){
            double a=rotation+i*Math.PI/2.0;
            double r=8.8;
            out.add(new CardZone(Math.cos(a)*r,Math.sin(a)*r,2.05,3.35,a+Math.PI/2.0,i));
        }
        return List.copyOf(out);
    }
    public static int deadMansWaves(int phase){return phase>=3?5:4;}
    public static int[] deadMansActive(int turn,int phase,int wave){
        int first=Math.floorMod(turn+wave,4);
        if(phase>=3 && wave==deadMansWaves(phase)-1)return new int[]{first,Math.floorMod(first+2,4)};
        return new int[]{first};
    }

    /** False Tell: only two spokes are real. The remaining spokes are deliberate bluffs. */
    public static int falseTellSpokes(int phase){return phase>=3?8:6;}
    public static int[] falseTellReal(int turn,int phase){
        int n=falseTellSpokes(phase);
        int a=Math.floorMod(turn*3+phase,n);
        int b=Math.floorMod(a+(phase>=3?3:2),n);
        return new int[]{a,b};
    }
    public static double falseTellAngle(double aim,int spoke,int phase,int turn){
        return aim+spoke*(Math.PI*2.0/falseTellSpokes(phase))+Math.floorMod(turn,6)*Math.PI/36.0;
    }
    public static boolean falseTellRealSpoke(int turn,int phase,int spoke){
        for(int i:falseTellReal(turn,phase))if(i==spoke)return true;
        return false;
    }

    /** Four suit seals used for per-player Suit Call assignments. */
    public static List<double[]> suitMarks(int turn){
        double rot=Math.floorMod(turn,8)*Math.PI/16.0;
        List<double[]> out=new ArrayList<>(4);
        for(int i=0;i<4;i++){
            double a=rot+i*Math.PI/2.0;
            out.add(new double[]{Math.cos(a)*9.6,Math.sin(a)*9.6});
        }
        return List.copyOf(out);
    }
    public static double suitRadius(){return 3.05;}

    /** Five-card royal hand. Two cards are collected on each wave. */
    public static List<CardZone> royalFlushCards(int turn){
        double rotation=Math.floorMod(turn,10)*Math.PI/20.0;
        List<CardZone> out=new ArrayList<>(5);
        for(int i=0;i<5;i++){
            double a=rotation+i*Math.PI*2.0/5.0;
            double r=(i&1)==0?8.1:10.0;
            out.add(new CardZone(Math.cos(a)*r,Math.sin(a)*r,1.75,2.95,a+Math.PI/2.0,i&3));
        }
        return List.copyOf(out);
    }
    public static int royalFlushWaves(int phase){return phase>=3?4:3;}
    public static int[] royalFlushActive(int turn,int wave){
        int a=Math.floorMod(turn+wave*2,5);
        return new int[]{a,Math.floorMod(a+2,5)};
    }

    public static int lastDealStages(){return 6;}

    public static double health(int players){return BossConfig.gambler().health()+BossConfig.gambler().healthPerAlly()*(Math.max(1,Math.min(4,players))-1);}
    public static int ticks(int base,double speed){return Math.max(12,(int)Math.round(base/speed));}
}
