package cn.weeklymoments.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PolicyStore store;
    private TextView serviceStatus, timingStatus, counter, nextTime;
    private Button startButton, endButton, pauseButton;
    private final Runnable ticker = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 1000); }
    };
    private static final int INK = LifeUi.INK, GREEN = LifeUi.GREEN;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = PolicyStore.get(this);
        UiMotion.prepare(this);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        if ("moments".equals(getIntent().getStringExtra("section"))) moments(); else home();
    }

    private void home() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(LifeUi.BG);
        LinearLayout layout=LifeUi.column(this,false);layout.setPadding(dp(22),dp(24),dp(22),dp(32));scroll.addView(layout);setContentView(scroll);
        TextView eyebrow=label("FOCUS  /  自律空间",11,true);eyebrow.setTextColor(GREEN);eyebrow.setLetterSpacing(.12f);add(layout,eyebrow,0);
        add(layout,new FocusCoverView(this),8);
        android.view.ViewGroup.LayoutParams hero=layout.getChildAt(1).getLayoutParams();hero.height=dp(176);layout.getChildAt(1).setLayoutParams(hero);
        add(layout,label("把时间，\n留给想成为的自己。",30,true),10);
        TextView intro=label("从一件小事开始，让每一天有方向。",14,false);intro.setTextColor(LifeUi.MUTED);add(layout,intro,12);
        add(layout,LifeUi.button(this,"开始今日计划  ↗",true,()->openRoutine("day")),24);
        LinearLayout first=new LinearLayout(this), second=new LinearLayout(this);
        tile(first,"01  计划","本周方向 · 今日重点",()->openRoutine("day"));
        tile(first,"02  作息","睡眠记录 · 短时计划",()->startActivity(new Intent(this,LifeActivity.class)));
        tile(second,"03  知识","每天三张 · 慢慢理解",()->openRoutine("learn"));
        tile(second,"04  专注","朋友圈节制 · 留住注意力",()->startActivity(new Intent(this,MainActivity.class).putExtra("section","moments")));
        add(layout,first,24);add(layout,second,12);
        add(layout,button("周计划与习惯  →",()->openRoutine("week")),18);
        add(layout,button("功能与音效设置  →",()->openRoutine("settings")),8);
        add(layout,button("账号与数据 · 备份恢复  →",()->startActivity(new Intent(this,DataActivity.class))),8);
        TextView local=label("记录留在本机。按你自己的步调开始。",12,false);local.setTextColor(LifeUi.MUTED);add(layout,local,20);
    }
    private void openRoutine(String section){startActivity(new Intent(this,RoutineActivity.class).putExtra("section",section));}
    private void tile(LinearLayout row,String title,String subtitle,Runnable action){
        LinearLayout tile=LifeUi.column(this,true);tile.setPadding(dp(16),dp(20),dp(16),dp(20));
        TextView heading=label(title,19,true);heading.setTextColor(GREEN);add(tile,heading,0);
        TextView note=label(subtitle,12,false);note.setTextColor(LifeUi.MUTED);add(tile,note,10);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-1,1);if(row.getChildCount()>0)params.leftMargin=dp(12);row.addView(tile,params);
        tile.setOnClickListener(v->action.run());tile.setBackground(new RippleDrawable(ColorStateList.valueOf(0x287AE0FF),LifeUi.shape(this,0xFF162234,20),LifeUi.shape(this,Color.WHITE,20)));
        tile.setFocusable(true);tile.setContentDescription(title+"，"+subtitle);
    }
    private void moments() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(30), dp(24), dp(32));
        scroll.addView(layout);
        setContentView(scroll);

        add(layout, button("‹  自律空间", this::finish), 0);
        add(layout, label("朋友圈节制", 28, true), 24);
        add(layout, label("给注意力留一点空间", 16, false), 8);

        LinearLayout card = panel();
        add(layout, card, 26);
        add(card, label("每 7 天 · 一次", 15, true), 0);
        counter = label("15:00", 48, true);
        counter.setTextColor(GREEN);
        add(card, counter, 10);
        timingStatus = label("", 17, true);
        nextTime = label("", 14, false);
        add(card, timingStatus, 8);
        add(card, nextTime, 8);
        startButton = button("开启本次 15 分钟", this::confirmStart);
        endButton = button("提前结束本次放行", () -> {
            new AlertDialog.Builder(this).setTitle("结束本次放行？")
                    .setMessage("剩余时间不会保留。下次仍需等到本次开始后的第 7 天。")
                    .setNegativeButton("继续使用", null).setPositiveButton("结束", (d, w) -> {
                        if (!store.end()) toast("保存失败，请重试");
                        refresh();
                    }).show();
        });
        add(card, startButton, 18);
        pauseButton = button("暂停本次计时", () -> {
            boolean ok=store.paused()?store.resume():store.pause();
            if(!ok)toast("无法切换：额度可能已用完，或已达到三次暂停上限"); refresh();
        });
        add(card, pauseButton, 8);
        add(card, endButton, 8);
        add(card, label("每 7 天共 15 分钟，最多暂停 3 次。\n离开微信仍计时；第 7 天剩余额度到期。", 13, false), 10);

        add(layout, label("运行状态", 20, true), 26);
        serviceStatus = label("", 15, false);
        add(layout, serviceStatus, 10);
        add(layout, button("开启／查看无障碍服务", this::requestService), 12);
        add(layout, button("华为后台运行设置说明", this::showHuaweiHelp), 4);

        add(layout, label("拦截范围", 20, true), 26);
        RadioGroup scope = new RadioGroup(this);
        RadioButton personal = new RadioButton(this), all = new RadioButton(this);
        personal.setId(1001); all.setId(1002);
        personal.setText("好友个人朋友圈（默认）");
        all.setText("所有朋友圈入口");
        personal.setTextColor(INK); all.setTextColor(INK);
        personal.setTextSize(16); all.setTextSize(16);
        scope.addView(personal); scope.addView(all);
        scope.check(store.allMoments() ? 1002 : 1001);
        scope.setOnCheckedChangeListener((g, id) -> store.setAllMoments(id == 1002));
        add(layout, scope, 8);
        add(layout, label("默认限制个人朋友圈，也可能包含自己。选择“所有”还会限制发现页、评论和朋友圈图片。", 13, false), 8);
        add(layout, button("打开微信并测试", this::openWechat), 18);
        add(layout, button("预览提醒页 · 不消耗额度", () ->
                startActivity(new Intent(this, ReminderPreviewActivity.class))), 8);

        add(layout, button("首次使用 · 验证方法", () -> new AlertDialog.Builder(this).setTitle("确认拦截生效")
                .setMessage("1. 开启服务。\n2. 打开好友主页的朋友圈，确认出现提醒页。\n3. 点“返回专注自己”。\n4. 检查聊天和通讯录能正常使用。\n\n若未拦截，请复制适配信息反馈。")
                .setPositiveButton("知道了",null).show()), 22);
        add(layout, button("查看／复制适配信息", this::showDiagnostics), 12);
        add(layout, button("隐私与使用说明", this::showPrivacy), 4);
        add(layout, label("关闭服务、卸载或清除数据后，限制失效。此安装包仅支持 Android。", 12, false), 20);
    }

    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(ticker); ticker.run(); }
    @Override protected void onPause() { handler.removeCallbacks(ticker); super.onPause(); }

    private boolean serviceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName desired = new ComponentName(this, MomentsGuardService.class);
        for (String value : enabled.split(":")) if (desired.equals(ComponentName.unflattenFromString(value))) return true;
        return false;
    }

    private void refresh() {
        if (store == null || serviceStatus == null) return;
        boolean connected = MomentsGuardService.connected;
        boolean enabled = serviceEnabled();
        serviceStatus.setText(connected && !store.consented() ? "● 请点击下方按钮，先了解并确认权限用途"
                : connected ? "● 已连接 · 请到微信验证"
                : enabled ? "● 已授权，等待系统连接；若持续如此请重开服务"
                : "● 尚未开启 · 进入设置授权");
        serviceStatus.setTextColor(connected ? GREEN : 0xFFE8C899);
        long open = store.remaining();
        counter.setText(open > 0 ? duration(open) : "15:00");
        timingStatus.setText(open > 0 ? (store.paused() ? "已暂停 · 剩余额度保留" : "本次放行剩余时间") : store.canStart() ? "本次额度可用" : "朋友圈限制中");
        long cooldown = store.cooldownRemaining();
        if (store.boot() < 0) nextTime.setText("无法读取系统启动标识，暂不能开启计时。请反馈适配信息。");
        else if (cooldown == 0) nextTime.setText("开启后，7 天可再用。");
        else nextTime.setText("下次可开启：" + new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
                    .format(new Date(System.currentTimeMillis() + cooldown)));
        startButton.setVisibility(open > 0 ? View.GONE : View.VISIBLE);
        startButton.setEnabled(connected && store.consented() && store.canStart());
        endButton.setVisibility(open > 0 ? View.VISIBLE : View.GONE);
        pauseButton.setVisibility(open > 0 ? View.VISIBLE : View.GONE);
        pauseButton.setText(store.paused()?"恢复计时 · 已暂停 "+store.pauses()+"/3 次":"暂停计时 · 已用 "+store.pauses()+"/3 次");
        pauseButton.setEnabled(store.paused() || store.pauses()<3);
    }

    private void confirmStart() {
        new AlertDialog.Builder(this).setTitle("使用本次 15 分钟额度？")
                .setMessage("共 15 分钟，最多暂停 3 次。\n离开微信仍计时；第三次恢复后不能再暂停。\n第 7 天余额到期，提前结束不退回。")
                .setNegativeButton("暂不开启", null).setPositiveButton("确认开启", (d, w) -> {
                    if (!MomentsGuardService.connected) { toast("请先开启无障碍服务"); return; }
                    if (!store.start()) toast("暂时无法开启，请查看下次可用时间");
                    refresh();
                }).show();
    }

    private void requestService() {
        if (store.consented()) { accessibilitySettings(); return; }
        new AlertDialog.Builder(this).setTitle("开启前请了解权限用途")
                .setMessage(getString(R.string.accessibility_description))
                .setNegativeButton("暂不开启", null).setPositiveButton("我了解，前往设置", (d, w) -> {
                    store.consent(); accessibilitySettings();
                }).show();
    }

    private void accessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (ActivityNotFoundException ex) {
            toast("请在系统设置中搜索“无障碍”，开启自律空间");
        }
    }

    private void openWechat() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(PageMatcher.WECHAT);
        if (intent == null) { toast("未找到微信，请手动打开"); return; }
        try { startActivity(intent); } catch (ActivityNotFoundException ex) { toast("请手动打开微信"); }
    }

    private void showHuaweiHelp() {
        new AlertDialog.Builder(this).setTitle("华为鸿蒙 4.x 设置")
                .setMessage("① 设置 → 搜索“无障碍” → 已安装的服务／下载的服务 → 自律空间 → 开启。\n\n② 设置 → 搜索“应用启动管理” → 自律空间 → 关闭自动管理 → 允许自启动、允许关联启动、允许后台活动。\n\n③ 如仍被关闭，可在最近任务列表中将本应用锁定。菜单名称以你的手机为准。重启后请回来确认“服务已连接”。")
                .setPositiveButton("知道了", null).show();
    }

    private void showDiagnostics() {
        String version = "未安装或不可读取";
        try { PackageInfo info = getPackageManager().getPackageInfo(PageMatcher.WECHAT, 0); version = info.versionName; }
        catch (Exception ignored) { }
        final String text = "自律空间 0.5.2\nAndroid API：" + android.os.Build.VERSION.SDK_INT
                + "\n微信版本：" + version + "\n服务连接：" + MomentsGuardService.connected
                + "\n系统启动标识：" + store.boot() + "\n范围：" + (store.allMoments() ? "全部" : "个人朋友圈")
                + "\n最近微信窗口类名：" + store.lastPage() + "\n拦截次数：" + store.blockCount()
                + "\n不含消息内容、联系人或截图。";
        new AlertDialog.Builder(this).setTitle("适配信息").setMessage(text)
                .setNegativeButton("关闭", null).setPositiveButton("复制", (d, w) -> {
                    ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("适配信息", text));
                    toast("适配信息已复制");
                }).show();
    }

    private void showPrivacy() {
        new AlertDialog.Builder(this).setTitle("隐私与使用说明")
                .setMessage("计划和提醒可离线使用，无广告与统计。只识别应用标识和页面类名，不读聊天、输入、密码或截图。\n\n你填写的计划、作息、课表与阅读记录默认保存在本机，可在账号与数据页导出。邮箱账号登录和主动云备份会联网；只有确认备份时上传记录。课表图片在本机识别，不上传原图。\n\n提醒由你启用，保留电话、时钟、通话、设置与紧急暂停。作息和完成情况由你确认。\n\n计时中的额度会在重启后结束；暂停余额保留到本周期结束。请保持自动日期。\n\n微信更新后请验证拦截。可随时关闭无障碍服务并卸载。")
                .setPositiveButton("知道了", null).show();
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xFF14263D); bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), 0xFF354D68);
        panel.setBackground(bg); return panel;
    }

    private TextView label(String value, int size, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(INK);
        v.setLineSpacing(dp(4), 1f);
        v.setTypeface(LifeUi.font(this,bold)); v.setIncludeFontPadding(false);
        return v;
    }

    private Button button(String text, Runnable action) {
        return LifeUi.button(this,text,false,action);
    }

    private void add(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); parent.addView(view, p);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    static String duration(long ms) {
        long secs = (ms + 999) / 1000;
        return String.format(Locale.CHINA, "%02d:%02d", secs / 60, secs % 60);
    }
}
