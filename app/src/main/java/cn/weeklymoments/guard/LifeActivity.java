package cn.weeklymoments.guard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

public final class LifeActivity extends Activity {
    private LifeStore store;
    private LinearLayout root;
    private String section = "home";
    private LocalDate reportDate;
    private final ZoneId zone = ZoneId.systemDefault();
    private static final int EXPORT = 811;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store = LifeStore.get(this); UiMotion.prepare(this); setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        route(getIntent().getStringExtra("section"));
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); route(intent.getStringExtra("section")); }
    @Override protected void onResume() { super.onResume(); LifeScheduler.refresh(this); if (store != null && section.equals("home")) home(); else if(store != null && section.equals("report") && reportDate!=null)report(reportDate); }
    private void route(String target) { if ("due".equals(target)) due(); else if("settings".equals(target))settings(); else home(); }
    private LifeData data() { return store.data(); }
    private long now() { return System.currentTimeMillis(); }
    private LocalDate today() { return LifeEngine.day(data(), now(), zone); }
    private void screen(String title, String subtitle, String name) {
        section = name;
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        root = LifeUi.column(this, false); root.setPadding(dp(22), dp(22), dp(22), dp(32));
        root.setBackgroundColor(LifeUi.BG); scroll.addView(root); setContentView(scroll);
        add(root, LifeUi.button(this, "‹  " + (name.equals("home") ? "自律空间" : "作息与计划"), false,
                name.equals("home") ? this::finish : this::home), 0);
        label(root, title, 27, true, 22); label(root, subtitle, 14, false, 8);
    }
    private void home() {
        screen("把一天，过回自己手里", "睡眠记录 · 课外短时计划 · 一天的复盘", "home");
        if (!store.error().isEmpty()) { label(root, store.error(), 16, true, 18); button(root,"导出保留的原始记录",false,this::exportDocument); return; }
        LifeEngine.Due due = LifeEngine.due(data(), now(), zone);
        LinearLayout card = card();
        label(card, due.title(), 20, true, 0);
        if (data().graceUntil > now()) label(card, "紧急暂停至 " + LifeUi.clock(data().graceUntil), 14, false, 10);
        label(card, "目标起床 " + LifeUi.clock(data().wakeMinute) + "  ·  准备睡觉 " + LifeUi.clock(data().bedMinute), 15, false, 12);
        if (due.kind != LifeEngine.Kind.NONE) button(card, "处理当前跟进", true, this::due);
        if (!data().sleepEnabled && !data().planningEnabled && !RoutineEngine.enabled(data().routine)) label(card, "新功能尚未启用。先设置目标、课表和循环时段，再开启。", 14, false, 10);
        for (LifeData.Plan p : data().plans) if (p.reviewedAt == 0 && p.end > now()) {
            label(card, "进行中：" + LifeUi.clock(p.end) + " 结束", 16, true, 14);
            for (LifeData.Task t : p.tasks) label(card, "· " + t.name + "  /  " + t.minutes + " 分钟", 14, false, 6);
            button(card, "查看／提前结束并反馈", false, () -> review(p.id)); break;
        }
        button(root, "周计划 · 今日计划 · AI 知识卡", false, () -> startActivity(new Intent(this,RoutineActivity.class)));
        button(root, "作息目标与循环时段", false, this::settings);
        button(root, "课表图片识别／编辑与调课", false, () -> startActivity(new Intent(this, CourseActivity.class)));
        button(root, "记录已经起床", false, () -> sleepEditor(LocalDate.now(zone).toString(), true));
        button(root, "记录准备睡觉", false, () -> sleepEditor(today().toString(), false));
        button(root, "睡眠记录与近 7 天统计", false, this::sleepHistory);
        button(root, "今日完成率与计划记录", false, () -> report(today()));
        permissions();
        button(root, "备份、恢复与账号", false, () -> startActivity(new Intent(this,DataActivity.class)));
        label(root, "课表在本机识别。作息和完成情况由你确认，可补记或修正。", 12, false, 20);
    }
    private void permissions() {
        LinearLayout c = card(); label(c, "提醒运行状态", 19, true, 0);
        label(c, "无障碍服务：" + (MomentsGuardService.connected ? "已连接" : "未连接，全手机提醒页尚不能显示"), 14, false, 10);
        button(c, "查看系统无障碍设置", false, () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        boolean runtimeAllowed = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean allowed = runtimeAllowed && ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled();
        label(c, "通知：" + (allowed ? "已授权" : "未授权"), 14, false, 10);
        if (!allowed) button(c, "允许到点通知", false, () -> {
            if(Build.VERSION.SDK_INT >= 33 && !runtimeAllowed) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 71);
            else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
        });
        label(c, "准时提醒：" + (LifeScheduler.exactAllowed(this) ? "已授权" : "未授权，后台提醒可能延后"), 14, false, 10);
        if (Build.VERSION.SDK_INT >= 31 && !LifeScheduler.exactAllowed(this)) button(c, "允许闹钟和提醒权限", false, () -> {
            try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); }
            catch (Exception ex) { toast("请在系统应用设置中查看闹钟和提醒权限"); }
        });
        label(c, "请允许后台运行。关机、服务或权限关闭时可能漏提醒；回来后会核对待办。", 12, false, 12);
    }
    private void due() {
        LifeEngine.Due due = LifeEngine.due(data(), now(), zone);
        switch (due.kind) {
            case WEEK_PLAN: case DAY_PLAN: case WEEK_REVIEW: case LEARN:
                startActivity(new Intent(this,RoutineActivity.class).putExtra("section","due")); break;
            case WAKE: sleepEditor(due.day, true); break;
            case BED: sleepEditor(due.day, false); break;
            case PLAN: planEditor(); break;
            case REVIEW: review(due.planId); break;
            case DAY_REVIEW: report(LocalDate.parse(due.day)); break;
            default: home();
        }
    }
    private void settings() {
        screen("目标与节奏，都可以调整", "历史保留，提醒随新目标调整。", "settings");
        Switch sleep = new Switch(this); sleep.setText("启用起床／睡觉记录提醒"); sleep.setChecked(data().sleepEnabled); add(root, sleep, 16);
        Switch planning = new Switch(this); planning.setText("启用课外循环计划"); planning.setChecked(data().planningEnabled); add(root, planning, 12);
        final int[] wake = {data().wakeMinute}, bed = {data().bedMinute};
        add(root, timeButton("目标起床", wake), 14); add(root, timeButton("准备睡觉", bed), 8);
        if(data().enabledAt==0)label(root,"首次启用前，请把示例目标改成自己的作息，并核对课表。",14,true,12);
        label(root, "睡眠时段不安排计划，可设置凌晨就寝。记录由你确认，不检测睡眠。", 13, false, 10);
        Spinner mode = spinner(new String[]{"全手机提醒页（保留紧急退出）", "只在微信内强制跟进", "通知提醒"});
        mode.setSelection(Math.max(0, Math.min(2, 2 - data().forceMode))); label(root, "跟进方式", 17, true, 20); add(root, mode, 8);
        label(root, "全手机提醒会暂时遮挡普通应用，完成跟进后解除。保留电话、时钟、通话和设置，可紧急暂停 15 分钟。", 13, false, 10);
        label(root, "需要循环计划的时间段", 19, true, 24);
        label(root, "支持多个时段，自动避开课程与睡眠。跨午夜按开始日计算。", 13, false, 8);
        LinearLayout windows = LifeUi.column(this, false); add(root, windows, 0);
        List<WindowRow> rows = new ArrayList<>();
        for (LifeData.Window w : data().windows) addWindow(windows, rows, w);
        button(root, "添加一个循环时段", false, () -> addWindow(windows, rows, new LifeData.Window()));
        label(root, "课表状态：" + (data().coursesConfirmed ? "已核对" : "待确认；启用循环计划前须核对课表或确认无课"), 14, true, 16);
        button(root, "保存目标与时段", true, () -> guarded(() -> {
            LifeData next = store.copy(); next.sleepEnabled = sleep.isChecked(); next.planningEnabled = planning.isChecked();
            next.wakeMinute = wake[0]; next.bedMinute = bed[0]; next.forceMode = 2 - mode.getSelectedItemPosition();
            if (wake[0] == bed[0]) throw new IllegalArgumentException("起床与睡觉目标不能是同一时间");
            next.windows.clear();
            for (WindowRow row : rows) { LifeData.Window w = row.value(); if (w.days == 0 || w.start == w.end) throw new IllegalArgumentException("请检查循环时段的星期和时间"); next.windows.add(w); }
            if (next.planningEnabled && (!next.coursesConfirmed || next.windows.isEmpty())) throw new IllegalArgumentException("请先确认课表，并至少设置一个循环时段");
            if (next.enabledAt == 0 && (next.sleepEnabled || next.planningEnabled)) next.enabledAt = now();
            apply(next, this::home);
        }));
    }
    static void applySettings(Activity activity, LifeStore store, LifeData next, Runnable done) {
        long now = System.currentTimeMillis();
        LifeEngine.Span free = LifeEngine.availableNow(next, now, ZoneId.systemDefault());
        long limit = free == null ? now : free.end;
        List<LifeData.Plan> affected = new ArrayList<>();
        if (next.planningEnabled) for (LifeData.Plan p : next.plans) if (p.reviewedAt == 0 && p.end > limit && p.end > now) affected.add(p);
        Runnable save = () -> {
            try {
                for (LifeData.Plan p : affected) { p.end = Math.max(p.start, Math.min(p.end, limit)); p.adjusted = true; }
                store.replace(next); LifeScheduler.refresh(activity); done.run();
            } catch (RuntimeException ex) { Toast.makeText(activity, ex.getMessage(), Toast.LENGTH_LONG).show(); }
        };
        if (affected.isEmpty()) save.run();
        else new AlertDialog.Builder(activity).setTitle("新设置影响了正在进行的计划")
                .setMessage("为避开新的课程、睡眠或循环时段边界，当前计划将提前结束于 " + LifeUi.clock(limit) + "，任务记录会保留并等待反馈。")
                .setNegativeButton("暂不修改", null).setPositiveButton("调整并保存", (d, w) -> save.run()).show();
    }
    private void apply(LifeData next, Runnable done) { applySettings(this, store, next, done); }
    private static final class WindowRow {
        final int[] start, end; final CheckBox[] days; final LinearLayout view;
        WindowRow(int[] start, int[] end, CheckBox[] days, LinearLayout view) { this.start=start; this.end=end; this.days=days; this.view=view; }
        LifeData.Window value() { LifeData.Window w = new LifeData.Window(); w.days = 0; for (int i=0;i<7;i++) if(days[i].isChecked()) w.days |= 1<<i; w.start=start[0];w.end=end[0];return w; }
    }
    private void addWindow(LinearLayout parent, List<WindowRow> rows, LifeData.Window value) {
        LinearLayout c = LifeUi.column(this, true); add(parent, c, 12);
        int[] start = {value.start}, end = {value.end}; add(c, timeButton("开始", start), 0); add(c, timeButton("结束", end), 6);
        CheckBox[] days = new CheckBox[7]; LinearLayout line = new LinearLayout(this); line.setOrientation(LinearLayout.VERTICAL); add(c,line,8);
        String[] labels={"周一","周二","周三","周四","周五","周六","周日"};
        for(int i=0;i<7;i++){days[i]=new CheckBox(this);days[i].setText(labels[i]);days[i].setChecked((value.days&(1<<i))!=0);line.addView(days[i]);}
        WindowRow row = new WindowRow(start,end,days,c); rows.add(row);
        button(c,"移除此时段",false,()->{rows.remove(row);parent.removeView(c);});
    }
    private void sleepEditor(String day, boolean wake) {
        screen(wake ? "记录起床" : "记录准备睡觉", "归属日 " + day + " · 可以补记和修正，请填写已经发生的时间。", "sleep");
        LifeData.Sleep record=data().sleep(day,false);
        long existing=record==null?0:(wake?record.wakeAt:record.bedAt);
        ZonedDateTime initial=Instant.ofEpochMilli(existing>0?existing:now()).atZone(zone);
        LocalDate[] actualDate={initial.toLocalDate()}; int[] minute={initial.getHour()*60+initial.getMinute()};
        Button date=LifeUi.button(this,"实际日期："+actualDate[0],false,()->{});
        date.setOnClickListener(v->pickDate(actualDate[0], chosen->{actualDate[0]=chosen;date.setText("实际日期："+chosen);})); add(root,date,22);
        add(root,timeButton("实际时间",minute),8);
        label(root,wake?"这是你确认已经起床的时间。与前一天的就寝记录配对后，会显示记录的休息间隔。":"这是你确认准备睡觉的时间。提交后本次清醒周期停止循环计划提醒；夜间不会继续要求你安排任务。",14,false,18);
        button(root,"保存记录",true,()->guarded(()->{
            store.recordSleep(day,wake,LifeEngine.at(actualDate[0],minute[0],zone),existing>0 || !actualDate[0].equals(LocalDate.now(zone)));
            LifeScheduler.refresh(this);due();
        }));
        if(existing>0)button(root,"撤销这条"+(wake?"起床":"就寝")+"记录",false,()->
            new AlertDialog.Builder(this).setTitle("撤销这条记录？").setMessage("该时间会恢复为未记录；就寝记录撤销后，会根据目标和课表重新计算提醒。")
                .setNegativeButton("保留",null).setPositiveButton("撤销",(d,w)->guarded(()->{store.clearSleep(day,wake);LifeScheduler.refresh(this);home();})).show());
    }
    private void sleepHistory() {
        screen("照顾好每天的开始和结束", "记录的休息间隔 = 起床确认时间 − 前一天准备睡觉时间；不等同于实际睡眠时长。", "sleepHistory");
        LocalDate today=LocalDate.now(zone);int wakes=0,beds=0,lateWake=0,lateBed=0;
        for(int i=0;i<7;i++){
            LocalDate date=today.minusDays(i);LifeData.Sleep s=data().sleep(date.toString(),false);
            LinearLayout c=card();label(c,date.toString(),18,true,0);
            label(c,"起床："+(s==null?"未记录":LifeUi.clock(s.wakeAt))+"\n就寝："+(s==null?"未记录":LifeUi.clock(s.bedAt)),14,false,10);
            label(c,"记录的休息间隔："+LifeStats.restInterval(data(),date),14,false,8);
            if(s!=null&&s.wakeAt>0){wakes++;if(s.wakeTarget>0&&s.wakeAt>s.wakeTarget+15*60_000L)lateWake++;}
            if(s!=null&&s.bedAt>0){beds++;if(s.bedTarget>0&&s.bedAt>s.bedTarget+15*60_000L)lateBed++;}
            if(s!=null&&s.corrected)label(c,"包含补记／修正",12,false,6);
            button(c,"补记／修改起床",false,()->sleepEditor(date.toString(),true));
            button(c,"补记／修改就寝",false,()->sleepEditor(date.toString(),false));
        }
        label(root,"近7天：起床记录 "+wakes+" 天，其中晚于当时目标15分钟的有 "+lateWake+" 天；就寝记录 "+beds+" 天，其中晚于目标15分钟的有 "+lateBed+" 天。缺失记录单独列出，不当作准时。",15,true,20);
    }
    private static final class TaskRow {
        EditText name, minutes; LinearLayout view;
    }
    private void planEditor() {
        LifeEngine.Span free=LifeEngine.availableNow(data(),now(),zone);
        if(free==null){home();return;}
        screen("下一小段，想完成什么？", "选择时长，写具体任务，并估计各自需要的分钟数。", "plan");
        CompanionDialogue.add(this,root,"看一眼今天的重点，把下一步写得小一点。",true);
        RoutineActivity.reference(this,root,data().routine,today(),true);
        int[] lengths={30,60,90,120};Spinner length=spinner(new String[]{"0.5小时 · 30分钟","1小时 · 60分钟","1.5小时 · 90分钟","2小时 · 120分钟"});
        int selection=0;for(int i=0;i<4;i++)if(lengths[i]==data().defaultMinutes)selection=i;length.setSelection(selection);add(root,length,18);
        TextView capacity=LifeUi.text(this,"",14,false);add(root,capacity,12);
        LinearLayout tasks=LifeUi.column(this,false);add(root,tasks,0);List<TaskRow> rows=new ArrayList<>();
        Runnable update=()->{long start=now();long end=Math.min(start+lengths[length.getSelectedItemPosition()]*60_000L,free.end);capacity.setText("本段最晚至 "+LifeUi.clock(end)+"；当前最多 "+Math.max(0,(end-start)/60_000)+" 分钟。课程和睡眠边界会自动截短，请据此调整任务量。");};
        update.run();
        int cap=(int)Math.min(lengths[selection],(free.end-now())/60_000);
        addTask(tasks,rows,Math.max(1,cap));
        length.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int pos,long id){update.run();if(rows.size()==1)rows.get(0).minutes.setText(""+Math.max(1,Math.min(lengths[pos],(free.end-now())/60_000)));}
        });
        button(root,"再加一项任务",false,()->{if(rows.size()>=8){toast("每段最多8项，建议先把目标写小一点");return;}addTask(tasks,rows,5);});
        label(root,"完成率按预计分钟数加权。任务别超过可用时间，记得留出休息。",13,false,16);
        button(root,"开始这一段",true,()->guarded(()->{
            List<LifeData.Task> values=new ArrayList<>();for(TaskRow row:rows){LifeData.Task t=new LifeData.Task();t.name=row.name.getText().toString().trim();t.minutes=Integer.parseInt(row.minutes.getText().toString());values.add(t);}
            store.startPlan(lengths[length.getSelectedItemPosition()],values);LifeScheduler.refresh(this);home();
        }));
    }
    private void addTask(LinearLayout parent,List<TaskRow> rows,int minutes){
        TaskRow row=new TaskRow();row.view=LifeUi.column(this,true);add(parent,row.view,14);
        row.name=LifeUi.field(this,"例如：完成数学第1至3题","",false);add(row.view,row.name,0);
        label(row.view,"预计分钟",13,false,12);row.minutes=LifeUi.field(this,"分钟",""+minutes,true);add(row.view,row.minutes,6);
        button(row.view,"移除此任务",false,()->{rows.remove(row);parent.removeView(row.view);});rows.add(row);
    }
    private void review(String id){
        LifeData.Plan plan=data().plan(id);if(plan==null){home();return;}
        screen("这一段，完成了多少？",LifeUi.clock(plan.start)+" → "+LifeUi.clock(plan.end)+(plan.adjusted?" · 已调整结束时间":""),"review");
        List<SeekBar> bars=new ArrayList<>();
        for(LifeData.Task task:plan.tasks){LinearLayout c=card();label(c,task.name+" · 预计"+task.minutes+"分钟",18,true,0);
            TextView value=LifeUi.text(this,"完成 "+Math.max(0,task.percent)+"%",15,false);add(c,value,12);
            SeekBar bar=new SeekBar(this);bar.setMax(100);bar.setProgress(Math.max(0,task.percent));bar.setContentDescription(task.name+"完成百分比");add(c,bar,10);
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int p,boolean user){value.setText("完成 "+p+"%");}});bars.add(bar);}
        Spinner reason=spinner(new String[]{"按计划推进","任务估计过多","临时事务打断","注意力分散","需要更多休息","其他"});add(root,reason,18);
        EditText note=LifeUi.field(this,"下一段想怎么调整？（可选）",plan.reflection,false);add(root,note,12);
        Runnable save=()->guarded(()->{List<Integer> percents=new ArrayList<>();for(SeekBar bar:bars)percents.add(bar.getProgress());
            store.review(id,percents,reason.getSelectedItem()+"；"+note.getText(),now()<plan.end);LifeScheduler.refresh(this);due();});
        button(root,plan.reviewedAt>0?"更新反馈":now()<plan.end?"提前结束并反馈":"提交反馈，安排下一段",true,()->{
            if(now()<plan.end)new AlertDialog.Builder(this).setTitle("提前结束这一段？").setMessage("会记录实际结束时间，并使用你填写的比例。之后根据当前空档安排下一段。").setNegativeButton("继续当前计划",null).setPositiveButton("结束并保存",(d,w)->save.run()).show();else save.run();});
    }
    private void report(LocalDate date){
        reportDate=date;
        screen(date+" · 一天的回顾","完成率来自你逐项确认的比例，建议根据本地记录生成。","report");
        button(root,"选择其他日期",false,()->pickDate(date,this::report));
        LifeStats.Day stats=LifeStats.day(data(),date,zone);
        RoutineData.Day daily=data().routine.day(date.toString());
        if(daily!=null){LinearLayout dailyCard=card();label(dailyCard,"今日待办 "+RoutineEngine.percent(daily.items)+"%",24,true,0);for(RoutineData.Item item:daily.items)label(dailyCard,(item.done?"☑ ":"□ ")+item.name,15,false,8);button(dailyCard,"更新今天的完成清单",false,()->startActivity(new Intent(this,RoutineActivity.class).putExtra("section","day").putExtra("key",date.toString())));}
        LinearLayout c=card();
        label(c,stats.percent()<0?"暂无完成率":stats.percent()+"%",44,true,0);
        label(c,"任务完成率 · 按预计分钟数加权",15,false,8);
        label(c,"共 "+stats.plans+" 段计划；还有 "+stats.pending+" 段未反馈。未反馈暂按0计入完成率，不能据此认定任务没做。",14,false,12);
        label(c,"计划覆盖率："+(stats.coverage()<0?"暂无可用时段":stats.coverage()+"%")+"\n已安排 "+stats.coveredMinutes+" / 可用 "+stats.availableMinutes+" 分钟",15,true,16);
        label(c,"覆盖率只表示课外可用时间中安排过计划的比例，不代表你其余时间没有做事。当天结束确认后保留这份时段统计。",12,false,8);
        label(root,"给下一次的小建议",21,true,24);
        for(String advice:LifeStats.advice(data(),date,zone)){LinearLayout a=card();label(a,advice,15,false,0);}
        long exits=data().emergencyExits.stream().filter(t->LifeEngine.day(data(),t,zone).equals(date)).count();
        label(root,"本日紧急暂停 "+exits+" 次。暂停不会停止计划计时，也不会修改你反馈的比例。",12,false,16);
        for(LifeData.Plan p:data().plans)if(p.day.equals(date.toString()))button(root,LifeUi.clock(p.start)+" · "+(p.reviewedAt>0?"查看／修改反馈":"补充反馈"),false,()->review(p.id));
        LifeData.Sleep sleep=data().sleep(date.toString(),false);
        if(now()>=LifeEngine.bedAt(data(),date,zone)||(sleep!=null&&sleep.bedAt>0))
            button(root,"确认本日复盘，继续",true,()->guarded(()->{store.acknowledge(date.toString());LifeScheduler.refresh(this);due();}));
        else label(root,"到目标就寝时间后，会提醒你确认当天复盘。",13,false,20);
    }
    @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);
        if(request==EXPORT&&result==RESULT_OK&&intent!=null)guarded(()->{try(OutputStream output=getContentResolver().openOutputStream(intent.getData())){
            if(output==null)throw new IllegalStateException("无法打开保存位置");store.writeBackup(output);toast("记录已导出到你选择的位置");}catch(Exception ex){throw new IllegalStateException("导出失败，请重试",ex);}});}
    private void exportDocument(){Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE,"作息与计划-"+LocalDate.now(zone)+".json");startActivityForResult(intent,EXPORT);}
    private Button timeButton(String label,int[] value){Button button=LifeUi.button(this,label+"："+LifeUi.clock(value[0]),false,()->{});
        button.setOnClickListener(v->LifeUi.pickTime(this,label,value[0],minute->{value[0]=minute;button.setText(label+"："+LifeUi.clock(minute));}));return button;}
    private interface DateChoice{void chosen(LocalDate date);}
    private void pickDate(LocalDate initial,DateChoice choice){new DatePickerDialog(this,(v,y,m,d)->choice.chosen(LocalDate.of(y,m+1,d)),initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();}
    private Spinner spinner(String[] values){Spinner v=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);v.setAdapter(a);return v;}
    private LinearLayout card(){LinearLayout c=LifeUi.column(this,true);add(root,c,18);return c;}
    private void label(LinearLayout p,String value,int size,boolean bold,int top){add(p,LifeUi.text(this,value,size,bold),top);}
    private void button(LinearLayout p,String text,boolean primary,Runnable action){add(p,LifeUi.button(this,text,primary,action),12);}
    private void add(LinearLayout p,android.view.View v,int top){LifeUi.add(p,v,top);}
    private int dp(int value){return LifeUi.dp(this,value);}
    private void guarded(Runnable action){try{action.run();}catch(RuntimeException ex){toast(ex instanceof NumberFormatException?"请填写有效的预计分钟数":ex.getMessage()==null?"操作未完成，请重试":ex.getMessage());}}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
}
