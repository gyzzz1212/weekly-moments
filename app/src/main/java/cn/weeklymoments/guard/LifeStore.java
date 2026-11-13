package cn.weeklymoments.guard;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LifeStore {
    private static LifeStore instance;
    private final AtomicFile file;
    private final LocalHistory history;
    private String historyWarning="";
    private volatile LifeData data = new LifeData();
    private String error = "";
    public static synchronized LifeStore get(Context context) {
        if (instance == null) instance = new LifeStore(context.getApplicationContext());
        return instance;
    }
    private LifeStore(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), "life-v1.json"));
        history = new LocalHistory(new File(context.getFilesDir(),"life-history"));
        if (file.getBaseFile().exists() || new File(file.getBaseFile() + ".bak").exists()) {
            try (FileInputStream input = file.openRead(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = input.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    if (out.size() > 16 * 1024 * 1024) throw new IllegalStateException("记录文件过大");
                }
                data = decode(new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8)));
                try {history.save(out.toByteArray(),String.valueOf(context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionCode));}
                catch(Exception snapshotError){historyWarning="升级前副本创建失败，请先导出备份。";}
            } catch (Exception ex) { error = "本地记录读取失败，已保留原文件。请先备份后反馈，暂不覆盖保存。"; }
        }
    }
    public LifeData data() { return data; }
    public String error() { return error; }
    public String historyWarning(){return historyWarning;}
    public java.util.List<File> history(){return history.list();}
    public String export() {
        try { return encode(data).toString(2); } catch (Exception ex) { throw new IllegalStateException("导出失败", ex); }
    }
    public void writeBackup(OutputStream output) throws IOException {
        if (error.isEmpty()) {
            try{output.write(BackupCodec.encode(data,System.currentTimeMillis()).toString(2).getBytes(StandardCharsets.UTF_8));}
            catch(Exception e){throw new IOException("备份失败",e);}
        }
        else try (FileInputStream input = file.openRead()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
        }
    }
    public synchronized LifeData copy() {
        try { return decode(encode(data)); } catch (Exception ex) { throw new IllegalStateException("复制设置失败", ex); }
    }
    public synchronized void replace(LifeData next) {
        if (!error.isEmpty()) throw new IllegalStateException(error);
        persist(next);
    }
    public synchronized void restore(LifeData next){BackupCodec.validate(next);persist(next);}
    private void persist(LifeData next){
        FileOutputStream output=null;
        try{
            byte[] bytes=encode(next).toString().getBytes(StandardCharsets.UTF_8);
            if(bytes.length>BackupCodec.MAX_BYTES)throw new IOException("记录文件过大");
            // Preserve the actual previous file, including unreadable records, before replacement.
            if(file.getBaseFile().exists()||new File(file.getBaseFile()+".bak").exists()){
                try(FileInputStream in=file.openRead()){history.save(readBytes(in),null);}
            }
            output=file.startWrite();output.write(bytes);file.finishWrite(output);
            data=next;error="";historyWarning="";
        }catch(Exception ex){if(output!=null)file.failWrite(output);throw new IllegalStateException("保存失败，旧记录保留。请检查剩余空间。",ex);}
    }
    static byte[] readBytes(java.io.InputStream input)throws IOException{
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(out.size()+n>BackupCodec.MAX_BYTES)throw new IOException("备份超过 16 MB");out.write(buffer,0,n);}return out.toByteArray();
        }
    }

    public void recordSleep(String day, boolean wake, long instant, boolean corrected) {
        long now = System.currentTimeMillis();
        if (instant > now + 60_000 || instant <= 0) throw new IllegalArgumentException("请记录已经发生的时间");
        LocalDate date = LocalDate.parse(day);
        LifeData next = copy(); LifeData.Sleep record = next.sleep(day, true);
        if (record.wakeTarget == 0) record.wakeTarget = LifeEngine.at(date, next.wakeMinute, ZoneId.systemDefault());
        if (record.bedTarget == 0) record.bedTarget = LifeEngine.bedAt(next, date, ZoneId.systemDefault());
        if (wake) record.wakeAt = instant; else record.bedAt = instant;
        record.corrected = record.corrected || corrected;
        replace(next);
    }
    public void clearSleep(String day, boolean wake) {
        LifeData next = copy(); LifeData.Sleep record = next.sleep(day, false);
        if (record == null) return;
        if (wake) record.wakeAt = 0; else record.bedAt = 0;
        record.corrected = true; replace(next);
    }

    public void startPlan(int length, List<LifeData.Task> tasks) {
        if (length != 30 && length != 60 && length != 90 && length != 120) throw new IllegalArgumentException("请选择 30、60、90 或 120 分钟");
        LifeData next = copy(); long now = System.currentTimeMillis(); ZoneId zone = ZoneId.systemDefault();
        if (LifeEngine.due(next, now, zone).kind != LifeEngine.Kind.PLAN) throw new IllegalArgumentException("当前不能开始新计划，请先处理待办或等待可用时段");
        LifeEngine.Span free = LifeEngine.availableNow(next, now, zone);
        if (free == null) throw new IllegalArgumentException("当前不在可计划时段");
        long end = Math.min(now + length * 60_000L, free.end);
        int capacity = (int)((end - now) / 60_000), budget = 0;
        if (tasks.isEmpty() || tasks.size() > 8) throw new IllegalArgumentException("请安排 1 至 8 项任务");
        for (LifeData.Task task : tasks) {
            if (task.name.trim().isEmpty() || task.name.length() > 120 || task.minutes <= 0 || task.minutes > 120)
                throw new IllegalArgumentException("请填写任务名称和有效的预计分钟数");
            budget += task.minutes; task.percent = -1;
        }
        if (budget > capacity) throw new IllegalArgumentException("任务预计共 " + budget + " 分钟，当前最多可安排 " + capacity + " 分钟，请留出余量");
        LifeData.Plan plan = new LifeData.Plan(); plan.id = UUID.randomUUID().toString();
        plan.day = LifeEngine.day(next, now, zone).toString(); plan.start = now; plan.end = end;
        plan.tasks.addAll(tasks); next.plans.add(plan); next.defaultMinutes = length;
        replace(next);
    }

    public void review(String id, List<Integer> percents, String reflection, boolean early) {
        LifeData next = copy(); LifeData.Plan plan = next.plan(id); long now = System.currentTimeMillis();
        if (plan == null || plan.tasks.size() != percents.size()) throw new IllegalArgumentException("计划记录已变动，请重新打开");
        if (now < plan.end && !early) throw new IllegalArgumentException("计划尚未到期，可选择提前结束并反馈");
        for (int i = 0; i < percents.size(); i++) {
            int p = percents.get(i);
            if (p < 0 || p > 100) throw new IllegalArgumentException("完成比例必须在 0 至 100 之间");
            plan.tasks.get(i).percent = p;
        }
        if (early && now < plan.end) { plan.end = Math.max(plan.start, now); plan.adjusted = true; }
        plan.reviewedAt = now; plan.reflection = reflection.trim(); replace(next);
    }

    public void acknowledge(String day) {
        LifeData next = copy();
        LifeStats.Day stats = LifeStats.day(next, LocalDate.parse(day), ZoneId.systemDefault());
        next.closedAvailableMinutes.put(day, stats.availableMinutes); next.closedCoveredMinutes.put(day, stats.coveredMinutes);
        next.acknowledgedDays.add(day); replace(next);
    }
    public void emergency(){emergency(System.currentTimeMillis());}
    public synchronized void emergency(long tappedAt) {
        LifeData next = copy();
        next.graceUntil = Math.max(next.graceUntil,tappedAt + 15 * 60_000L);next.emergencyExits.add(tappedAt);replace(next);
    }

    private static JSONObject object(Object... pairs) throws Exception {
        JSONObject out = new JSONObject();
        for (int i = 0; i < pairs.length; i += 2) out.put((String)pairs[i], pairs[i + 1]);
        return out;
    }
    private static JSONArray array(JSONObject root, String key) { JSONArray value = root.optJSONArray(key); return value == null ? new JSONArray() : value; }
    private static int minute(JSONObject root, String key, int fallback) {
        int value = root.optInt(key, fallback);
        if (value < 0 || value >= 1440) throw new IllegalArgumentException("无效的时间设置");
        return value;
    }
    static JSONObject encode(LifeData d) throws Exception {
        JSONObject root = object("schema", 1, "sleepEnabled", d.sleepEnabled, "planningEnabled", d.planningEnabled,
                "coursesConfirmed", d.coursesConfirmed, "wake", d.wakeMinute, "bed", d.bedMinute,
                "force", d.forceMode, "length", d.defaultMinutes, "enabledAt", d.enabledAt, "grace", d.graceUntil,
                "termStart", d.termStart, "imageUri", d.imageUri, "periodText", d.periodText);
        JSONArray windows = new JSONArray(), courses = new JSONArray(), plans = new JSONArray(), sleeps = new JSONArray();
        for (LifeData.Window w : d.windows) windows.put(object("days", w.days, "start", w.start, "end", w.end));
        for (LifeData.Course c : d.courses) courses.put(object("id", c.id, "name", c.name, "date", c.date, "day", c.day,
                "start", c.start, "end", c.end, "first", c.firstWeek, "last", c.lastWeek, "parity", c.parity, "skip", new JSONArray(c.skipDates)));
        for (LifeData.Plan p : d.plans) {
            JSONArray tasks = new JSONArray();
            for (LifeData.Task t : p.tasks) tasks.put(object("name", t.name, "minutes", t.minutes, "percent", t.percent));
            plans.put(object("id", p.id, "day", p.day, "start", p.start, "end", p.end, "reviewed", p.reviewedAt,
                    "reflection", p.reflection, "adjusted", p.adjusted, "tasks", tasks));
        }
        for (LifeData.Sleep s : d.sleeps) sleeps.put(object("day", s.day, "wake", s.wakeAt, "bed", s.bedAt,
                "wakeTarget", s.wakeTarget, "bedTarget", s.bedTarget, "corrected", s.corrected));
        JSONArray closed = new JSONArray();
        for (String key : d.closedAvailableMinutes.keySet()) closed.put(object("day", key,
                "available", d.closedAvailableMinutes.get(key), "covered", d.closedCoveredMinutes.get(key)));
        root.put("routine", RoutineCodec.encode(d.routine));
        root.put("windows", windows); root.put("courses", courses); root.put("plans", plans); root.put("sleeps", sleeps);
        root.put("acknowledged", new JSONArray(d.acknowledgedDays)); root.put("exits", new JSONArray(d.emergencyExits)); root.put("closed", closed);
        return root;
    }
    static LifeData decode(JSONObject root) throws Exception {
        if (root.optInt("schema") != 1) throw new IllegalArgumentException("不支持的数据版本");
        LifeData d = new LifeData();
        d.routine = RoutineCodec.decode(root.optJSONObject("routine"));
        d.sleepEnabled = root.optBoolean("sleepEnabled"); d.planningEnabled = root.optBoolean("planningEnabled");
        d.coursesConfirmed = root.optBoolean("coursesConfirmed"); d.wakeMinute = minute(root, "wake", 420); d.bedMinute = minute(root, "bed", 1380);
        d.forceMode = root.optInt("force", 2); d.defaultMinutes = root.optInt("length", 30);
        d.enabledAt = root.optLong("enabledAt"); d.graceUntil = root.optLong("grace");
        d.termStart = root.optString("termStart"); if (!d.termStart.isEmpty()) LocalDate.parse(d.termStart);
        d.imageUri = root.optString("imageUri"); d.periodText = root.optString("periodText");
        JSONArray a = array(root, "windows");
        for (int i = 0; i < a.length(); i++) { JSONObject o = a.getJSONObject(i); LifeData.Window w = new LifeData.Window();
            w.days = o.getInt("days"); w.start = minute(o, "start", 480); w.end = minute(o, "end", 1320); d.windows.add(w); }
        a = array(root, "courses");
        for (int i = 0; i < a.length(); i++) { JSONObject o = a.getJSONObject(i); LifeData.Course c = new LifeData.Course();
            c.id = o.getString("id"); c.name = o.getString("name"); c.date = o.optString("date"); c.day = o.getInt("day");
            if (!c.date.isEmpty()) LocalDate.parse(c.date);
            c.start = minute(o, "start", 0); c.end = minute(o, "end", 0); c.firstWeek = o.optInt("first", 1); c.lastWeek = o.optInt("last", 52); c.parity = o.optInt("parity");
            JSONArray skip = array(o, "skip"); for (int j = 0; j < skip.length(); j++) { String date = skip.getString(j); LocalDate.parse(date); c.skipDates.add(date); }
            d.courses.add(c); }
        a = array(root, "plans");
        for (int i = 0; i < a.length(); i++) { JSONObject o = a.getJSONObject(i); LifeData.Plan p = new LifeData.Plan();
            p.id = o.getString("id"); p.day = o.getString("day"); LocalDate.parse(p.day); p.start = o.getLong("start"); p.end = o.getLong("end");
            p.reviewedAt = o.optLong("reviewed"); p.reflection = o.optString("reflection"); p.adjusted = o.optBoolean("adjusted");
            JSONArray tasks = array(o, "tasks"); for (int j = 0; j < tasks.length(); j++) { JSONObject t = tasks.getJSONObject(j); LifeData.Task task = new LifeData.Task();
                task.name = t.getString("name"); task.minutes = t.getInt("minutes"); task.percent = t.optInt("percent", -1); p.tasks.add(task); }
            d.plans.add(p); }
        a = array(root, "sleeps");
        for (int i = 0; i < a.length(); i++) { JSONObject o = a.getJSONObject(i); LifeData.Sleep s = new LifeData.Sleep();
            s.day = o.getString("day"); LocalDate.parse(s.day); s.wakeAt = o.optLong("wake"); s.bedAt = o.optLong("bed");
            s.wakeTarget = o.optLong("wakeTarget"); s.bedTarget = o.optLong("bedTarget"); s.corrected = o.optBoolean("corrected"); d.sleeps.add(s); }
        a = array(root, "acknowledged"); for (int i = 0; i < a.length(); i++) d.acknowledgedDays.add(a.getString(i));
        a = array(root, "exits"); for (int i = 0; i < a.length(); i++) d.emergencyExits.add(a.getLong(i));
        a = array(root, "closed"); for (int i = 0; i < a.length(); i++) { JSONObject o = a.getJSONObject(i);
            d.closedAvailableMinutes.put(o.getString("day"), o.getLong("available")); d.closedCoveredMinutes.put(o.getString("day"), o.getLong("covered")); }
        return d;
    }
}
