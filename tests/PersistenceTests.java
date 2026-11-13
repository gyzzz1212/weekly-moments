package cn.weeklymoments.guard;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;

public final class PersistenceTests {
    private static int checked;
    private static void same(Object a,Object b,String path)throws Exception{
        checked++;
        if(a==null||b==null){if(a!=b)throw new AssertionError(path);return;}
        if(a instanceof List){List<?> x=(List<?>)a,y=(List<?>)b;if(x.size()!=y.size())throw new AssertionError(path);for(int i=0;i<x.size();i++)same(x.get(i),y.get(i),path+"["+i+"]");}
        else if(a instanceof Map||a instanceof Set||a instanceof String||a instanceof Number||a instanceof Boolean){if(!a.equals(b))throw new AssertionError(path+": "+a+" != "+b);}
        else for(Field field:a.getClass().getFields())if(!java.lang.reflect.Modifier.isStatic(field.getModifiers()))same(field.get(a),field.get(b),path+"."+field.getName());
    }
    public static void main(String[]args)throws Exception{
        LifeData d=new LifeData();d.sleepEnabled=true;d.planningEnabled=true;d.coursesConfirmed=true;d.wakeMinute=390;d.bedMinute=30;d.forceMode=2;d.defaultMinutes=90;d.enabledAt=1800000000000L;d.graceUntil=d.enabledAt+900000;
        d.termStart="2026-09-07";d.imageUri="content://课表/图片1";d.periodText="1 08:00-08:45";
        LifeData.Window w=new LifeData.Window();w.days=31;w.start=480;w.end=30;d.windows.add(w);
        LifeData.Course c=new LifeData.Course();c.id="course-1";c.name="概率论与数理统计";c.day=2;c.start=480;c.end=570;c.parity=2;c.firstWeek=2;c.lastWeek=16;c.skipDates.add("2026-10-27");d.courses.add(c);
        LifeData.Plan p=new LifeData.Plan();p.id="plan-1";p.day="2026-10-26";p.start=d.enabledAt;p.end=p.start+1800000;p.reviewedAt=p.end+9000;p.adjusted=true;p.reflection="做完2题，下一段留出休息时间🌱";
        LifeData.Task t=new LifeData.Task();t.name="数学第1至3题";t.minutes=20;t.percent=75;p.tasks.add(t);d.plans.add(p);
        LifeData.Sleep s=d.sleep("2026-10-26",true);s.wakeAt=d.enabledAt;s.bedAt=d.enabledAt+1000;s.wakeTarget=d.enabledAt-1000;s.bedTarget=d.enabledAt+2000;s.corrected=true;
        d.acknowledgedDays.add(p.day);d.emergencyExits.add(d.enabledAt+200);d.closedAvailableMinutes.put(p.day,480L);d.closedCoveredMinutes.put(p.day,90L);
        d.routine.weeklyEnabled=true;d.routine.dailyEnabled=true;d.routine.knowledgeEnabled=true;d.routine.soundEnabled=false;
        RoutineData.Week week=new RoutineData.Week();week.key="2026-10-25";week.createdAt=d.enabledAt;week.reviewedAt=d.enabledAt+700;week.reflection="每天少排一件事";
        RoutineData.Item item=new RoutineData.Item();item.name="完成AI小实验";item.done=true;week.items.add(item);
        RoutineData.Habit habit=new RoutineData.Habit();habit.name="游戏";habit.rule="每天不超过40分钟";habit.type=1;habit.goal=40;habit.allowedDays=16;habit.actual.put("2026-10-26",35);week.habits.add(habit);d.routine.weeks.add(week);
        RoutineData.Day day=new RoutineData.Day();day.key="2026-10-26";day.createdAt=d.enabledAt;day.items.add(item);d.routine.days.add(day);
        RoutineData.LearningPeriod period=new RoutineData.LearningPeriod();period.start="2026-10-25";period.end="2026-10-27";d.routine.learningPeriods.add(period);d.routine.readCards.add("2026-10-25:0");
        String json=LifeStore.encode(d).toString();LifeData restored=LifeStore.decode(new JSONObject(new String(json.getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8)));
        same(d,restored,"full record graph");same(new LifeData(),LifeStore.decode(LifeStore.encode(new LifeData())),"empty record graph");
        restored.plans.get(0).tasks.get(0).name="changed";if(d.plans.get(0).tasks.get(0).name.equals("changed"))throw new AssertionError("copy alias");
        boolean rejected=false;try{LifeStore.decode(new JSONObject("{\"schema\":99}"));}catch(IllegalArgumentException expected){rejected=true;}if(!rejected)throw new AssertionError("schema not rejected");
        same(d,BackupCodec.decode(BackupCodec.encode(d,1800000000000L).toString()),"portable backup");
        same(d,BackupCodec.decode(json),"legacy export");
        String[] invalidBackups={"{", "{\"schema\":1}",
            BackupCodec.encode(d,1).put("version",99).toString(),
            BackupCodec.encode(d,1).put("format","other-app").toString(),
            LifeStore.encode(d).put("windows",new JSONObject()).toString(),
            LifeStore.encode(d).put("force",99).toString()};
        for(String invalid:invalidBackups){boolean failed=false;try{BackupCodec.decode(invalid);}catch(Exception expected){failed=true;}if(!failed)throw new AssertionError("invalid backup accepted");}
        JSONObject noRoutine=LifeStore.encode(new LifeData());noRoutine.remove("routine");
        same(new LifeData(),BackupCodec.decode(noRoutine.toString()),"pre-routine upgrade");
        System.out.println("PASS: portable/legacy backups and incomplete, foreign, future or invalid backup rejection");
        System.out.println("PASS: "+checked+" field/collection persistence comparisons, UTF-8 and independent-copy/schema checks");
    }
}
