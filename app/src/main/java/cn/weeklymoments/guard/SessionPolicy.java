package cn.weeklymoments.guard;

/** One weekly allowance, with at most three explicit pauses. No background auto-pause. */
public final class SessionPolicy {
    public final AccessPolicy cycle;
    public final long spent, anchor;
    public final int anchorBoot, pauses;
    public final boolean paused;
    public SessionPolicy(AccessPolicy cycle, long spent, long anchor, int anchorBoot, int pauses, boolean paused) {
        this.cycle=cycle; this.spent=Math.max(0,spent); this.anchor=anchor;
        this.anchorBoot=anchorBoot; this.pauses=Math.max(0,pauses); this.paused=paused;
    }
    public long remaining(long wall,long elapsed,int boot) {
        if(!cycle.hasStarted() || cycle.ended || cycle.remainingCooldown(wall,elapsed,boot)==0) return 0;
        if(paused) return Math.max(0,AccessPolicy.WINDOW_MS-spent);
        if(anchorBoot<0 || boot!=anchorBoot || elapsed<anchor) return 0;
        return Math.max(0,AccessPolicy.WINDOW_MS-spent-(elapsed-anchor));
    }
    public boolean canPause(long wall,long elapsed,int boot) {
        return !paused && pauses<3 && remaining(wall,elapsed,boot)>0;
    }
}
