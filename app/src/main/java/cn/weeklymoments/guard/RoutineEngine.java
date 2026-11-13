package cn.weeklymoments.guard;

import java.time.*;
import java.time.temporal.*;
import java.util.*;

public final class RoutineEngine {
    public static String week(LocalDate date){return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).toString();}
    public static boolean enabled(RoutineData r){return r.weeklyEnabled||r.dailyEnabled||r.knowledgeEnabled;}
    public static boolean learningDay(RoutineData r,LocalDate date){
        for(RoutineData.LearningPeriod p:r.learningPeriods)if(!date.isBefore(LocalDate.parse(p.start))&&(p.end.isEmpty()||date.isBefore(LocalDate.parse(p.end))))return true;
        return false;
    }
    public static LocalDate debt(RoutineData r,LocalDate today){
        for(RoutineData.LearningPeriod p:r.learningPeriods){LocalDate first=LocalDate.parse(p.start),end=p.end.isEmpty()?today:LocalDate.parse(p.end);if(end.isAfter(today))end=today;
            for(LocalDate date=first;date.isBefore(end);date=date.plusDays(1))for(int slot=0;slot<3;slot++)if(!r.readCards.contains(date+":"+slot))return date;}
        return null;
    }
    public static int cardIndex(RoutineData r,LocalDate date,int slot,int size){
        long ordinal=0;for(RoutineData.LearningPeriod p:r.learningPeriods){LocalDate start=LocalDate.parse(p.start);if(!date.isAfter(start))continue;LocalDate end=p.end.isEmpty()?date:LocalDate.parse(p.end);if(end.isAfter(date))end=date;if(end.isAfter(start))ordinal+=ChronoUnit.DAYS.between(start,end);}
        return (int)Math.floorMod(ordinal*3+slot,(long)size);
    }
    public static int cycle(RoutineData r,LocalDate date,int size){
        long days=0;for(RoutineData.LearningPeriod p:r.learningPeriods){LocalDate a=LocalDate.parse(p.start),b=p.end.isEmpty()?date:LocalDate.parse(p.end);if(b.isAfter(date))b=date;if(b.isAfter(a))days+=ChronoUnit.DAYS.between(a,b);}return 1+(int)(days*3/size);
    }
    public static void learning(RoutineData r,boolean enabled,LocalDate date){
        if(enabled==r.knowledgeEnabled)return;
        if(enabled){if(!r.learningPeriods.isEmpty()){RoutineData.LearningPeriod last=r.learningPeriods.get(r.learningPeriods.size()-1);if(last.end.equals(date.toString())){last.end="";r.knowledgeEnabled=true;return;}}
            RoutineData.LearningPeriod p=new RoutineData.LearningPeriod();p.start=date.toString();r.learningPeriods.add(p);
        }else if(!r.learningPeriods.isEmpty())r.learningPeriods.get(r.learningPeriods.size()-1).end=date.toString();
        r.knowledgeEnabled=enabled;
    }
    public static LifeEngine.Due priority(LifeData d,long now,ZoneId zone){
        RoutineData r=d.routine;LocalDate calendar=LifeEngine.date(now,zone);
        if(r.knowledgeEnabled){LocalDate debt=debt(r,calendar);if(debt!=null)return new LifeEngine.Due(LifeEngine.Kind.LEARN,"learn:"+debt,debt.toString(),"");}
        if(r.weeklyEnabled){
            // Saturday's bedtime can fall on Sunday. Finish that week's review before starting the new week.
            for(RoutineData.Week w:r.weeks){LocalDate start=LocalDate.parse(w.key);if(w.reviewedAt==0&&now>=LifeEngine.bedAt(d,start.plusDays(6),zone)){
                String day=start.plusDays(6).toString();
                if(d.planningEnabled)for(LifeData.Plan plan:d.plans)if(plan.reviewedAt==0&&plan.end<=now)return new LifeEngine.Due(LifeEngine.Kind.REVIEW,"review:"+plan.id,plan.day,plan.id);
                if((d.planningEnabled||r.dailyEnabled)&&!d.acknowledgedDays.contains(day))return new LifeEngine.Due(LifeEngine.Kind.DAY_REVIEW,"summary:"+day,day,"");
                return new LifeEngine.Due(LifeEngine.Kind.WEEK_REVIEW,"week-review:"+w.key,w.key,"");
            }}
            String week=week(calendar);if(r.week(week)==null)return new LifeEngine.Due(LifeEngine.Kind.WEEK_PLAN,"week:"+week,week,"");
        }
        return LifeEngine.NONE;
    }
    public static LifeEngine.Due daily(LifeData d,long now,ZoneId zone){
        if(!d.routine.dailyEnabled)return LifeEngine.NONE;LocalDate day=LifeEngine.day(d,now,zone);String key=day.toString();
        if(now>=LifeEngine.bedAt(d,day,zone))return d.acknowledgedDays.contains(key)?LifeEngine.NONE:new LifeEngine.Due(LifeEngine.Kind.DAY_REVIEW,"summary:"+key,key,"");
        String previous=day.minusDays(1).toString();
        if(d.routine.day(previous)!=null&&!d.acknowledgedDays.contains(previous))return new LifeEngine.Due(LifeEngine.Kind.DAY_REVIEW,"summary:"+previous,previous,"");
        return d.routine.day(key)==null?new LifeEngine.Due(LifeEngine.Kind.DAY_PLAN,"day:"+key,key,""):LifeEngine.NONE;
    }
    public static int percent(java.util.List<RoutineData.Item> items){if(items.isEmpty())return 0;int done=0;for(RoutineData.Item i:items)if(i.done)done++;return Math.round(done*100f/items.size());}
    public static String habitResult(RoutineData.Habit h,String week){
        LocalDate start=LocalDate.parse(week);int total=0,logged=0,violations=0;
        for(int i=0;i<7;i++){LocalDate date=start.plusDays(i);Integer n=h.actual.get(date.toString());if(n==null)continue;logged++;total+=n;
            if((h.allowedDays&(1<<(date.getDayOfWeek().getValue()-1)))==0&&n>0)violations++;
            if(h.type==1&&n>h.goal)violations++;}
        if(h.type==0)return "累计 "+total+" / 目标 "+h.goal+" 次；已记录 "+logged+"/7 天";
        if(h.type==2&&total>h.goal)violations++;
        return "已记录 "+logged+"/7 天；已记录部分"+(violations==0?"符合限制":"有 "+violations+" 处超限")+(h.type==2?"；累计 "+total+"/"+h.goal+" 次":"");
    }
    private RoutineEngine(){}
}
