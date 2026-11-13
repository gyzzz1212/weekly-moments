package cn.weeklymoments.guard;

import org.json.*;
import java.time.LocalDate;

/** Versioned portable backups; rejects incomplete/foreign data before any write. */
final class BackupCodec {
    static final int MAX_BYTES=16*1024*1024;
    static JSONObject encode(LifeData data,long now)throws Exception {
        return new JSONObject().put("format","focus-space-backup").put("version",1)
                .put("createdAt",now).put("payload",LifeStore.encode(data));
    }
    static LifeData decode(String json)throws Exception {
        JSONObject root=new JSONObject(json);
        if(root.has("format")){
            if(!"focus-space-backup".equals(root.getString("format"))||root.getInt("version")!=1)
                throw new IllegalArgumentException("此备份版本暂不支持，请使用对应或更新版本的应用");
            root=root.getJSONObject("payload");
        }
        // Older exported life-v1 files are also supported, but never infer missing collections as empty.
        if(root.getInt("schema")!=1)throw new IllegalArgumentException("不支持的数据版本");
        for(String key:new String[]{"windows","courses","plans","sleeps","acknowledged","exits","closed"})root.getJSONArray(key);
        for(String key:new String[]{"sleepEnabled","planningEnabled","coursesConfirmed"})root.getBoolean(key);
        root.getInt("wake");root.getInt("bed");root.getInt("force");root.getInt("length");
        if(root.has("routine")){
            JSONObject r=root.getJSONObject("routine");
            for(String key:new String[]{"weeks","days","periods","read"})r.getJSONArray(key);
        }
        LifeData data=LifeStore.decode(root);validate(data);return data;
    }
    static void validate(LifeData d){
        if(d.forceMode<0||d.forceMode>2)throw new IllegalArgumentException("提醒方式无效");
        if(d.defaultMinutes!=30&&d.defaultMinutes!=60&&d.defaultMinutes!=90&&d.defaultMinutes!=120)throw new IllegalArgumentException("计划时长无效");
        for(LifeData.Window w:d.windows)if(w.days<0||w.days>127)throw new IllegalArgumentException("循环星期无效");
        java.util.Set<String> ids=new java.util.HashSet<>();
        for(LifeData.Plan p:d.plans){
            if(p.id.isEmpty()||!ids.add(p.id)||p.start<0||p.end<p.start)throw new IllegalArgumentException("计划记录无效");
            for(LifeData.Task t:p.tasks)if(t.minutes<1||t.percent< -1||t.percent>100)throw new IllegalArgumentException("完成记录无效");
        }
        for(LifeData.Course c:d.courses)if(c.day<1||c.day>7||c.firstWeek<1||c.lastWeek<c.firstWeek||c.parity<0||c.parity>2)throw new IllegalArgumentException("课程记录无效");
        for(String day:d.acknowledgedDays)LocalDate.parse(day);
        for(String day:d.closedAvailableMinutes.keySet())LocalDate.parse(day);
        for(String key:d.routine.readCards){int colon=key.lastIndexOf(':');if(colon<0)throw new IllegalArgumentException("阅读记录无效");LocalDate.parse(key.substring(0,colon));int slot=Integer.parseInt(key.substring(colon+1));if(slot<0||slot>2)throw new IllegalArgumentException("阅读记录无效");}
    }
    static String summary(LifeData d){return "周计划 "+d.routine.weeks.size()+" 份 · 日计划 "+d.routine.days.size()+" 份\n短时计划 "+d.plans.size()+" 段 · 作息 "+d.sleeps.size()+" 天\n课程 "+d.courses.size()+" 门 · 已读知识卡 "+d.routine.readCards.size()+" 张";}
    private BackupCodec(){}
}
