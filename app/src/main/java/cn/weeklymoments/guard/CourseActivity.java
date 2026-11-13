package cn.weeklymoments.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CourseActivity extends Activity {
    private LifeStore store; private LifeData draft; private LinearLayout root;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TimetableOcr.Result recognized; private String recognitionNote="";
    private static final int IMAGE=831;
    @Override public void onCreate(Bundle state){super.onCreate(state);store=LifeStore.get(this);draft=store.copy();list();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private void screen(String title,String subtitle){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root=LifeUi.column(this,false);
        root.setPadding(dp(22),dp(22),dp(22),dp(32));root.setBackgroundColor(LifeUi.BG);scroll.addView(root);setContentView(scroll);
        button(root,"‹  返回作息与计划",false,this::finish);label(root,title,27,true,20);label(root,subtitle,14,false,8);
    }
    private void list(){
        screen("让课表，为你的时间留边界","图片识别只生成候选。确认前，请逐条核对，尤其是跨节课程的起止时间。修改统一确认后才生效。");
        button(root,"选择课表图片并离线识别",true,()->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent,IMAGE);
        });
        button(root,"学校节次时间（图片只标节次时填写）",false,this::periodEditor);
        button(root,"学期第1周周一："+(draft.termStart.isEmpty()?"未设置":draft.termStart),false,()->{
            LocalDate initial=draft.termStart.isEmpty()?LocalDate.now():LocalDate.parse(draft.termStart);
            pickDate(initial,d->{draft.termStart=d.with(java.time.DayOfWeek.MONDAY).toString();list();});
        });
        if(!recognitionNote.isEmpty())label(root,recognitionNote,15,true,16);
        if(recognized!=null)button(root,"查看识别文字",false,()->new AlertDialog.Builder(this).setTitle("本机识别文字")
                .setMessage(recognized.text.isEmpty()?"没有读到清晰文字，请换一张清晰、端正的课表图片。":recognized.text)
                .setPositiveButton("关闭",null).show());
        if(!draft.imageUri.isEmpty())button(root,"参照原课表图片",false,()->{
            worker.execute(()->{try{Bitmap image=TimetableOcr.bitmap(getApplicationContext(),Uri.parse(draft.imageUri));
                runOnUiThread(()->{if(isFinishing()||isDestroyed()){image.recycle();return;}
                    CourseImageView view=new CourseImageView(this,image);view.setMinimumHeight(dp(440));
                    AlertDialog dialog=new AlertDialog.Builder(this).setTitle("双指缩放 · 拖动 · 双击还原").setView(view).setPositiveButton("回到核对列表",null).create();
                    dialog.setOnDismissListener(d->image.recycle());dialog.show();});
            }catch(Exception ex){runOnUiThread(()->toast("无法打开原图，请重新选择课表图片"));}});
        });
        button(root,"手动添加课程／临时加课",false,()->edit(new LifeData.Course(),true));
        for(LifeData.Course course:new ArrayList<>(draft.courses)){
            LinearLayout c=card();label(c,course.name,19,true,0);
            String repeat=course.date.isEmpty()?new String[]{"","周一","周二","周三","周四","周五","周六","周日"}[course.day]:course.date+"（临时）";
            label(c,repeat+"  "+LifeUi.clock(course.start)+"–"+LifeUi.clock(course.end)+
                    (course.parity==1?" · 单周":course.parity==2?" · 双周":"")+"\n第"+course.firstWeek+"–"+course.lastWeek+"周"+
                    (course.skipDates.isEmpty()?"":" · 停课日期 "+course.skipDates),14,false,8);
            button(c,"核对／修改／删除",false,()->edit(course,false));
        }
        CheckBox confirm=new CheckBox(this);confirm.setText("我已逐条核对课程时间；如列表为空，确认当前没有课程。");add(root,confirm,22);
        label(root,"未确认的识别结果不会用于避让课程。确认后会自动避开这些上课时段；已在进行的计划若受影响，会先让你确认调整。",13,false,12);
        button(root,"确认课表并应用修改",true,()->{
            if(!confirm.isChecked()){toast("请先核对并勾选确认");return;}
            for(LifeData.Course c:draft.courses)if(c.date.isEmpty()&&(c.parity!=0||c.firstWeek!=1||c.lastWeek!=52)&&draft.termStart.isEmpty()){toast("使用单双周或特定周次范围前，请先设置学期第1周周一");return;}
            // Keep plan/progress/sleep changes that may have been recorded while this editor was open.
            LifeData next=store.copy();next.courses.clear();next.courses.addAll(draft.courses);next.termStart=draft.termStart;
            next.imageUri=draft.imageUri;next.periodText=draft.periodText;next.coursesConfirmed=true;
            LifeActivity.applySettings(this,store,next,()->{toast("课表已确认");finish();});
        });
    }
    private void periodEditor(){
        EditText input=LifeUi.field(this,"每行：节次 开始-结束\n例如：1 08:00-08:45",draft.periodText,false);input.setMinLines(7);input.setMaxLines(20);
        new AlertDialog.Builder(this).setTitle("学校每节课的真实时间").setView(input)
                .setMessage("请使用学校实际作息。例：1 08:00-08:45。图片自带完整时间段时可以不填。")
                .setNegativeButton("取消",null).setPositiveButton("保留",(d,w)->{
                    String text=input.getText().toString().trim();if(!text.isEmpty()&&TimetableParser.periods(text).isEmpty()){toast("未读到有效节次时间，请检查格式");return;}
                    draft.periodText=text;list();}).show();
    }
    private void edit(LifeData.Course original,boolean fresh){
        screen(fresh?"添加一门课程":"核对课程与调课","课程均可随时修改。临时加课可指定日期；停课可添加要跳过的日期。");
        EditText name=LifeUi.field(this,"课程名称",original.name,false);add(root,name,18);
        Spinner day=spinner(new String[]{"周一","周二","周三","周四","周五","周六","周日"});day.setSelection(Math.max(0,original.day-1));add(root,day,12);
        int[] start={fresh?480:original.start},end={fresh?570:original.end};add(root,timeButton("开始",start),12);add(root,timeButton("结束",end),8);
        Switch one=new Switch(this);one.setText("仅此日期的临时课程");one.setChecked(!original.date.isEmpty());add(root,one,20);
        LocalDate[] date={original.date.isEmpty()?LocalDate.now():LocalDate.parse(original.date)};
        Button dateButton=LifeUi.button(this,"临时日期："+date[0],false,()->{});dateButton.setOnClickListener(v->pickDate(date[0],d->{date[0]=d;dateButton.setText("临时日期："+d);}));add(root,dateButton,8);
        label(root,"正常周课的范围",18,true,20);Spinner parity=spinner(new String[]{"每周","单周","双周"});parity.setSelection(original.parity);add(root,parity,8);
        EditText first=LifeUi.field(this,"开始周次",""+original.firstWeek,true),last=LifeUi.field(this,"结束周次",""+original.lastWeek,true);
        label(root,"开始周次",13,false,12);add(root,first,6);label(root,"结束周次",13,false,12);add(root,last,6);
        Set<String> skips=new TreeSet<>(original.skipDates);TextView skipText=LifeUi.text(this,"停课日期："+skips,14,false);add(root,skipText,16);
        button(root,"添加一个停课日期",false,()->pickDate(LocalDate.now(),d->{skips.add(d.toString());skipText.setText("停课日期："+skips);}));
        button(root,"清空停课日期",false,()->{skips.clear();skipText.setText("停课日期：无");});
        button(root,"保留课程，回到核对列表",true,()->{
            try{
                if(name.getText().toString().trim().isEmpty()||start[0]==end[0])throw new IllegalArgumentException("请填写课程名称和不同的起止时间");
                int a=Integer.parseInt(first.getText().toString()),b=Integer.parseInt(last.getText().toString());if(a<1||b<a||b>104)throw new IllegalArgumentException("请检查课程周次范围");
                LifeData.Course c=new LifeData.Course();c.id=fresh?UUID.randomUUID().toString():original.id;c.name=name.getText().toString().trim();c.day=day.getSelectedItemPosition()+1;
                c.start=start[0];c.end=end[0];c.date=one.isChecked()?date[0].toString():"";c.firstWeek=a;c.lastWeek=b;c.parity=parity.getSelectedItemPosition();c.skipDates.addAll(skips);
                draft.courses.removeIf(item->item.id.equals(c.id));draft.courses.add(c);draft.coursesConfirmed=false;list();
            }catch(Exception ex){toast(ex instanceof NumberFormatException?"请填写有效周次":ex.getMessage());}
        });
        if(!fresh)button(root,"删除此课程",false,()->new AlertDialog.Builder(this).setTitle("从课表移除此课程？").setNegativeButton("保留",null)
                .setPositiveButton("移除",(d,w)->{draft.courses.removeIf(c->c.id.equals(original.id));draft.coursesConfirmed=false;list();}).show());
    }
    @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);
        if(request!=IMAGE||result!=RESULT_OK||intent==null||intent.getData()==null)return;
        Uri uri=intent.getData();try{getContentResolver().takePersistableUriPermission(uri,intent.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
        draft.imageUri=uri.toString();ProgressDialog progress=new ProgressDialog(this);progress.setMessage("正在本机识别中文课表…\n请稍候，图片不会上传。");progress.setCancelable(false);progress.show();
        final List<TimetableParser.Period> periods=TimetableParser.periods(draft.periodText);
        worker.execute(()->{
            try{
                TimetableOcr.Result read=TimetableOcr.recognize(getApplicationContext(),uri);List<LifeData.Course> courses=TimetableParser.proposals(read.text,read.words,periods);
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;progress.dismiss();recognized=read;
                    for(LifeData.Course c:courses){boolean duplicate=false;for(LifeData.Course old:draft.courses)if(old.day==c.day&&old.start==c.start&&old.end==c.end&&old.name.equals(c.name))duplicate=true;if(!duplicate)draft.courses.add(c);}
                    draft.coursesConfirmed=false;recognitionNote=courses.isEmpty()?"读到了图片，但未能可靠对应课程与时间。可查看识别文字，参照原图手动添加；必要时先填写学校节次时间后重新识别。":"已生成 "+courses.size()+" 条候选，尚未应用。请核对课程名称、星期、起止时间及单双周，删除多识别的行。";list();});
            }catch(Throwable ex){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;progress.dismiss();toast("本机识别未完成，请尝试清晰端正的图片，也可手动添加课程");list();});}
        });
    }
    private Button timeButton(String label,int[] time){Button b=LifeUi.button(this,label+"："+LifeUi.clock(time[0]),false,()->{});b.setOnClickListener(v->LifeUi.pickTime(this,label,time[0],minute->{time[0]=minute;b.setText(label+"："+LifeUi.clock(minute));}));return b;}
    private interface DateChoice{void choose(LocalDate date);}
    private void pickDate(LocalDate d,DateChoice choice){new DatePickerDialog(this,(v,y,m,n)->choice.choose(LocalDate.of(y,m+1,n)),d.getYear(),d.getMonthValue()-1,d.getDayOfMonth()).show();}
    private Spinner spinner(String[] values){Spinner v=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);v.setAdapter(a);return v;}
    private void button(LinearLayout p,String s,boolean primary,Runnable r){add(p,LifeUi.button(this,s,primary,r),12);}
    private LinearLayout card(){LinearLayout c=LifeUi.column(this,true);add(root,c,16);return c;}
    private void label(LinearLayout p,String s,int size,boolean bold,int top){add(p,LifeUi.text(this,s,size,bold),top);}
    private void add(LinearLayout p,android.view.View v,int top){LifeUi.add(p,v,top);}
    private int dp(int n){return LifeUi.dp(this,n);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
