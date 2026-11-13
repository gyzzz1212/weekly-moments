package cn.weeklymoments.guard;

import android.content.Context;
import android.util.AtomicFile;
import android.security.keystore.*;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Session tokens are encrypted with a non-exportable Android Keystore key, outside backup storage. */
final class AccountVault implements AccountClient.Vault {
    private static final String ALIAS="focus-space-account-v1";
    private final AtomicFile file;
    AccountVault(Context c){file=new AtomicFile(new File(c.getNoBackupFilesDir(),"account-session-v1.bin"));}
    private SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
    }
    @Override public synchronized JSONObject load()throws Exception{
        if(!file.getBaseFile().exists()&&!new File(file.getBaseFile()+".bak").exists())return null;
        try(FileInputStream input=file.openRead();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1){if(bytes.size()+n>256*1024)throw new IOException("登录信息无效");bytes.write(buffer,0,n);}
            byte[] all=bytes.toByteArray();if(all.length<29)throw new IOException("登录信息无效");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,all,0,12));
            return new JSONObject(new String(cipher.doFinal(all,12,all.length-12),StandardCharsets.UTF_8));
        }catch(Exception e){clear();return null;}
    }
    @Override public synchronized void save(JSONObject value)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));FileOutputStream out=null;
        try{out=file.startWrite();out.write(cipher.getIV());out.write(encrypted);file.finishWrite(out);}
        catch(Exception e){if(out!=null)file.failWrite(out);throw new IOException("无法安全保存登录状态，请检查手机剩余空间");}
    }
    @Override public synchronized void clear()throws Exception{file.delete();KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))store.deleteEntry(ALIAS);}
}
