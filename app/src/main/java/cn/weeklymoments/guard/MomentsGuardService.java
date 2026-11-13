package cn.weeklymoments.guard;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.app.KeyguardManager;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.PowerManager;
import android.telecom.TelecomManager;
import android.media.AudioManager;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;
import java.time.ZoneId;

public final class MomentsGuardService extends AccessibilityService {
    public static volatile boolean connected;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PolicyStore store;
    private String page = "", foregroundPackage = "", foregroundClass = "";
    private int pageWindow = -1;
    private boolean personalOrigin, watching;
    private FocusReminderView overlay;
    private LifePromptView lifeOverlay;
    private String dialer = "", clockPackage = "";
    private final java.util.Set<String> clockPackages=new java.util.HashSet<>();
    private OverlayInteraction interaction;
    private final java.util.concurrent.ExecutorService ruleWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.ExecutorService diskWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile boolean active;
    private volatile LifeStore lifeStore;
    private LifeData evaluatedData;
    private LifeEngine.Due evaluatedDue=LifeEngine.NONE;
    private ZoneId evaluatedZone;
    private long nextRuleCheck,ruleWall,ruleElapsed,retryRulesAfter;
    private boolean ruleQueued;
    private String keyboardPackage="";
    private long keyboardChecked=-3000;
    private long lastFallbackBack;
    private int fallbackAttempts;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            LifeScheduler.tick(MomentsGuardService.this);
            enforce();
            handler.postDelayed(this, watching ? 400 : 1500);
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        store = PolicyStore.get(this);
        connected = true;active=true;
        interaction=new OverlayInteraction(SystemClock::elapsedRealtime,task->handler.post(task),diskWorker,
                ()->{hideOverlay();hideLifeOverlay();},message->Toast.makeText(this,message,Toast.LENGTH_LONG).show());
        requestRules();
        TelecomManager phone = (TelecomManager)getSystemService(TELECOM_SERVICE);
        if (phone != null && phone.getDefaultDialerPackage() != null) dialer = phone.getDefaultDialerPackage();
        android.content.pm.ResolveInfo clock=getPackageManager().resolveActivity(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if(clock!=null&&clock.activityInfo!=null)clockPackage=clock.activityInfo.packageName;
        clockPackages.addAll(SystemClockAccess.packages(this));
        LifeScheduler.refresh(this);
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Only package/activity metadata. Never access event text, source, node trees or keys.
        if(store==null || event==null || event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return;
        String pkg=event.getPackageName()==null?"":event.getPackageName().toString();
        String name=event.getClassName()==null?"":event.getClassName().toString();
        if(pkg.isEmpty())return;
        long elapsed=SystemClock.elapsedRealtime();
        if(elapsed-keyboardChecked>=2000){
            keyboardChecked=elapsed;
            String keyboard=android.provider.Settings.Secure.getString(getContentResolver(),android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
            android.content.ComponentName ime=keyboard==null?null:android.content.ComponentName.unflattenFromString(keyboard);
            keyboardPackage=ime==null?"":ime.getPackageName();
        }
        if(pkg.equals(keyboardPackage))return; // Ignore keyboard metadata; never inspect its content.
        if(pkg.equals(getPackageName()) && !name.endsWith("Activity"))return; // Our overlay is not a foreground-app change.
        foregroundPackage=pkg; foregroundClass=name;
        if(interaction!=null&&(exempt(pkg)||!PageMatcher.WECHAT.equals(pkg)
                ||PageMatcher.isWechatPage(name)&&!PageMatcher.isMoments(name)))interaction.confirmNavigation();
        if(PageMatcher.WECHAT.equals(pkg)) {
            watching=true;
            if(PageMatcher.isWechatPage(name)) {
                store.recordPage(name); page=name; pageWindow=event.getWindowId(); fallbackAttempts=0;
                if(PageMatcher.isPersonal(page))personalOrigin=true;
                else if(!PageMatcher.isMoments(page))personalOrigin=false;
            }
        } else { watching=false; page=""; pageWindow=-1; personalOrigin=false; }
        enforce();
    }

    private void requestRules(){
        if(!active||ruleQueued||SystemClock.elapsedRealtime()<retryRulesAfter)return;
        ruleQueued=true;
        ruleWorker.execute(()->{
            try{
                LifeStore records=LifeStore.get(this);lifeStore=records;LifeData snapshot=records.data();
                long wall=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();ZoneId zone=ZoneId.systemDefault();
                LifeEngine.Due due=LifeEngine.due(snapshot,wall,zone);
                long next=Math.min(wall+1500,LifeEngine.nextBoundary(snapshot,wall,zone));
                handler.post(()->{
                    ruleQueued=false;if(!active)return;
                    if(records.data()!=snapshot){requestRules();return;}
                    evaluatedData=snapshot;evaluatedDue=due;evaluatedZone=zone;
                    nextRuleCheck=next;ruleWall=wall;ruleElapsed=elapsed;enforce();
                });
            }catch(RuntimeException e){handler.post(()->{
                ruleQueued=false;if(!active)return;
                retryRulesAfter=SystemClock.elapsedRealtime()+5000;evaluatedData=null;hideLifeOverlay();
                android.util.Log.w("MomentsGuard","Reminder evaluation unavailable");
            });}
        });
    }
    private void pauseFromReminder(){
        long tappedAt=System.currentTimeMillis();
        interaction.emergency(()->{LifeStore.get(this).emergency(tappedAt);LifeScheduler.refresh(this);});
    }
    private void openFromReminder(Intent intent){
        interaction.open(()->startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP)));
    }
    private void enforce() {
        if(!active)return;
        interaction.checkLaunchTimeout();
        if(interaction.transitioning()){hideOverlay();hideLifeOverlay();return;}
        PowerManager power = (PowerManager)getSystemService(POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if (power == null || keyguard == null || !power.isInteractive() || keyguard.isKeyguardLocked()) { hideOverlay(); hideLifeOverlay(); return; }
        Foreground foreground = foreground();
        if (foreground == null) { hideOverlay(); hideLifeOverlay(); return; }
        if (exempt(foreground.pkg) || sensitiveOrCall()) { hideOverlay(); hideLifeOverlay(); return; }
        LifeStore records=lifeStore;
        if(records==null){requestRules();return;}
        LifeData life=records.data();long wall=System.currentTimeMillis();
        if(evaluatedData!=life||wall>=nextRuleCheck||!ZoneId.systemDefault().equals(evaluatedZone)
                ||Math.abs((wall-ruleWall)-(SystemClock.elapsedRealtime()-ruleElapsed))>2000)requestRules();
        LifeEngine.Due due=evaluatedData==life?evaluatedDue:LifeEngine.NONE;
        boolean required=due.kind!=LifeEngine.Kind.NONE&&life.forceMode>0&&life.graceUntil<=wall&&!interaction.lifePaused();
        if (!watching && !required) { hideOverlay();hideLifeOverlay(); return; }
        if (required && !exempt(foreground.pkg) && (life.forceMode == 2 || PageMatcher.WECHAT.equals(foreground.pkg))) {
            hideOverlay(); showLifeOverlay(due); return;
        }
        hideLifeOverlay();
        // Touching our non-focusable reminder must not cause a hide/show flicker.
        if (!PageMatcher.WECHAT.equals(foreground.pkg)) {
            watching = false; page = ""; pageWindow = -1; personalOrigin = false;
            hideOverlay(); return;
        }
        if (pageWindow >= 0 && pageWindow != foreground.window) {
            page = ""; personalOrigin = false; hideOverlay(); return;
        }
        if (!blocked(foreground)) { hideOverlay(); return; }
        if (showOverlay()) return;

        // Automatic navigation is only a fallback when the OS refuses our cover.
        // A displayed reminder stays until the user chooses to leave.
        long now = SystemClock.elapsedRealtime();
        if (fallbackAttempts < 3 && now - lastFallbackBack > 1200) {
            lastFallbackBack = now; fallbackAttempts++;
            if (performGlobalAction(GLOBAL_ACTION_BACK)) {
                store.recordBlock();
                Toast.makeText(this, "提醒页暂时无法显示，已返回上一页", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean exempt(String pkg) {
        return pkg.equals(getPackageName()) || pkg.equals(dialer) || pkg.equals(clockPackage) || clockPackages.contains(pkg) || SystemClockAccess.knownPackage(pkg) || pkg.equals("com.android.phone")
                || pkg.equals("com.android.incallui") || pkg.equals("com.huawei.incallui")
                || pkg.equals("com.android.deskclock") || pkg.equals("com.huawei.deskclock")
                || pkg.equals("com.google.android.deskclock") || pkg.equals("com.android.alarmclock")
                || pkg.equals("com.eg.android.AlipayGphone") || pkg.equals("com.huawei.wallet")
                || pkg.equals("com.android.emergency") || pkg.equals("com.android.settings")
                || pkg.equals("com.android.systemui") || pkg.equals("com.huawei.systemmanager")
                || pkg.equals("com.android.permissioncontroller") || pkg.equals("com.google.android.permissioncontroller")
                || pkg.equals("com.android.packageinstaller") || pkg.equals("com.google.android.packageinstaller")
                || pkg.equals("com.android.documentsui") || pkg.equals("com.google.android.documentsui")
                || pkg.equals("com.huawei.hidisk") || pkg.equals("com.android.providers.media.module");
    }

    private static boolean routineDue(LifeEngine.Kind kind){return kind==LifeEngine.Kind.WEEK_PLAN||kind==LifeEngine.Kind.DAY_PLAN
            ||kind==LifeEngine.Kind.WEEK_REVIEW||kind==LifeEngine.Kind.LEARN;}
    private void showLifeOverlay(LifeEngine.Due due) {
        if (lifeOverlay != null && lifeOverlay.key.equals(due.key)) return;
        hideLifeOverlay();
        LifePromptView view = new LifePromptView(this, due,
                () -> openFromReminder(new Intent(this, routineDue(due.kind)?RoutineActivity.class:LifeActivity.class).putExtra("section", "due")),
                this::pauseFromReminder,
                () -> openFromReminder(new Intent(Intent.ACTION_DIAL)),
                () -> interaction.open(()->{if(!SystemClockAccess.open(this))throw new IllegalStateException("Clock unavailable");}));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.OPAQUE);
        params.gravity = Gravity.TOP | Gravity.START; params.setTitle("作息与计划 · 待跟进");
        try { ((WindowManager)getSystemService(WINDOW_SERVICE)).addView(view, params); lifeOverlay = view; }
        catch (RuntimeException ignored) { /* Existing due notification remains available. */ }
    }

    private void hideLifeOverlay() {
        if (lifeOverlay == null) return;
        try { lifeOverlay.setVisibility(android.view.View.GONE);((WindowManager)getSystemService(WINDOW_SERVICE)).removeViewImmediate(lifeOverlay); }
        catch (RuntimeException ignored) { }
        lifeOverlay = null;
    }

    private boolean blocked(Foreground foreground) {
        return foreground != null && store.consented() && store.openRemaining() == 0
                && PageMatcher.shouldBlock(foreground.pkg, page, store.allMoments(), personalOrigin);
    }

    private void returnToPrevious() {
        Foreground foreground = foreground();
        if (foreground == null || !PageMatcher.WECHAT.equals(foreground.pkg)
                || (pageWindow >= 0 && pageWindow != foreground.window)) {
            hideOverlay(); return;
        }
        interaction.open(()->{if(!performGlobalAction(GLOBAL_ACTION_BACK))throw new IllegalStateException("Back unavailable");});
    }

    private boolean showOverlay() {
        if (overlay != null) return true;
        FocusReminderView view = new FocusReminderView(this, store, false,
                this::returnToPrevious,
                () -> {
                    openFromReminder(new Intent(this, MainActivity.class).putExtra("section","moments"));
                });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.OPAQUE);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("自律空间 · 专注提醒");
        try {
            ((WindowManager)getSystemService(WINDOW_SERVICE)).addView(view, params);
            overlay = view;
            view.rememberShownQuote(); store.recordBlock();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean sensitiveOrCall() {
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        if(audio!=null) {
            int mode=audio.getMode();
            if(mode==AudioManager.MODE_RINGTONE || mode==AudioManager.MODE_IN_CALL || mode==AudioManager.MODE_IN_COMMUNICATION)return true;
        }
        String name=foregroundClass.toLowerCase(java.util.Locale.ROOT);
        return name.contains(".voip.") || name.contains(".multitalk.") || name.contains("voip")
                || name.contains("wallet") || name.contains("wcpay") || name.contains("payment")
                || name.contains("password") || name.contains("credential") || name.contains("biometric");
    }

    private Foreground foreground() {
        return foregroundPackage.isEmpty()?null:new Foreground(foregroundPackage,pageWindow);
    }

    private static final class Foreground {
        final String pkg;
        final int window;
        Foreground(String pkg, int window) { this.pkg = pkg; this.window = window; }
    }

    private void hideOverlay() {
        if (overlay == null) return;
        try { overlay.setVisibility(android.view.View.GONE);((WindowManager)getSystemService(WINDOW_SERVICE)).removeViewImmediate(overlay); }
        catch (RuntimeException ignored) { }
        overlay = null;
    }

    @Override public void onInterrupt() { hideOverlay(); hideLifeOverlay(); }
    @Override public void onDestroy() {
        connected = false;active=false;if(interaction!=null)interaction.close();
        handler.removeCallbacksAndMessages(null);hideOverlay();hideLifeOverlay();ruleWorker.shutdown();diskWorker.shutdown();super.onDestroy();
    }
}
