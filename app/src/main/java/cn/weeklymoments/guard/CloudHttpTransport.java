package cn.weeklymoments.guard;

import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** The only app networking boundary. TLS verification stays at the platform defaults. */
final class CloudHttpTransport implements AccountClient.Transport {
    @Override public AccountClient.Reply send(String method,String url,Map<String,String> headers,String body)throws Exception{
        HttpsURLConnection connection=(HttpsURLConnection)new URL(url).openConnection();
        try{
            connection.setRequestMethod(method);connection.setConnectTimeout(10000);connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
            for(Map.Entry<String,String> e:headers.entrySet())connection.setRequestProperty(e.getKey(),e.getValue());
            if(body!=null){byte[] bytes=body.getBytes(StandardCharsets.UTF_8);connection.setDoOutput(true);connection.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream out=connection.getOutputStream()){out.write(bytes);}}
            int status=connection.getResponseCode();InputStream stream=status>=400?connection.getErrorStream():connection.getInputStream();
            if(status>=300&&status<400)throw new IOException("云端地址发生跳转，请检查服务配置");
            if(stream==null)return new AccountClient.Reply(status,"");
            try(InputStream input=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(out.size()+n>20*1024*1024)throw new IOException("云端响应超过限制");out.write(buffer,0,n);}
                return new AccountClient.Reply(status,new String(out.toByteArray(),StandardCharsets.UTF_8));
            }
        }catch(java.net.UnknownHostException|java.net.SocketTimeoutException e){throw new IOException("暂时无法连接云端，请检查网络后重试；本机记录仍然保留");}
        finally{connection.disconnect();}
    }
}
