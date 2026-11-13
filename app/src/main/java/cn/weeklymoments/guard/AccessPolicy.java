package cn.weeklymoments.guard;

/** Pure timing policy: one continuous window per rolling seven days. */
public final class AccessPolicy {
    public static final long WINDOW_MS = 15L * 60 * 1000;
    public static final long COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000;
    public final long startWall, startElapsed;
    public final int bootId;
    public final boolean ended;

    public AccessPolicy(long startWall, long startElapsed, int bootId, boolean ended) {
        this.startWall = startWall;
        this.startElapsed = startElapsed;
        this.bootId = bootId;
        this.ended = ended;
    }

    public boolean hasStarted() { return startWall > 0; }

    private boolean sameBoot(long elapsed, int boot) {
        return bootId >= 0 && bootId == boot && elapsed >= startElapsed;
    }

    public long remainingOpen(long elapsed, int boot) {
        // Reboot or an unknown boot counter ends the window conservatively.
        if (!hasStarted() || ended || !sameBoot(elapsed, boot)) return 0;
        return Math.max(0, WINDOW_MS - (elapsed - startElapsed));
    }

    public long remainingCooldown(long wall, long elapsed, int boot) {
        if (!hasStarted()) return 0;
        long age = sameBoot(elapsed, boot) ? elapsed - startElapsed : wall - startWall;
        // A backwards clock never grants a new window.
        if (age < 0) return COOLDOWN_MS;
        return Math.max(0, COOLDOWN_MS - age);
    }

    public boolean canStart(long wall, long elapsed, int boot) {
        return remainingCooldown(wall, elapsed, boot) == 0;
    }
}
