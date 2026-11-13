package cn.weeklymoments.guard;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class LifePromptView extends FrameLayout {
    final String key;
    LifePromptView(Context context, LifeEngine.Due due, Runnable open, Runnable emergency, Runnable phone, Runnable clock) {
        super(context); key = due.key;
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF091426, 0xFF17283F}));
        setOnApplyWindowInsetsListener((v, insets) -> {
            setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets();
        });
        LinearLayout shell=LifeUi.column(context,false);addView(shell,new LayoutParams(-1,-1));
        ScrollView scroll = new ScrollView(context); scroll.setFillViewport(true);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout outer = LifeUi.column(context, false); outer.setGravity(Gravity.CENTER);
        outer.setPadding(LifeUi.dp(context, 24), LifeUi.dp(context, 32), LifeUi.dp(context, 24), LifeUi.dp(context, 32));
        scroll.addView(outer, new ScrollView.LayoutParams(-1, -2));
        TextView brand = LifeUi.text(context, "为自己留白  /  小步向前", 13, false); brand.setGravity(Gravity.CENTER);
        LifeUi.add(outer, brand, 0);
        LinearLayout card = LifeUi.column(context, false); LifeUi.add(outer, card, 28);
        LifeUi.add(card, LifeUi.text(context, due.kind == LifeEngine.Kind.REVIEW ? "这一段，走到了哪里？" : due.title(), 25, true), 0);
        String message;
        switch (due.kind) {
            case WEEK_PLAN: message = "周计划是这一周的方向。写下待办和习惯目标，娱乐活动只记录实际完成情况。"; break;
            case DAY_PLAN: message = "把周目标放在手边，选出今天真正重要的几件事。"; break;
            case WEEK_REVIEW: message = "看看做到了什么，也看看哪些目标需要缩小。没有填写的实际情况会单独列出。"; break;
            case LEARN: message = "之前还有未读的 AI 知识卡。先补完短文和示意图，再继续使用普通应用。电话、时钟和紧急退出始终可用。"; break;
            case PLAN: message = "当前是你设定的课外计划时段。先选 30／60／90／120 分钟，写下具体任务，再开始使用手机。"; break;
            case REVIEW: message = "不用做到完美，认真记录就好。逐项填写完成比例，看看什么帮到了你，再安排下一段。"; break;
            case WAKE: message = "先记录你确认起床的时间，再慢慢开始今天。可以补记真实时间，也可以修正。"; break;
            case BED: message = "今天先到这里。记录准备睡觉的时间，把休息也放进自己的目标里。"; break;
            default: message = "看一看今天的计划完成率、漏填记录和本地改进建议，再为今天收个尾。";
        }
        CompanionDialogue.add(context,card,message,true);
        LinearLayout controls=LifeUi.column(context,false);
        controls.setPadding(LifeUi.dp(context,20),LifeUi.dp(context,8),LifeUi.dp(context,20),LifeUi.dp(context,16));
        shell.addView(controls,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout safety=new LinearLayout(context);
        safety.addView(LifeUi.button(context,"电话",false,phone),new LinearLayout.LayoutParams(0,-2,1));
        safety.addView(LifeUi.button(context,"时钟",false,clock),new LinearLayout.LayoutParams(0,-2,1));
        LifeUi.add(controls,safety,0);
        LifeUi.add(controls, LifeUi.button(context, "去记录／完成跟进", true, open), 12);
        LifeUi.add(controls, LifeUi.button(context, "紧急使用 · 暂停提醒 15 分钟", false, emergency), 10);
        LifeUi.add(outer, LifeUi.text(context, "电话、时钟、通话和系统设置始终保留。暂停提醒时，已开始的计划仍继续计时。", 12, false), 18);
    }
}
