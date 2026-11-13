package cn.weeklymoments.guard;

import android.app.*;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

/** Email account, encrypted login session, explicit cloud snapshots and preview-before-restore. */
public final class AccountActivity extends Activity {
    private AccountClient client;private LifeStore store;private LinearLayout root,backupList;private TextView status;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private boolean busy;
    private String pendingEmail="";
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=LifeStore.get(this);
        try(InputStream in=getAssets().open("cloud/config.json")){
            JSONObject config=new JSONObject(new String(LifeStore.readBytes(in),StandardCharsets.UTF_8));
            client=new AccountClient(new CloudConfig(config.optString("url"),config.optString("publishableKey")),new CloudHttpTransport(),new AccountVault(this));
            if(client.signedIn()){account();listBackups();}else form("login");
        }catch(Exception e){screen("账号暂不可用","本机计划仍可以正常使用。");text(root,"账号配置或登录存储未能读取，请联系维护者检查。",15,false);}
    }
    private void screen(String title,String subtitle){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(LifeUi.BG);
        root=LifeUi.column(this,false);int p=LifeUi.dp(this,22);root.setPadding(p,p,p,p);scroll.addView(root);setContentView(scroll);
        button(root,"‹  返回",false,this::finish);text(root,"ACCOUNT / 自律空间",11,true);text(root,title,28,true);text(root,subtitle,14,false);
        status=LifeUi.text(this,"",13,false);status.setTextColor(LifeUi.MUTED);LifeUi.add(root,status,14);
        if(client!=null&&!client.ready()){LinearLayout note=LifeUi.column(this,true);LifeUi.add(root,note,14);
            text(note,"账号服务正在配置",18,true);text(note,"邮箱账号与云备份尚未启用。下面的入口会在服务配置完成后开放；当前不会发送邮箱、密码或计划记录。",13,false);}
    }
    private EditText field(LinearLayout p,String hint,boolean password){
        EditText e=LifeUi.field(this,hint,"",false);e.setSingleLine(true);e.setSaveEnabled(false);
        e.setInputType(password?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        LifeUi.add(p,e,12);return e;
    }
    private void form(String mode){
        boolean register="register".equals(mode),verify="verify".equals(mode),recovery="recovery".equals(mode);
        screen(register?"创建你的账号":verify?"验证邮箱":recovery?"找回密码":"登录自律空间",
            "登录后备份计划与作息，换机时从自己的账号恢复。");
        LinearLayout panel=LifeUi.column(this,true);LifeUi.add(root,panel,12);
        EditText email=field(panel,"邮箱地址",false);email.setText(pendingEmail);
        EditText password=verify?null:field(panel,recovery?"新密码（8–128 位）":"密码（注册需 8–128 位）",true);
        EditText repeat=register||recovery?field(panel,"再次输入密码",true):null;
        EditText code=verify||recovery?field(panel,"邮件中的验证码",false):null;
        if(code!=null){code.setInputType(InputType.TYPE_CLASS_NUMBER);code.setContentDescription("邮件中的验证码");}
        if(recovery)button(panel,"发送找回密码邮件",false,()->{String address=email.getText().toString().trim();run("正在发送邮件…",()->{client.recover(address);ui(()->status.setText("如邮箱可以接收找回邮件，将收到验证码。请查看收件箱与垃圾邮件。"));});});
        Button submit=LifeUi.button(this,register?"注册账号":verify?"验证并登录":recovery?"更新密码":"登录",true,()->{
            String address=email.getText().toString().trim(),secret=password==null?"":password.getText().toString(),confirmation=repeat==null?"":repeat.getText().toString(),otp=code==null?"":code.getText().toString().trim();
            if((register||recovery)&&!secret.equals(confirmation)){status.setText("两次输入的密码不一致");return;}
            if(password!=null)password.setText("");if(repeat!=null)repeat.setText("");
            run(register?"正在创建账号…":verify?"正在验证邮箱…":recovery?"正在更新密码…":"正在登录…",()->{
                if(register){boolean signed=client.signUp(address,secret);ui(()->{pendingEmail=address;if(!signed){form("verify");status.setText("请查看验证邮件。已验证过的邮箱可以直接返回登录。");}});if(signed)openedAccount();}
                else if(verify){client.verifySignup(address,otp);openedAccount();}
                else if(recovery){boolean revoked=client.resetPassword(address,otp,secret);ui(()->{pendingEmail=address;form("login");status.setText(revoked?"密码已更新，请使用新密码登录。":"密码已更新，请使用新密码登录；云端其他会话撤销未确认，请在服务控制台核对。");});}
                else{client.signIn(address,secret);openedAccount();}
            });
        });LifeUi.add(panel,submit,18);submit.setEnabled(client.ready());
        if(verify)button(panel,"重新发送验证邮件",false,()->{String address=email.getText().toString().trim();run("正在发送…",()->{client.resend(address);ui(()->status.setText("验证邮件已请求发送，请稍后查看邮箱。"));});});
        if(!register&&!verify&&!recovery)button(root,"没有账号？注册",false,()->{pendingEmail=email.getText().toString().trim();form("register");});
        if(register||verify||recovery)button(root,"返回登录",false,()->{pendingEmail=email.getText().toString().trim();form("login");});
        else button(root,"忘记密码",false,()->{pendingEmail=email.getText().toString().trim();form("recovery");});
        text(root,"邮箱用于账号验证和找回密码。只有主动备份时，计划、作息、课程文字和阅读进度才会上传；不会上传课表原图、聊天或手机权限。",12,false);
    }
    private void openedAccount()throws Exception{
        ui(this::account);JSONArray rows=client.backups();ui(()->{renderBackups(rows);status.setText(rows.length()==0?"登录成功，云端还没有备份。":"登录成功，已读取自己的云端备份。");});
    }
    private void account(){
        screen("你的账号",client.email());
        LinearLayout card=LifeUi.column(this,true);LifeUi.add(root,card,12);text(card,"云端备份",20,true);
        text(card,"备份保留最近 7 份。登录不会替换本机记录；恢复前可先查看内容。",14,false);
        button(card,"备份现在的计划与作息",true,()->{
            if(!store.error().isEmpty()){status.setText("本机记录读取异常，请先从“账号与数据”导出原始文件。");return;}
            final LifeData snapshot=store.copy();snapshot.imageUri="";
            new AlertDialog.Builder(this).setTitle("备份到当前账号？").setMessage(client.email()+"\n\n"+BackupCodec.summary(snapshot)+"\n\n将上传这份快照，保留最近 7 份。课表原图、权限与朋友圈额度不上传。")
                .setNegativeButton("取消",null).setPositiveButton("备份",(d,w)->run("正在上传备份…",()->{
                    client.saveBackup(BackupCodec.encode(snapshot,System.currentTimeMillis()).toString(),"0.7.1");
                    JSONArray rows=client.backups();ui(()->{renderBackups(rows);status.setText("云端备份已保存，可以在同一账号的另一台设备恢复。");});
                })).show();
        });
        button(card,"刷新云端备份",false,this::listBackups);
        backupList=LifeUi.column(this,false);LifeUi.add(root,backupList,12);
        button(root,"退出登录",false,()->run("正在退出…",()->{boolean revoked=client.signOut();ui(()->{form("login");status.setText(revoked?"已退出，本机计划继续保留。":"本机已退出；暂未连接云端撤销会话，请联网后在账号服务中核对设备会话。");});}));
        text(root,"退出或切换账号不会自动删除、上传或替换本机计划。",12,false);
    }
    private void listBackups(){if(busy)return;run("正在读取云端备份…",()->{JSONArray rows=client.backups();ui(()->{renderBackups(rows);status.setText(rows.length()==0?"云端还没有备份。":"已读取当前账号的云端备份。");});});}
    private void renderBackups(JSONArray rows){
        if(backupList==null)return;backupList.removeAllViews();text(backupList,"最近的云端备份",18,true);
        if(rows.length()==0)text(backupList,"还没有备份，点击上方按钮保存第一份。",14,false);
        for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;String id=row.optString("id"),date=row.optString("created_at");
            try{date=DateTimeFormatter.ofPattern("M月d日 HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(date));}catch(Exception ignored){}
            button(backupList,date+" · 查看并恢复",false,()->preview(id));
        }
    }
    private void preview(String id){
        run("正在检查备份…",()->{JSONObject row=client.backup(id);LifeData candidate=BackupCodec.decode(row.getString("payload"));candidate.imageUri="";
            ui(()->new AlertDialog.Builder(this).setTitle("恢复这份云端备份？").setMessage(client.email()+"\n\n"+BackupCodec.summary(candidate)+"\n\n将替换本机计划和作息。确认恢复前会保留本机历史副本；课表原图需重新选择。")
                .setNegativeButton("取消",null).setPositiveButton("确认恢复",(d,w)->run("正在恢复…",()->{store.restore(candidate);ui(()->{LifeScheduler.refresh(this);status.setText("恢复完成，原记录已保留为本机历史副本。");});})).show());
        });
    }
    private interface Job{void perform()throws Exception;}
    private void run(String message,Job job){
        if(busy)return;if(!client.ready()){status.setText("账号服务尚未完成配置，当前不会发送邮箱或密码。");return;}
        busy=true;status.setText(message);enable(root,false);
        worker.execute(()->{try{job.perform();}catch(Exception e){ui(()->{if(!client.signedIn()&&backupList!=null){backupList=null;form("login");}status.setText(e instanceof AccountClient.Failure||e instanceof IllegalArgumentException||e instanceof IOException?e.getMessage():"操作未完成，请稍后重试；本机记录保留。");});}
            finally{ui(()->{busy=false;enable(root,true);});}});
    }
    private void enable(View view,boolean enabled){if(view instanceof Button||view instanceof EditText)view.setEnabled(enabled);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)enable(group.getChildAt(i),enabled);}}
    private void ui(Runnable action){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())action.run();});}
    private void text(LinearLayout parent,String words,int size,boolean bold){TextView v=LifeUi.text(this,words,size,bold);if(!bold)v.setTextColor(LifeUi.MUTED);LifeUi.add(parent,v,12);}
    private void button(LinearLayout p,String title,boolean primary,Runnable action){LifeUi.add(p,LifeUi.button(this,title,primary,action),12);}
    @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
}
