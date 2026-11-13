package cn.weeklymoments.guard;

import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Provider-backed auth and per-user backups. No local fake accounts or password storage. */
final class AccountClient {
    interface Transport {Reply send(String method,String url,Map<String,String> headers,String body)throws Exception;}
    interface Vault {JSONObject load()throws Exception;void save(JSONObject value)throws Exception;void clear()throws Exception;}
    static final class Reply {final int status;final String body;Reply(int status,String body){this.status=status;this.body=body;}}
    static final class Failure extends Exception {
        final int status;final String code;
        Failure(int status,String code,String message){super(message);this.status=status;this.code=code;}
    }
    private final CloudConfig config;private final Transport transport;private final Vault vault;
    private JSONObject session;
    AccountClient(CloudConfig config,Transport transport,Vault vault)throws Exception{
        this.config=config;this.transport=transport;this.vault=vault;
        if(config.ready())config.validate();session=vault.load();
        if(session!=null&&(!config.origin.equals(session.optString("origin"))||!validSession(session))){vault.clear();session=null;}
    }
    boolean ready(){return config.ready();}
    boolean signedIn(){return session!=null;}
    String email(){return session==null?"":session.optJSONObject("user").optString("email");}
    String userId(){return session==null?"":session.optJSONObject("user").optString("id");}
    static void email(String value){if(value==null||value.length()>254||!value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new IllegalArgumentException("请填写有效邮箱");}
    static void password(String value){if(value==null||value.length()<8||value.length()>128)throw new IllegalArgumentException("密码请使用 8 至 128 个字符");}
    static String sha256(String value)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:digest)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private static boolean validSession(JSONObject s){JSONObject user=s.optJSONObject("user");return !s.optString("access_token").isEmpty()&&!s.optString("refresh_token").isEmpty()&&user!=null&&!user.optString("id").isEmpty()&&!user.optString("email").isEmpty();}
    private void retain(JSONObject response)throws Exception{
        if(!validSession(response))throw new Failure(0,"invalid_session","账号服务返回了不完整的登录信息，请重试");
        JSONObject next=new JSONObject(response.toString());next.put("origin",config.origin);
        next.put("expires_at_ms",System.currentTimeMillis()+Math.max(0,response.optLong("expires_in",3600))*1000L);
        vault.save(next);session=next;
    }
    void signIn(String email,String password)throws Exception{
        email(email);if(password==null||password.isEmpty())throw new IllegalArgumentException("请输入密码");
        retain(object(send("POST","/auth/v1/token?grant_type=password",new JSONObject().put("email",email).put("password",password).toString(),null)));
    }
    boolean signUp(String email,String password)throws Exception{
        email(email);password(password);JSONObject result=object(send("POST","/auth/v1/signup",new JSONObject().put("email",email).put("password",password).toString(),null));
        if(validSession(result)){retain(result);return true;}return false;
    }
    void verifySignup(String email,String code)throws Exception{email(email);code(code);retain(object(send("POST","/auth/v1/verify",new JSONObject().put("email",email).put("token",code).put("type","signup").toString(),null)));}
    void resend(String email)throws Exception{email(email);send("POST","/auth/v1/resend",new JSONObject().put("email",email).put("type","signup").toString(),null);}
    void recover(String email)throws Exception{email(email);send("POST","/auth/v1/recover",new JSONObject().put("email",email).toString(),null);}
    boolean resetPassword(String email,String code,String next)throws Exception{
        email(email);code(code);password(next);
        JSONObject verified=object(send("POST","/auth/v1/verify",new JSONObject().put("email",email).put("token",code).put("type","recovery").toString(),null));
        if(!validSession(verified))throw new Failure(0,"invalid_session","邮箱验证失败，请重新获取验证码");
        String token=verified.getString("access_token");send("PUT","/auth/v1/user",new JSONObject().put("password",next).toString(),token);
        boolean revoked=true;try{send("POST","/auth/v1/logout?scope=global","{}",token);}catch(Exception e){revoked=false;}
        finally{vault.clear();session=null;}return revoked;
    }
    private static void code(String value){if(value==null||!value.matches("[0-9]{6,10}"))throw new IllegalArgumentException("请填写邮件中的验证码");}
    private String access()throws Exception{
        if(session==null)throw new Failure(401,"not_signed_in","请先登录账号");
        if(session.optLong("expires_at_ms")<=System.currentTimeMillis()+60000){
            try{retain(object(send("POST","/auth/v1/token?grant_type=refresh_token",new JSONObject().put("refresh_token",session.getString("refresh_token")).toString(),null)));}
            catch(Failure e){if(e.status==400||e.status==401){vault.clear();session=null;throw new Failure(401,"session_expired","登录已过期，请重新登录；本机记录仍然保留");}throw e;}
        }return session.getString("access_token");
    }
    private String authenticated(String method,String path,String body)throws Exception{
        String token=access();
        try{return send(method,path,body,token);}catch(Failure e){
            if(e.status!=401)throw e;session.put("expires_at_ms",0);return send(method,path,body,access());
        }
    }
    JSONArray backups()throws Exception{return new JSONArray(authenticated("GET","/rest/v1/focus_backups?select=id,created_at,sha256,client_version&order=created_at.desc,id.desc&limit=7",null));}
    JSONObject backup(String id)throws Exception{
        uuid(id);JSONArray rows=new JSONArray(authenticated("GET","/rest/v1/focus_backups?select=id,created_at,payload,sha256&id=eq."+id+"&limit=1",null));
        if(rows.length()!=1)throw new Failure(404,"backup_missing","这份备份不存在，或不属于当前账号");
        JSONObject row=rows.getJSONObject(0);String payload=row.getString("payload");
        if(!sha256(payload).equals(row.getString("sha256")))throw new Failure(0,"backup_damaged","备份校验失败，当前记录未改动");return row;
    }
    void saveBackup(String payload,String version)throws Exception{
        if(payload.getBytes(StandardCharsets.UTF_8).length>16*1024*1024)throw new IllegalArgumentException("备份超过 16 MB，请先导出文件保留");
        JSONObject root=new JSONObject(payload);if(!"focus-space-backup".equals(root.optString("format")))throw new IllegalArgumentException("备份格式无效");
        authenticated("POST","/rest/v1/rpc/focus_save_backup",new JSONObject().put("p_payload",payload).put("p_sha256",sha256(payload)).put("p_version",version).toString());
    }
    /** Returns false on offline revocation failure, but always forgets this device's encrypted credentials. */
    boolean signOut()throws Exception{
        boolean revoked=true;try{if(session!=null)send("POST","/auth/v1/logout?scope=local","{}",access());}catch(Exception e){revoked=false;}
        finally{vault.clear();session=null;}return revoked;
    }
    private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException();}catch(Exception e){throw new IllegalArgumentException("备份编号无效");}}
    private static JSONObject object(String value)throws Exception{return new JSONObject(value);}
    private String send(String method,String path,String body,String bearer)throws Exception{
        if(!config.ready())throw new Failure(0,"not_configured","账号服务尚未完成配置，当前不会发送邮箱或密码");
        Map<String,String> headers=new LinkedHashMap<>();headers.put("apikey",config.key);headers.put("Content-Type","application/json");headers.put("Accept","application/json");
        if(bearer!=null)headers.put("Authorization","Bearer "+bearer);
        Reply r=transport.send(method,config.origin+path,headers,body);
        if(r.status>=200&&r.status<300)return r.body.isEmpty()?"{}":r.body;
        String code="";try{code=new JSONObject(r.body).optString("error_code",new JSONObject(r.body).optString("code"));}catch(Exception ignored){}
        String message;
        if(r.status==429)message="操作较频繁，请稍后再试；邮箱服务也可能达到发送上限";
        else if("email_not_confirmed".equals(code))message="邮箱还未验证，请输入邮件中的验证码后再登录";
        else if("invalid_credentials".equals(code)||r.status==401)message="邮箱或密码不正确，或登录已过期";
        else if("user_already_exists".equals(code))message="请尝试登录或找回密码";
        else if("otp_expired".equals(code))message="验证码已失效，请重新获取";
        else if("weak_password".equals(code))message="密码未达到账号服务要求，请换一个更长的密码";
        else if("email_address_not_authorized".equals(code)||"over_email_send_rate_limit".equals(code))message="邮件服务尚未准备好或达到发送上限，请联系服务维护者";
        else if(r.status==404||"PGRST202".equals(code)||"42P01".equals(code))message="云备份服务尚未初始化，请完成数据库配置";
        else if(r.status>=500)message="云端暂时不可用，请稍后重试；本机记录不受影响";
        else message="账号操作未成功，请检查输入或稍后重试";
        throw new Failure(r.status,code,message);
    }
}
