package cn.weeklymoments.guard;

import java.net.URI;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Only a public application key may be bundled. All record access still requires a user JWT. */
final class CloudConfig {
    final String origin,key;
    CloudConfig(String origin,String key){this.origin=origin.trim().replaceAll("/+$","");this.key=key.trim();}
    boolean ready(){return !origin.isEmpty()&&!key.isEmpty();}
    void validate()throws Exception{
        URI uri=new URI(origin);
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||!uri.getHost().matches("[a-z0-9]+\\.supabase\\.co")
            ||uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!uri.getPath().isEmpty())
            throw new IllegalArgumentException("云端地址必须是项目的 HTTPS Supabase 地址");
        if(key.startsWith("sb_publishable_")&&key.matches("sb_publishable_[A-Za-z0-9_-]{10,}"))return;
        String[] parts=key.split("\\.");
        if(parts.length==3){try{JSONObject claims=new JSONObject(new String(Base64.getUrlDecoder().decode(parts[1]),StandardCharsets.UTF_8));
            if("anon".equals(claims.optString("role")))return;}catch(Exception ignored){}}
        throw new IllegalArgumentException("只能使用公开连接密钥，不能打包管理员密钥");
    }
}
