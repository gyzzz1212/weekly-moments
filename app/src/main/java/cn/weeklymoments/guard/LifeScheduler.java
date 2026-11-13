package cn.weeklymoments.guard;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import java.time.ZoneId;

public final class LifeScheduler {
    private static final String CHANNEL = "life-reminders";
    private static final int ID = 7102;
    private static long lastTick;
    private static String lastNotified = "";
    private static long scheduledAt;
    private static boolean scheduledExact;
    private static final java.util.concurrent.ExecutorService WORK=java.util.concurrent.Executors.newSingleThreadExecutor();
    private static CoalescingTask updates;
    private static synchronized CoalescingTask updates(Context c){
        if(updates==null){Context app=c.getApplicationContext();updates=new CoalescingTask(WORK,()->refreshNow(app),
            error->android.util.Log.w("LifeScheduler","Reminder scheduling unavailable"));}
        return updates;
    }
    public static void tick(Context context) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastTick >= 15_000) { lastTick = now; refresh(context); }
    }
    public static boolean exactAllowed(Context context) {
        return Build.VERSION.SDK_INT < 31 || ((AlarmManager)context.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
    }
    public static void refresh(Context c){updates(c).request();}
    static void refresh(Context c,Runnable completion){updates(c).request(completion);}
    private static void refreshNow(Context c) {
        LifeData data = LifeStore.get(c).data();
        AlarmManager alarms = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        NotificationManager notifications = (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        PendingIntent pending = PendingIntent.getBroadcast(c, ID, new Intent(c, LifeAlarmReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (!data.sleepEnabled && !data.planningEnabled && !RoutineEngine.enabled(data.routine)) {
            alarms.cancel(pending);scheduledAt=0; notifications.cancel(ID); lastNotified = ""; return;
        }
        long now = System.currentTimeMillis();
        LifeEngine.Due due = LifeEngine.due(data, now, ZoneId.systemDefault());
        if (due.kind == LifeEngine.Kind.NONE || data.graceUntil > now) { notifications.cancel(ID); lastNotified = ""; }
        else {
            if ((Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                    &&notifications.areNotificationsEnabled()&&!due.key.equals(lastNotified)) {
                notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "作息与计划跟进", NotificationManager.IMPORTANCE_HIGH));
                Intent open = new Intent(c, LifeActivity.class).putExtra("section", "due");
                PendingIntent content = PendingIntent.getActivity(c, ID, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                if (!due.key.equals(lastNotified)) notifications.cancel(ID);
                Notification notification = new Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(due.title()).setContentText("点此处理待办；电话、时钟和紧急退出始终保留。")
                        .setContentIntent(content).setOnlyAlertOnce(true).setAutoCancel(true)
                        .setVisibility(Notification.VISIBILITY_PRIVATE).build();
                notifications.notify(ID, notification); lastNotified = due.key;
            }
        }
        long at = LifeEngine.nextBoundary(data, now, ZoneId.systemDefault());
        boolean exact=exactAllowed(c);
        if(at==scheduledAt&&exact==scheduledExact)return;
        try {
            if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
        } catch (SecurityException ex) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);exact=false; }
        scheduledAt=at;scheduledExact=exact;
    }
    private LifeScheduler() {}
}
