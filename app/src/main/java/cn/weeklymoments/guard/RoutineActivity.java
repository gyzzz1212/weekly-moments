package cn.weeklymoments.guard;

import android.app.*;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.util.*;

/** All targets, habits, actuals and reading confirmations are entered explicitly here. */
public final class RoutineActivity extends Activity {
    private LifeStore store;private LinearLayout root;private KnowledgeDeck deck;
    private final ZoneId zone=ZoneId.systemDefault();
    @Override public void onCreate(Bundle state){super.onCreate(state);store=LifeStore.get(this);UiMotion.prepare(this);setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);route(getIntent());}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);route(i);}
    @Override protected void onResume(){super.onResume();LifeScheduler.refresh(this);}
    private void route(Intent i){String section=i.getStringExtra("section"),key=i.getStringExtra("key");
        if("due".equals(section)){next();return;}
        if("week".equals(section)){week(key==null?RoutineEngine.week(LocalDate.now(zone)):key,false);return;}
        if("day".equals(section)){day(key==null?LifeEngine.day(store.data(),now(),zone).toString():key);return;}
        if("learn".equals(section)){learn(key==null?LocalDate.now(zone):LocalDate.parse(key));return;}
        if("settings".equals(section)){settings();return;}
        home();
    }
    private long now(){return System.currentTimeMillis();}
    private void screen(String title,String subtitle){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(LifeUi.BG);root=LifeUi.column(this,false);root.setPadding(dp(22),dp(22),dp(22),dp(32));scroll.addView(root);setContentView(scroll);button(root,"‹  自律空间",false,this::home);label(root,title,27,true,22);label(root,subtitle,14,false,8);}
    private void home(){screen("让每一小步，有方向", "周目标 → 今日重点 → 下一段行动");
        button(root,"返回自律首页",false,this::finish);
        if(!store.error().isEmpty()){label(root,store.error(),16,true,16);return;}
        LifeEngine.Due due=LifeEngine.due(store.data(),now(),zone);if(due.kind!=LifeEngine.Kind.NONE)button(root,due.title(),true,this::next);
        button(root,"本周计划与习惯记录",true,()->week(RoutineEngine.week(LocalDate.now(zone)),false));
        button(root,"今日计划与完成清单",false,()->day(LifeEngine.day(store.data(),now(),zone).toString()));
        button(root,"今天的 3 张 AI 知识卡",false,()->learn(LocalDate.now(zone)));
        RoutineData r=store.data().routine;LocalDate debt=RoutineEngine.debt(r,LocalDate.now(zone));
        if(r.knowledgeEnabled&&debt!=null)button(root,"补读 "+debt+" 的卡片",true,()->learn(debt));
        button(root,"启用功能与音效设置",false,this::settings);
        label(root,"周日开始新一周。周六就寝时，依次复盘当天与本周。跨午夜仍按周六计算。",13,false,20);
        label(root,"习惯和娱乐由你手动记录，不读取或拦截相关应用。",13,false,14);
        for(int i=r.weeks.size()-1;i>=0;i--){RoutineData.Week w=r.weeks.get(i);button(root,"历史周 "+w.key+" · 待办 "+RoutineEngine.percent(w.items)+"%"+(w.reviewedAt>0?" · 已复盘":""),false,()->week(w.key,w.reviewedAt>0));}
    }
    private void settings(){screen("按自己的节奏设置", "可以随时修改；历史记录保留。");RoutineData r=store.data().routine;
        Switch weekly=toggle("启用每周计划与周六复盘",r.weeklyEnabled),daily=toggle("启用每日计划与就寝复盘",r.dailyEnabled),knowledge=toggle("启用每天三张 AI 卡片与次日补读",r.knowledgeEnabled),sound=toggle("展开／收起卡片时播放轻音效",r.soundEnabled);
        label(root,"14 天入门，42 张离线知识卡。涵盖基础、Transformer、多模态与工具；读完顺序复习。",14,false,20);
        label(root,"启用当天开始每天三张。漏读次日先补；暂停不新增，恢复后保留欠读。打勾由你确认。",14,false,14);
        label(root,"提醒方式在作息设置中调整。音效使用媒体音量；静音、振动和通话时不播放。",13,false,14);
        button(root,"试听界面音效",false,()->{if(!sound.isChecked()){toast("请先打开音效开关");return;}UiMotion.preview(this);toast("已试听：若无声，请调高媒体音量，并退出静音或振动模式");});
        button(root,"账号与数据 · 备份恢复",false,()->startActivity(new Intent(this,DataActivity.class)));
        button(root,"保存功能设置",true,()->guarded(()->{LifeData d=store.copy();d.routine.weeklyEnabled=weekly.isChecked();d.routine.dailyEnabled=daily.isChecked();d.routine.soundEnabled=sound.isChecked();RoutineEngine.learning(d.routine,knowledge.isChecked(),LocalDate.now(zone));if(d.enabledAt==0&&RoutineEngine.enabled(d.routine))d.enabledAt=now();store.replace(d);LifeScheduler.refresh(this);next();}));
    }
    private Switch toggle(String text,boolean checked){Switch v=new Switch(this);v.setText(text);v.setTextColor(LifeUi.INK);v.setChecked(checked);add(root,v,18);return v;}
    private static final class ItemRow{EditText name;CheckBox done;LinearLayout view;}
    private List<ItemRow> items(LinearLayout parent,List<RoutineData.Item> original){
        // A dedicated list keeps every new editor above Add and Save, including after deletion.
        LinearLayout list=LifeUi.column(this,false);add(parent,list,0);
        List<ItemRow> rows=new ArrayList<>();for(RoutineData.Item item:original)addItem(list,rows,item);
        if(rows.isEmpty())addItem(list,rows,new RoutineData.Item());
        button(parent,"＋ 添加一项待办",false,()->{
            if(rows.size()>=20){toast("建议把重点控制在20项以内");return;}
            addItem(list,rows,new RoutineData.Item());ItemRow added=rows.get(rows.size()-1);
            added.name.requestFocus();added.view.post(()->added.view.requestRectangleOnScreen(new android.graphics.Rect(0,0,added.view.getWidth(),added.view.getHeight()),false));
        });return rows;
    }
    private void addItem(LinearLayout parent,List<ItemRow> rows,RoutineData.Item value){ItemRow row=new ItemRow();row.view=LifeUi.column(this,true);add(parent,row.view,12);row.name=LifeUi.field(this,"写一个具体、可完成的目标",value.name,false);add(row.view,row.name,0);row.done=new CheckBox(this);row.done.setText("已完成");row.done.setTextColor(LifeUi.INK);row.done.setChecked(value.done);add(row.view,row.done,6);button(row.view,"移除此项",false,()->{rows.remove(row);parent.removeView(row.view);});rows.add(row);}
    private List<RoutineData.Item> readItems(List<ItemRow> rows){List<RoutineData.Item> values=new ArrayList<>();for(ItemRow row:rows){String name=row.name.getText().toString().trim();if(name.isEmpty())continue;if(name.length()>160)throw new IllegalArgumentException("待办请控制在160字以内");RoutineData.Item i=new RoutineData.Item();i.name=name;i.done=row.done.isChecked();values.add(i);}return values;}
    private static final class HabitRow{EditText name,rule,goal;Spinner type;CheckBox[] allowed=new CheckBox[7];final Map<String,EditText> actual=new LinkedHashMap<>();LinearLayout view;}
    private void week(String key,boolean review){LifeData draft=store.copy();RoutineData.Week old=draft.routine.week(key);final RoutineData.Week w=old==null?new RoutineData.Week():old;w.key=key;
        screen(review?"一周的 done list":"本周，想把时间留给什么？",key+" — "+LocalDate.parse(key).plusDays(6)+" · 周日开始");
        CompanionDialogue.add(this,root,review?"把已经做到的事，认真写下来。":"挑几件真正重要的事，给这一周一个方向。",true);
        if(review){label(root,"待办完成 "+RoutineEngine.percent(w.items)+"% · 实际情况由你填写",21,true,18);for(RoutineData.Habit h:w.habits)label(root,h.name+"："+RoutineEngine.habitResult(h,key),14,false,10);}
        label(root,"To-do / Done list",20,true,22);List<ItemRow> rows=items(root,w.items);
        CheckBox rest=new CheckBox(this);rest.setText("这周暂不安排待办（例如休息周）");rest.setTextColor(LifeUi.INK);rest.setChecked(!w.items.isEmpty()?false:old!=null);add(root,rest,12);
        label(root,"习惯与娱乐目标 · 辅助计划表",20,true,22);label(root,"例如：周五刷一次抖音，游戏每天 40 分钟。手动记录；空白不算达标。",13,false,8);
        List<HabitRow> habits=new ArrayList<>();for(RoutineData.Habit h:w.habits)addHabit(root,habits,h,key);
        button(root,"添加习惯／娱乐目标",false,()->{if(habits.size()>=12){toast("每周最多12个辅助目标");return;}addHabit(root,habits,new RoutineData.Habit(),key);});
        EditText reflection=LifeUi.field(this,"这周什么有效？下一周想缩小或调整什么？",w.reflection,false);add(root,reflection,22);
        Runnable save=()->guarded(()->{w.items.clear();w.items.addAll(readItems(rows));if(w.items.isEmpty()&&!rest.isChecked())throw new IllegalArgumentException("请填写至少一项待办，或勾选本周休息");w.habits.clear();for(HabitRow row:habits)w.habits.add(readHabit(row));w.reflection=reflection.getText().toString().trim();if(w.createdAt==0){w.createdAt=now();draft.routine.weeks.add(w);}store.replace(draft);LifeScheduler.refresh(this);toast("周计划与实际记录已保存");next();});
        button(root,"保存周计划与记录",true,save);
        if(now()>=LifeEngine.bedAt(draft,LocalDate.parse(key).plusDays(6),zone))button(root,"保存并确认本周复盘",true,()->guarded(()->{w.reviewedAt=now();save.run();}));
    }
    private void addHabit(LinearLayout parent,List<HabitRow> rows,RoutineData.Habit h,String key){HabitRow row=new HabitRow();row.view=LifeUi.column(this,true);add(parent,row.view,16);row.name=LifeUi.field(this,"习惯／活动名称",h.name,false);row.rule=LifeUi.field(this,"补充规则，例如只在周五刷一次",h.rule,false);row.goal=LifeUi.field(this,"次数或分钟",String.valueOf(h.goal),true);row.type=spinner(new String[]{"每周至少达到 · 次数","每天不超过 · 分钟","每周不超过 · 次数"});row.type.setSelection(Math.max(0,Math.min(2,h.type)));add(row.view,row.name,0);add(row.view,row.rule,8);add(row.view,row.type,12);add(row.view,row.goal,8);label(row.view,"允许的星期（不勾选的日子，实际值应为0）",13,false,12);
        String[] names={"一","二","三","四","五","六","日"};LinearLayout days=new LinearLayout(this);for(int i=0;i<7;i++){final int index=i;CheckBox c=new CheckBox(this);c.setText(names[i]);c.setTextSize(11);c.setTextColor(LifeUi.INK);c.setMinWidth(0);c.setPadding(0,0,0,0);c.setChecked((h.allowedDays&(1<<i))!=0);row.allowed[index]=c;days.addView(c,new LinearLayout.LayoutParams(0,-2,1));}add(row.view,days,8);
        label(row.view,"实际记录（次数／分钟；空白表示还没记录，0也要手动填写）",13,false,14);LocalDate first=LocalDate.parse(key),today=LocalDate.now(zone);
        for(int i=0;i<7;i++){String date=first.plusDays(i).toString();if(first.plusDays(i).isAfter(today))continue;EditText actual=LifeUi.field(this,date+" 实际",h.actual.containsKey(date)?String.valueOf(h.actual.get(date)):"",true);label(row.view,date,12,false,8);add(row.view,actual,4);row.actual.put(date,actual);}
        button(row.view,"移除此辅助目标",false,()->{rows.remove(row);parent.removeView(row.view);});rows.add(row);
    }
    private RoutineData.Habit readHabit(HabitRow row){RoutineData.Habit h=new RoutineData.Habit();h.name=row.name.getText().toString().trim();h.rule=row.rule.getText().toString().trim();if(h.name.isEmpty())throw new IllegalArgumentException("请填写辅助目标名称，或删除空白目标");h.type=row.type.getSelectedItemPosition();h.goal=Integer.parseInt(row.goal.getText().toString());if(h.goal<0||h.goal>10080)throw new IllegalArgumentException("目标请填0至10080之间的数值");h.allowedDays=0;for(int i=0;i<7;i++)if(row.allowed[i].isChecked())h.allowedDays|=1<<i;for(String date:row.actual.keySet()){String text=row.actual.get(date).getText().toString().trim();if(text.isEmpty())continue;int value=Integer.parseInt(text);if(value<0||value>10080)throw new IllegalArgumentException("实际值请填0至10080之间");h.actual.put(date,value);}return h;}
    private void day(String key){LifeData draft=store.copy();RoutineData.Day existing=draft.routine.day(key);final RoutineData.Day d=existing==null?new RoutineData.Day():existing;d.key=key;
        screen("今日重点与完成清单",key+" · 周目标在下面，随时参考");CompanionDialogue.add(this,root,"今天做到哪几件事，就会让你觉得值得？",true);reference(this,root,draft.routine,LocalDate.parse(key),false);
        label(root,"今天的 To-do / Done list",20,true,22);List<ItemRow> rows=items(root,d.items);CheckBox rest=new CheckBox(this);rest.setText("今天不安排待办（休息／机动日）");rest.setTextColor(LifeUi.INK);rest.setChecked(existing!=null&&d.items.isEmpty());add(root,rest,12);
        button(root,"保存今日计划与完成情况",true,()->guarded(()->{d.items.clear();d.items.addAll(readItems(rows));if(d.items.isEmpty()&&!rest.isChecked())throw new IllegalArgumentException("请至少填写一项，或确认今天休息");if(d.createdAt==0){d.createdAt=now();draft.routine.days.add(d);}store.replace(draft);LifeScheduler.refresh(this);next();}));
    }
    static void reference(Activity a,LinearLayout root,RoutineData r,LocalDate day,boolean daily){LinearLayout card=LifeUi.column(a,true);LifeUi.add(root,card,18);LifeUi.add(card,LifeUi.text(a,daily?"今日计划 · 同步参考":"周计划 · 同步参考",18,true),0);StringBuilder text=new StringBuilder();
        if(daily){RoutineData.Day d=r.day(day.toString());if(d!=null)for(RoutineData.Item i:d.items)text.append(i.done?"☑ ":"□ ").append(i.name).append('\n');}
        else{RoutineData.Week w=r.week(RoutineEngine.week(day));if(w!=null){for(RoutineData.Item i:w.items)text.append(i.done?"☑ ":"□ ").append(i.name).append('\n');for(RoutineData.Habit h:w.habits)text.append("◇ ").append(h.name).append(" · ").append(h.rule).append('\n');}}
        TextView content=LifeUi.text(a,text.length()==0?"暂未填写，可先保存上一级计划。":text.toString().trim(),14,false);LifeUi.add(card,content,12);LifeUi.add(card,LifeUi.button(a,"展开／收起参考",false,()->UiMotion.toggle(content)),8);
    }
    private void learn(LocalDate date){if(deck==null)deck=new KnowledgeDeck(this);RoutineData r=store.data().routine;screen("AI 探索 · 每天三小步",date+" · 第 "+RoutineEngine.cycle(r,date,deck.cards.length())+" 轮 · 可展开阅读");
        if(!RoutineEngine.learningDay(r,date)){label(root,"这一天没有投放。先在功能设置中启用知识卡片，从启用当天开始，每天三张。",16,false,20);button(root,"前往功能设置",true,this::settings);return;}
        CompanionDialogue.add(this,root,"不用一次懂完，带着一个问题往前走。",true);
        for(int slot=0;slot<3;slot++){final int s=slot;final String readKey=date+":"+slot;JSONObject entry=deck.card(RoutineEngine.cardIndex(r,date,slot,deck.cards.length()));LinearLayout card=LifeUi.column(this,true);add(root,card,18);label(card,(r.readCards.contains(readKey)?"✓ ":"0"+(slot+1)+"  ")+entry.optString("title"),20,true,0);label(card,entry.optString("chapter"),12,false,8);
            LinearLayout article=LifeUi.column(this,false);article.setVisibility(View.GONE);button(card,"展开／收起知识卡",false,()->UiMotion.toggle(article));add(card,article,10);
            add(article,new ConceptDiagramView(this,entry.optString("diagramType"),entry.optJSONArray("diagram")),0);DialogueTextView body=new DialogueTextView(this,entry.optString("body"),17);add(article,body,18);TextView reveal=LifeUi.text(this,"轻点正文 · 立即显示全文",11,false);add(article,reveal,8);body.bindHint(reveal);label(article,"想一想\n"+entry.optString("question"),15,true,20);
            JSONObject source=deck.source(entry);label(article,"延伸阅读："+source.optString("title")+"\n短文和示意图为原创入门解释，不是课程原文。",12,false,18);
            button(article,"在浏览器查看延伸资料",false,()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(source.optString("url"))));}catch(Exception ex){toast("没有可打开链接的浏览器");}});
            CheckBox read=new CheckBox(this);read.setText("我已读完这张卡片");read.setTextColor(LifeUi.INK);read.setChecked(r.readCards.contains(readKey));add(article,read,18);
            read.setOnCheckedChangeListener((v,checked)->guarded(()->{LifeData next=store.copy();if(checked)next.routine.readCards.add(readKey);else next.routine.readCards.remove(readKey);store.replace(next);LifeScheduler.refresh(this);}));
        }
        button(root,"读完后继续",true,this::next);label(root,"读完请主动打勾。漏读从最早日期补起，42 张后顺序复习。",12,false,18);
    }
    private void next(){LifeEngine.Due d=LifeEngine.due(store.data(),now(),zone);switch(d.kind){case WEEK_PLAN:week(d.day,false);break;case DAY_PLAN:day(d.day);break;case WEEK_REVIEW:week(d.day,true);break;case LEARN:learn(LocalDate.parse(d.day));break;case NONE:home();break;default:startActivity(new Intent(this,LifeActivity.class).putExtra("section","due").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();}}
    private Spinner spinner(String[] values){Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);return s;}
    private void add(LinearLayout p,View v,int margin){LifeUi.add(p,v,margin);}
    private void label(LinearLayout p,String text,int size,boolean bold,int margin){add(p,LifeUi.text(this,text,size,bold),margin);}
    private void button(LinearLayout p,String text,boolean primary,Runnable action){add(p,LifeUi.button(this,text,primary,action),12);}
    private int dp(int n){return LifeUi.dp(this,n);}
    private void guarded(Runnable action){try{action.run();}catch(RuntimeException e){toast(e instanceof NumberFormatException?"请填写有效的整数":e.getMessage()==null?"保存未完成，请重试":e.getMessage());}}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
}
