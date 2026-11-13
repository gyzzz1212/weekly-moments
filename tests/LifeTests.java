package cn.weeklymoments.guard;

import java.time.*;
import java.util.*;

public final class LifeTests {
    static final ZoneId Z=ZoneId.of("Asia/Shanghai");static int checks;
    static long t(String value){return LocalDateTime.parse(value).atZone(Z).toInstant().toEpochMilli();}
    static void eq(long a,long b,String reason){checks++;if(a!=b)throw new AssertionError(reason+": "+a+" != "+b);}
    static void kind(LifeData d,String at,LifeEngine.Kind expected,String reason){checks++;LifeEngine.Kind got=LifeEngine.due(d,t(at),Z).kind;if(got!=expected)throw new AssertionError(reason+": "+got+" != "+expected);}
    static LifeData base(){LifeData d=new LifeData();d.planningEnabled=true;d.sleepEnabled=true;d.coursesConfirmed=true;d.enabledAt=t("2026-10-26T06:00");LifeData.Window w=new LifeData.Window();w.start=7*60;w.end=23*60;d.windows.add(w);return d;}
    static void awake(LifeData d,String day){LifeData.Sleep s=d.sleep(day,true);s.wakeAt=t(day+"T07:00");}
    static LifeData.Course course(int day,int start,int end){LifeData.Course c=new LifeData.Course();c.id="course";c.name="数学";c.day=day;c.start=start;c.end=end;return c;}
    static LifeData.Plan plan(String day,String a,String b,int minutes,int percent){LifeData.Plan p=new LifeData.Plan();p.id="plan";p.day=day;p.start=t(day+"T"+a);p.end=t(day+"T"+b);LifeData.Task task=new LifeData.Task();task.name="读书";task.minutes=minutes;task.percent=percent;p.tasks.add(task);return p;}
    public static void main(String[] args){
        LifeData d=base();kind(d,"2026-10-26T06:30",LifeEngine.Kind.NONE,"no retroactive sleep reminder before activation bed");
        kind(d,"2026-10-26T07:00",LifeEngine.Kind.WAKE,"wake boundary");awake(d,"2026-10-26");
        kind(d,"2026-10-26T07:01",LifeEngine.Kind.PLAN,"plan after wake confirmation");
        d.courses.add(course(1,8*60,10*60));
        eq(LifeEngine.availableNow(d,t("2026-10-26T07:30"),Z).end,t("2026-10-26T08:00"),"plan capped at next class");
        kind(d,"2026-10-26T08:00",LifeEngine.Kind.NONE,"class start excludes gate");
        kind(d,"2026-10-26T09:59",LifeEngine.Kind.NONE,"class ongoing");kind(d,"2026-10-26T10:00",LifeEngine.Kind.PLAN,"class end permits planning");
        kind(d,"2026-10-26T22:50",LifeEngine.Kind.NONE,"less than fifteen minutes not forced");
        kind(d,"2026-10-26T23:00",LifeEngine.Kind.DAY_REVIEW,"summary at bedtime");d.acknowledgedDays.add("2026-10-26");
        kind(d,"2026-10-26T23:01",LifeEngine.Kind.BED,"bed confirmation after summary");d.sleep("2026-10-26",true).bedAt=t("2026-10-26T23:01");
        kind(d,"2026-10-27T02:00",LifeEngine.Kind.NONE,"no planning during confirmed sleep");
        kind(d,"2026-10-27T07:00",LifeEngine.Kind.WAKE,"next wake starts new day");
        LifeData p=base();awake(p,"2026-10-26");LifeData.Plan block=plan("2026-10-26","10:00","10:30",30,-1);p.plans.add(block);
        kind(p,"2026-10-26T10:29",LifeEngine.Kind.NONE,"no overlapping new plan");kind(p,"2026-10-26T10:30",LifeEngine.Kind.REVIEW,"review exact expiry");
        p.courses.add(course(1,10*60+30,11*60));kind(p,"2026-10-26T10:30",LifeEngine.Kind.NONE,"defer feedback during class");
        kind(p,"2026-10-26T11:00",LifeEngine.Kind.REVIEW,"resume feedback after class");block.reviewedAt=t("2026-10-26T11:00");block.tasks.get(0).percent=50;
        kind(p,"2026-10-26T11:01",LifeEngine.Kind.PLAN,"replan after review");
        LifeStats.Day stats=LifeStats.day(p,LocalDate.parse("2026-10-26"),Z);eq(stats.percent(),50,"weighted completion");eq(stats.availableMinutes,930,"classes subtracted from total free minutes");
        eq(stats.coveredMinutes,30,"planned interval coverage");
        LifeData.Task extra=new LifeData.Task();extra.name="复习";extra.minutes=10;extra.percent=100;block.tasks.add(extra);eq(LifeStats.day(p,LocalDate.parse("2026-10-26"),Z).percent(),63,"unequal task weighting rounded");
        block.reviewedAt=0;extra.percent=-1;block.tasks.get(0).percent=-1;stats=LifeStats.day(p,LocalDate.parse("2026-10-26"),Z);eq(stats.percent(),0,"missing feedback conservatively zero");eq(stats.pending,1,"missing feedback separate count");
        LifeData empty=base();eq(LifeStats.day(empty,LocalDate.parse("2026-10-26"),Z).percent(),-1,"no denominator not 100 percent");
        LifeData c=base();c.termStart="2026-10-19";LifeData.Course odd=course(1,480,570);odd.parity=1;c.courses.add(odd);
        eq(LifeEngine.courseOn(c,odd,LocalDate.parse("2026-10-19"))?1:0,1,"first odd week");eq(LifeEngine.courseOn(c,odd,LocalDate.parse("2026-10-26"))?1:0,0,"second even week");
        eq(LifeEngine.courseOn(c,odd,LocalDate.parse("2026-10-12"))?1:0,0,"before term");odd.parity=0;odd.skipDates.add("2026-10-26");eq(LifeEngine.courseOn(c,odd,LocalDate.parse("2026-10-26"))?1:0,0,"temporary cancellation");
        odd.date="2026-10-27";eq(LifeEngine.courseOn(c,odd,LocalDate.parse("2026-10-27"))?1:0,1,"one-off course disregards weekday");
        LifeData n=base();n.bedMinute=60;awake(n,"2026-10-26");n.windows.get(0).end=2*60;
        eq(LifeEngine.day(n,t("2026-10-27T00:30"),Z).toEpochDay(),LocalDate.parse("2026-10-26").toEpochDay(),"midnight business day");
        eq(LifeEngine.availableNow(n,t("2026-10-27T00:30"),Z).end,t("2026-10-27T01:00"),"post-midnight bedtime cap");kind(n,"2026-10-27T01:00",LifeEngine.Kind.DAY_REVIEW,"post-midnight report");
        n.coursesConfirmed=false;kind(n,"2026-10-26T14:00",LifeEngine.Kind.NONE,"unconfirmed timetable never used for new plans");
        LifeData disabled=base();disabled.sleepEnabled=false;disabled.planningEnabled=false;kind(disabled,"2026-10-26T14:00",LifeEngine.Kind.NONE,"features opt-in");
        List<LifeEngine.Span> merged=LifeEngine.merge(Arrays.asList(new LifeEngine.Span(10,20),new LifeEngine.Span(15,30),new LifeEngine.Span(30,40)));eq(merged.size(),1,"merge overlapping windows");eq(merged.get(0).end,40,"merge exact adjacent windows");
        eq(LifeEngine.nextBoundary(p,t("2026-10-26T10:29"),Z),t("2026-10-26T10:30"),"alarm at pending expiry");
        LifeData dst=base();dst.sleepEnabled=false;dst.wakeMinute=60;dst.bedMinute=240;dst.windows.get(0).start=60;dst.windows.get(0).end=240;
        long duration=0;for(LifeEngine.Span s:LifeEngine.free(dst,LocalDate.parse("2026-03-29"),ZoneId.of("Europe/Berlin")))duration+=s.end-s.start;eq(duration/60_000,120,"DST forward uses real elapsed duration");
        List<TimetableParser.Period> periods=TimetableParser.periods("1 08:00-08:45\n2 08:55-09:40");eq(periods.size(),2,"school period input");eq(periods.get(1).start,535,"period minutes");
        List<LifeData.Course> read=TimetableParser.proposals("周一 08:00-09:40 高等数学\n周三 10:00-11:30 英语",Collections.emptyList(),Collections.emptyList());eq(read.size(),2,"structured OCR proposals");eq(read.get(1).day,3,"Chinese weekday");
        eq(TimetableParser.proposals("一些没有星期与时间的文字",Collections.emptyList(),Collections.emptyList()).size(),0,"ambiguous OCR does not invent classes");
        eq(TimetableParser.periods("1 08:75-09:40").size(),0,"invalid OCR minutes rejected");
        eq(TimetableParser.proposals("周一 周二 周三\n08:00-09:40 1-2节",Collections.emptyList(),Collections.emptyList()).size(),0,"column headers cannot assign every row to first weekday");
        List<TimetableParser.Word> grid=new ArrayList<>();String[] days={"周一","周二","周三","周四","周五"};for(int i=0;i<5;i++)grid.add(new TimetableParser.Word(days[i],200+i*160,20,260+i*160,40));grid.add(new TimetableParser.Word("1",20,150,35,170));grid.add(new TimetableParser.Word("高等数学",200,150,280,170));
        read=TimetableParser.proposals("",grid,periods);eq(read.size(),1,"spatial grid OCR proposal");eq(read.get(0).start,480,"spatial grid mapped to confirmed period times");
        System.out.println("PASS: "+checks+" life-cycle, timetable, weighting, cross-midnight and DST assertions");
    }
}
