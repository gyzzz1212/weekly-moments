package cn.weeklymoments.guard;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Local recovery is usable independently of any future account provider. */
public final class DataActivity extends Activity {
    private static final int EXPORT=920,IMPORT=921;
    private LifeStore store;private LinearLayout root;private TextView status;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state){super.onCreate(state);store=LifeStore.get(this);show();}
    private void show(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(LifeUi.BG);
        root=LifeUi.column(this,false);int pad=LifeUi.dp(this,22);root.setPadding(pad,pad,pad,pad);scroll.addView(root);setContentView(scroll);
        button(root,"‹  返回",false,this::finish);text(root,"账号与数据",27,true);
        text(root,"计划值得保留，不必重新开始。",14,false);
        LinearLayout account=LifeUi.column(this,true);LifeUi.add(root,account,20);
        text(account,"自律空间账号",20,true);text(account,"邮箱注册和登录，备份计划与作息，换机时从同一账号恢复。",14,false);
        button(account,"进入账号 · 登录与云备份",true,()->startActivity(new Intent(this,AccountActivity.class)));
        LinearLayout backup=LifeUi.column(this,true);LifeUi.add(root,backup,22);
        text(backup,"备份与恢复",19,true);text(backup,BackupCodec.summary(store.data()),14,false);
        status=LifeUi.text(this,"记录在本机。导出一份，可在重装或换机后恢复。",13,false);status.setTextColor(LifeUi.MUTED);LifeUi.add(backup,status,12);
        if(!store.error().isEmpty())text(backup,store.error(),14,false);
        if(!store.historyWarning().isEmpty())text(backup,store.historyWarning(),14,false);
        button(backup,"备份到文件／已登录的网盘",true,this::export);
        button(backup,"从备份文件恢复",false,()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE),IMPORT));
        text(backup,"包含计划、作息、课程和阅读进度。课表原图需重新选择；权限与朋友圈额度不在备份内。",12,false);
        LinearLayout local=LifeUi.column(this,true);LifeUi.add(root,local,18);text(local,"本机历史副本",19,true);
        text(local,"保存前自动留存最近 7 份；升级前另留副本。卸载或清除数据会删除这些副本。",12,false);
        List<File> files=store.history();
        if(files.isEmpty())text(local,"首次保存后，会在这里出现。",13,false);
        for(File file:files){String name=(file.getName().startsWith("upgrade-")?"升级前 · ":"保存前 · ")+new SimpleDateFormat("M月d日 HH:mm:ss",Locale.CHINA).format(new Date(file.lastModified()));
            button(local,name,false,()->load(()->new FileInputStream(file)));}

    }
    private void export(){
        try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"自律空间备份-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.CHINA).format(new Date())+".json"),EXPORT);}
        catch(android.content.ActivityNotFoundException e){status.setText("没有可用的文件选择器，请先启用系统文件管理。");}
    }
    private interface Input {InputStream open()throws Exception;}
    private void load(Input source){
        status.setText("正在检查备份…");
        worker.execute(()->{try(InputStream input=source.open()){
            if(input==null)throw new IOException("无法打开备份");
            LifeData candidate=BackupCodec.decode(new String(LifeStore.readBytes(input),StandardCharsets.UTF_8));
            ui(()->{status.setText("备份检查通过，尚未修改当前记录。");
                new AlertDialog.Builder(this).setTitle("恢复这份备份？").setMessage(BackupCodec.summary(candidate)+"\n\n会替换当前计划与作息。恢复前，先保留当前记录的本机副本。")
                    .setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->restore(candidate)).show();});
        }catch(Exception e){ui(()->status.setText("备份无法恢复：文件可能不完整，或版本不兼容。当前记录未改动。"));}});
    }
    private void restore(LifeData candidate){status.setText("正在恢复…");worker.execute(()->{
        try{store.restore(candidate);ui(()->{LifeScheduler.refresh(this);show();Toast.makeText(this,"已恢复；原记录保留在本机历史副本中",Toast.LENGTH_LONG).show();});}
        catch(Exception e){ui(()->status.setText("恢复失败，旧记录已保留。请检查剩余空间。"));}
    });}
    @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);
        if(result!=RESULT_OK||intent==null||intent.getData()==null)return;
        android.net.Uri uri=intent.getData();
        if(request==IMPORT){load(()->getContentResolver().openInputStream(uri));return;}
        if(request==EXPORT){status.setText("正在导出备份…");worker.execute(()->{
            try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                if(out==null)throw new IOException("无法保存");store.writeBackup(out);
                ui(()->status.setText(store.error().isEmpty()?"备份已写入所选位置；若选择云盘，请到网盘确认上传完成。":"已导出原始记录，请保留文件用于排查。"));
            }catch(Exception e){ui(()->status.setText("备份未保存成功，请换个位置重试。"));}
        });}
    }
    private void ui(Runnable action){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())action.run();});}
    private void text(LinearLayout p,String value,int size,boolean bold){TextView text=LifeUi.text(this,value,size,bold);if(!bold)text.setTextColor(LifeUi.MUTED);LifeUi.add(p,text,12);}
    private void button(LinearLayout p,String title,boolean primary,Runnable action){LifeUi.add(p,LifeUi.button(this,title,primary,action),12);}
    @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
}
