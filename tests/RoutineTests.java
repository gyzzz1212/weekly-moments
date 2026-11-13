package cn.weeklymoments.guard;

import java.time.*;

public final class RoutineTests {
    static int n;static final ZoneId Z=ZoneId.of("Asia/Hong_Kong");
    static void eq(Object a,Object b,String message){n++;if(!a.equals(b))throw new AssertionError(message+": "+a+" != "+b);}
    static long time(String s){return LocalDateTime.parse(s).atZone(Z).toInstant().toEpochMilli();}
    static LifeData base(){LifeData d=new LifeData();d.enabledAt=time("2026-10-18T08:00");return d;}
    static void due(LifeData d,String time,LifeEngine.Kind expected,String message){eq(LifeEngine.due(d,time(time),Z).kind,expected,message);}
    public static void main(String[] args){
        long wall=1800000000000L,elapsed=5000000;int boot=5;AccessPolicy cycle=new AccessPolicy(wall,elapsed,boot,false);
        SessionPolicy running=new SessionPolicy(cycle,0,elapsed,boot,0,false);
        eq(running.remaining(wall+120000,elapsed+120000,boot),780000L,"two active minutes deducted");
        eq(running.canPause(wall,elapsed,boot),true,"first pause allowed");
        SessionPolicy paused=new SessionPolicy(cycle,120000,elapsed+120000,boot,1,true);
        eq(paused.remaining(wall+3600000,elapsed+3600000,boot),780000L,"paused time never charged");
        eq(paused.remaining(wall+3600000,100,boot+1),780000L,"paused balance survives reboot");
        eq(paused.canPause(wall,elapsed,boot),false,"cannot pause twice without resume");
        SessionPolicy resumed=new SessionPolicy(cycle,120000,elapsed+3600000,boot,1,false);
        eq(resumed.remaining(wall+3660000,elapsed+3660000,boot),720000L,"resume spends only active time");
        SessionPolicy third=new SessionPolicy(cycle,180000,elapsed+3660000,boot,3,false);
        eq(third.canPause(wall+3660000,elapsed+3660000,boot),false,"fourth pause forbidden");
        eq(third.remaining(wall+3660000,elapsed+3660000,boot),720000L,"third resume still usable");
        eq(third.remaining(wall+100,10,boot+1),0L,"running reboot conservatively ends balance");
        eq(paused.remaining(wall+AccessPolicy.COOLDOWN_MS,elapsed+AccessPolicy.COOLDOWN_MS,boot),0L,"paused quota expires at next weekly allowance");
        eq(resumed.remaining(wall+4380000,elapsed+4380000,boot),0L,"exact cumulative fifteen minutes");
        eq(RoutineEngine.week(LocalDate.parse("2026-10-24")),"2026-10-18","Saturday belongs to prior Sunday");
        eq(RoutineEngine.week(LocalDate.parse("2026-10-25")),"2026-10-25","Sunday week boundary");
        LifeData d=base();d.routine.weeklyEnabled=true;
        due(d,"2026-10-25T00:00",LifeEngine.Kind.WEEK_PLAN,"weekly gate begins exactly at Sunday midnight");
        RoutineData.Week w=new RoutineData.Week();w.key="2026-10-18";w.createdAt=d.enabledAt;d.routine.weeks.add(w);
        due(d,"2026-10-24T22:59",LifeEngine.Kind.NONE,"no premature Saturday review");
        due(d,"2026-10-24T23:00",LifeEngine.Kind.WEEK_REVIEW,"Saturday bedtime week review");
        d.routine.dailyEnabled=true;
        due(d,"2026-10-24T23:00",LifeEngine.Kind.DAY_REVIEW,"day review precedes week review");d.acknowledgedDays.add("2026-10-24");
        due(d,"2026-10-25T00:00",LifeEngine.Kind.WEEK_REVIEW,"old week review retained after rollover");w.reviewedAt=time("2026-10-24T23:05");
        due(d,"2026-10-25T00:00",LifeEngine.Kind.WEEK_PLAN,"new week after completing old review");
        d=base();d.routine.weeklyEnabled=true;d.bedMinute=60;w=new RoutineData.Week();w.key="2026-10-18";w.createdAt=d.enabledAt;d.routine.weeks.add(w);
        due(d,"2026-10-25T00:30",LifeEngine.Kind.WEEK_PLAN,"Sunday first use may plan next week before Saturday's late bedtime");
        RoutineData.Week next=new RoutineData.Week();next.key="2026-10-25";next.createdAt=time("2026-10-25T00:30");d.routine.weeks.add(next);
        due(d,"2026-10-25T00:59",LifeEngine.Kind.NONE,"late Saturday review not premature");
        due(d,"2026-10-25T01:00",LifeEngine.Kind.WEEK_REVIEW,"cross-midnight bedtime still reviews Saturday week");
        d=base();d.routine.dailyEnabled=true;
        due(d,"2026-10-26T07:00",LifeEngine.Kind.DAY_PLAN,"daily plan at wake target");RoutineData.Day day=new RoutineData.Day();day.key="2026-10-26";d.routine.days.add(day);
        due(d,"2026-10-26T07:01",LifeEngine.Kind.NONE,"saved daily plan releases gate");
        due(d,"2026-10-26T23:00",LifeEngine.Kind.DAY_REVIEW,"daily-only still gets evening review");
        due(d,"2026-10-27T07:00",LifeEngine.Kind.DAY_REVIEW,"daily-only missed report carried to next morning");
        RoutineData r=new RoutineData();LocalDate start=LocalDate.parse("2026-10-25");RoutineEngine.learning(r,true,start);
        eq(RoutineEngine.debt(r,start)==null,true,"enrollment never creates prior-day debt");eq(RoutineEngine.debt(r,start.plusDays(1)),start,"unfinished first day blocks next day");
        for(int i=0;i<3;i++)r.readCards.add(start+":"+i);
        eq(RoutineEngine.debt(r,start.plusDays(1))==null,true,"all three confirmations clear debt");
        eq(RoutineEngine.cardIndex(r,start,0,42),0,"first card");eq(RoutineEngine.cardIndex(r,start.plusDays(1),0,42),3,"three sequential cards daily");eq(RoutineEngine.cardIndex(r,start.plusDays(14),0,42),0,"fourteen-day curriculum then review");eq(RoutineEngine.cycle(r,start.plusDays(14),42),2,"review visibly marked second cycle");
        RoutineEngine.learning(r,false,start.plusDays(1));eq(RoutineEngine.learningDay(r,start.plusDays(1)),false,"disabled day receives no new cards");RoutineEngine.learning(r,true,start.plusDays(4));eq(RoutineEngine.cardIndex(r,start.plusDays(4),0,42),3,"disabled gap does not skip curriculum");eq(RoutineEngine.debt(r,start.plusDays(4))==null,true,"disabled gap has no debt");eq(RoutineEngine.debt(r,start.plusDays(5)),start.plusDays(4),"resumed learning creates new debt correctly");
        d=base();d.routine=r;due(d,"2026-10-30T08:00",LifeEngine.Kind.LEARN,"learning can gate independently of sleep and hourly planning");
        RoutineData.Item a=new RoutineData.Item(),b=new RoutineData.Item();a.done=true;java.util.List<RoutineData.Item> is=java.util.Arrays.asList(a,b);eq(RoutineEngine.percent(is),50,"done-list completion proportion");
        RoutineData.Habit h=new RoutineData.Habit();h.type=1;h.goal=40;h.actual.put("2026-10-25",0);h.actual.put("2026-10-26",41);eq(RoutineEngine.habitResult(h,"2026-10-25").contains("有 1 处超限"),true,"manual minutes over target reported");eq(RoutineEngine.habitResult(h,"2026-10-25").contains("2/7"),true,"missing records are not presumed successful");
        System.out.println("PASS: "+n+" pause/resume, Sunday rollover, Saturday/cross-midnight review, daily and learning-debt assertions");
    }
}
