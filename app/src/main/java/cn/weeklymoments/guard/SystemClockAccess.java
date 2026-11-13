package cn.weeklymoments.guard;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.AlarmClock;
import android.widget.Toast;
import java.util.*;

/** Resolve by capability and known OEM package names, never by translated display labels. */
final class SystemClockAccess {
    private static final String[] KNOWN={"com.huawei.deskclock","com.huawei.android.deskclock",
        "com.android.deskclock","com.google.android.deskclock","com.android.alarmclock","com.hihonor.deskclock"};
    static boolean knownPackage(String name){return Arrays.asList(KNOWN).contains(name);}
    private static List<Intent> intents(){return Arrays.asList(new Intent(AlarmClock.ACTION_SHOW_ALARMS),new Intent(AlarmClock.ACTION_SHOW_TIMERS));}
    static Set<String> packages(Context c){
        Set<String> result=new HashSet<>(Arrays.asList(KNOWN));
        for(Intent intent:intents())for(ResolveInfo info:c.getPackageManager().queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY))
            if(info.activityInfo!=null)result.add(info.activityInfo.packageName);
        return result;
    }
    static boolean open(Context c){
        PackageManager pm=c.getPackageManager();
        // Launch the actual Clock home on Huawei; some versions do not implement SHOW_ALARMS.
        for(String name:KNOWN){Intent launch=pm.getLaunchIntentForPackage(name);if(launch!=null&&start(c,launch))return true;}
        for(Intent intent:intents()){
            for(ResolveInfo info:pm.queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY)){
                if(info.activityInfo!=null&&start(c,new Intent(intent).setClassName(info.activityInfo.packageName,info.activityInfo.name)))return true;
            }
            if(start(c,intent))return true;
        }
        Toast.makeText(c,"暂未找到时钟入口。请使用紧急暂停，再从桌面打开“时钟”。",Toast.LENGTH_LONG).show();return false;
    }
    private static boolean start(Context c,Intent intent){try{c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;}
        catch(android.content.ActivityNotFoundException|SecurityException e){return false;}}
    private SystemClockAccess(){}
}
