package cn.weeklymoments.guard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

public final class PolicyStore {
    private static PolicyStore instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final int bootId;
    private String recentPage = "尚未识别到微信页面";

    public static synchronized PolicyStore get(Context c) {
        if (instance == null) instance = new PolicyStore(c.getApplicationContext());
        return instance;
    }

    private PolicyStore(Context c) {
        context = c;
        bootId=Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);
        prefs = c.getSharedPreferences("guard", Context.MODE_PRIVATE);
        prefs.edit().remove("last_page").apply();
    }

    public int boot() {
        return bootId;
    }

    public AccessPolicy policy() {
        return new AccessPolicy(prefs.getLong("start_wall", 0), prefs.getLong("start_elapsed", 0),
                prefs.getInt("start_boot", -1), prefs.getBoolean("ended", false));
    }

    public boolean canStart() {
        return boot() >= 0 && policy().canStart(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot());
    }

    public SessionPolicy session() {
        AccessPolicy p=policy();
        return new SessionPolicy(p,prefs.getLong("spent",0),prefs.getLong("anchor",p.startElapsed),
                prefs.getInt("anchor_boot",p.bootId),prefs.getInt("pauses",0),prefs.getBoolean("paused",false));
    }
    public long remaining() { return session().remaining(System.currentTimeMillis(),SystemClock.elapsedRealtime(),boot()); }
    public long openRemaining() { return session().paused ? 0 : remaining(); }
    public boolean paused() { return session().paused && remaining()>0; }
    public int pauses() { return session().pauses; }
    public synchronized boolean pause() {
        SessionPolicy s=session(); long wall=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime(); int b=boot();
        if(!s.canPause(wall,elapsed,b))return false;
        return prefs.edit().putLong("spent",AccessPolicy.WINDOW_MS-s.remaining(wall,elapsed,b))
                .putBoolean("paused",true).putInt("pauses",s.pauses+1).commit();
    }
    public synchronized boolean resume() {
        if(!paused() || boot()<0)return false;
        return prefs.edit().putBoolean("paused",false).putLong("anchor",SystemClock.elapsedRealtime())
                .putInt("anchor_boot",boot()).commit();
    }
    public long cooldownRemaining() {
        return policy().remainingCooldown(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot());
    }

    public synchronized boolean start() {
        int boot = boot();
        long wall = System.currentTimeMillis(), elapsed = SystemClock.elapsedRealtime();
        if (boot < 0 || !policy().canStart(wall, elapsed, boot)) return false;
        return prefs.edit().putLong("start_wall", wall).putLong("start_elapsed", elapsed)
                .putInt("start_boot", boot).putBoolean("ended", false).putLong("spent",0)
                .putLong("anchor",elapsed).putInt("anchor_boot",boot).putBoolean("paused",false).putInt("pauses",0).commit();
    }

    public boolean end() { return prefs.edit().putBoolean("ended", true).commit(); }
    public boolean allMoments() { return prefs.getBoolean("all_moments", false); }
    public void setAllMoments(boolean value) { prefs.edit().putBoolean("all_moments", value).apply(); }
    public boolean consented() { return prefs.getBoolean("consent", false); }
    public void consent() { prefs.edit().putBoolean("consent", true).apply(); }
    public String lastPage() { return recentPage; }
    public void recordPage(String page) { recentPage=page; }
    public long blockCount() { return prefs.getLong("block_count", 0); }
    public void recordBlock() { prefs.edit().putLong("block_count", blockCount() + 1).apply(); }
    public int lastQuoteIndex() { return prefs.getInt("last_quote", -1); }
    public void setLastQuoteIndex(int value) { prefs.edit().putInt("last_quote", value).apply(); }
}
