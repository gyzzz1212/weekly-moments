package cn.weeklymoments.guard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class LifeAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) { PendingResult pending=goAsync();LifeScheduler.refresh(context,pending::finish); }
}
