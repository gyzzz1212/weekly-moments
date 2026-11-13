package cn.weeklymoments.guard;

import org.json.*;
import java.util.*;

/** Executes real client logic against a scripted provider, never real personal credentials. */
public final class AccountTests {
    private static int checks;
    static void yes(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    interface Action{void run()throws Exception;}
    static void fails(Action action,String message)throws Exception{boolean rejected=false;try{action.run();}catch(Exception expected){rejected=true;}yes(rejected,message);}
    static final class Memory implements AccountClient.Vault {
        JSONObject value;public JSONObject load(){return value;}public void save(JSONObject v)throws Exception{value=new JSONObject(v.toString());}public void clear(){value=null;}
    }
    static final class Wire implements AccountClient.Transport {
        int status=200,calls;String response="{}",url,method,body;Map<String,String> headers;boolean offline;
        public AccountClient.Reply send(String method,String url,Map<String,String> headers,String body)throws Exception{
            calls++;this.url=url;this.method=method;this.headers=headers;this.body=body;
            if(offline)throw new java.io.IOException("offline");return new AccountClient.Reply(status,response);
        }
    }
    static String session(String access,int lifetime)throws Exception{return new JSONObject().put("access_token",access).put("refresh_token","test-refresh")
        .put("expires_in",lifetime).put("user",new JSONObject().put("id","11111111-1111-4111-8111-111111111111").put("email","test@example.invalid")).toString();}
    public static void main(String[] args)throws Exception{
        CloudConfig config=new CloudConfig("https://testproject.supabase.co/","sb_publishable_test_public_123456");config.validate();
        fails(()->new CloudConfig("http://testproject.supabase.co",config.key).validate(),"reject HTTP");
        fails(()->new CloudConfig("https://testproject.supabase.co.evil.invalid",config.key).validate(),"reject lookalike endpoint");
        fails(()->new CloudConfig(config.origin,"sb_secret_not_for_apps").validate(),"reject secret key");
        String admin="x."+Base64.getUrlEncoder().withoutPadding().encodeToString("{\"role\":\"service_role\"}".getBytes("UTF-8"))+".y";
        fails(()->new CloudConfig(config.origin,admin).validate(),"reject legacy admin JWT");
        fails(()->AccountClient.email("x\n@evil.invalid"),"reject newline email");fails(()->AccountClient.password("short"),"reject weak registration password");
        Memory vault=new Memory();Wire wire=new Wire();AccountClient client=new AccountClient(config,wire,vault);
        wire.response="{\"user\":{\"id\":\"unconfirmed\"}}";
        yes(!client.signUp("test@example.invalid","test-password"),"unconfirmed registration requires verification");yes(!client.signedIn()&&vault.value==null,"do not invent logged-in session");
        wire.response=session("access-one",3600);client.signIn("test@example.invalid","test-password");
        yes(client.signedIn(),"real session retained");yes(wire.url.endsWith("/auth/v1/token?grant_type=password"),"provider password grant");
        yes(!vault.value.toString().contains("test-password"),"password not persisted");yes("test@example.invalid".equals(client.email()),"account email from session");
        wire.response="[]";client.backups();yes(wire.url.contains("select=id,created_at,sha256,client_version"),"list metadata without loading all backup payloads");
        yes("Bearer access-one".equals(wire.headers.get("Authorization")),"backup requests require user token");
        String body="{\"format\":\"focus-space-backup\",\"version\":1,\"payload\":{\"schema\":1}}";
        wire.response="\"backup-id\"";client.saveBackup(body,"test");JSONObject sent=new JSONObject(wire.body);
        yes(!sent.has("user_id"),"server decides backup ownership");yes(sent.getString("p_sha256").equals(AccountClient.sha256(body)),"exact UTF8 checksum sent");
        String id="11111111-1111-4111-8111-111111111111";
        wire.response=new JSONArray().put(new JSONObject().put("payload",body).put("sha256",AccountClient.sha256(body))).toString();
        yes(client.backup(id).getString("payload").equals(body),"download validated");
        int before=wire.calls;fails(()->client.backup("x&user_id=eq.someone"),"query injection rejected");yes(wire.calls==before,"invalid id never sent");
        wire.response=new JSONArray().put(new JSONObject().put("payload",body).put("sha256","bad")).toString();fails(()->client.backup(id),"damaged backup rejected before restore");
        wire.response="[]";fails(()->client.backup(id),"missing or foreign backup rejected");
        wire.status=401;wire.response="{\"error_code\":\"invalid_credentials\"}";fails(()->client.signIn("test@example.invalid","wrong"),"failed login surfaced");
        wire.status=200;wire.response=session("access-expired",0);client.signIn("test@example.invalid","test-password");
        wire.status=400;wire.response="{\"error_code\":\"refresh_token_not_found\"}";fails(client::backups,"expired refresh prompts login");yes(!client.signedIn()&&vault.value==null,"expired credentials cleared");
        wire.status=200;wire.response=session("access-new",3600);client.signIn("test@example.invalid","test-password");
        wire.offline=true;yes(!client.signOut(),"offline revocation reported");yes(!client.signedIn()&&vault.value==null,"offline signout forgets local tokens");
        wire.offline=false;wire.response=session("access-other",3600);client.signIn("test@example.invalid","test-password");
        AccountClient other=new AccountClient(new CloudConfig("https://otherproject.supabase.co",config.key),wire,vault);
        yes(!other.signedIn(),"project change cannot reuse credentials");
        wire.response=session("recovery-session",3600);yes(client.resetPassword("test@example.invalid","123456","next-test-password"),"verified recovery changes password and revokes sessions");
        yes(!client.signedIn()&&vault.value==null,"recovery does not leave temporary tokens on device");
        yes(wire.url.endsWith("/auth/v1/logout?scope=global"),"recovery revokes all sessions");
        fails(()->client.verifySignup("test@example.invalid","bad-code"),"malformed verification code rejected");
        Wire disabled=new Wire();AccountClient pending=new AccountClient(new CloudConfig("",""),disabled,new Memory());
        fails(()->pending.signIn("test@example.invalid","test-password"),"unconfigured service blocks auth");yes(disabled.calls==0,"no email or password sent before config");
        System.out.println("PASS: "+checks+" account client assertions: provider auth, no fake login, password exclusion, token expiry/logout, endpoint validation, backup ownership and corruption checks");
    }
}
