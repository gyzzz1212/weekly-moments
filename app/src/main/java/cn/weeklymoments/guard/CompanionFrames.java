package cn.weeklymoments.guard;

/** Sleep between authored state changes instead of repainting an idle character every 50–100 ms. */
final class CompanionFrames {
    static int standing(long age,long responseLeft){if(responseLeft>0)return 2+(int)(age/240%2);return age%5800>=5520?1:0;}
    static long standingDelay(long age,long responseLeft){
        if(responseLeft>0)return Math.max(1,Math.min(responseLeft,240-age%240));
        long phase=age%5800;return phase<5520?5520-phase:5800-phase;
    }
    static boolean blink(long age){long p=age%6400;return p>=5950&&p<6140;}
    static float smile(long age){long p=age%15200;return p>=10000&&p<12000?(float)Math.sin((p-10000)*Math.PI/2000):0;}
    static long portraitDelay(long age){
        long blink=age%6400,smile=age%15200;
        if(blink>=5950&&blink<6140||smile>=10000&&smile<12000)return 40;
        long b=blink<5950?5950-blink:6400-blink+5950;
        long s=smile<10000?10000-smile:15200-smile+10000;
        return Math.min(b,s);
    }
    private CompanionFrames(){}
}
