package cn.weeklymoments.guard;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Decode on one background worker; never delay the construction of the reminder's buttons. */
final class CharacterAssets {
    private static final Handler UI=new Handler(Looper.getMainLooper());
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(24*1024*1024){
        @Override protected int sizeOf(String name,Bitmap bitmap){return bitmap.getAllocationByteCount();}
    };
    private static final Map<String,List<Consumer<Bitmap>>> WAITING=new HashMap<>();
    static void load(Context c,String name,Consumer<Bitmap> ready){
        Context app=c.getApplicationContext();
        synchronized(WAITING){Bitmap found=CACHE.get(name);if(found!=null){ready.accept(found);return;}
            List<Consumer<Bitmap>> callbacks=WAITING.get(name);if(callbacks!=null){callbacks.add(ready);return;}
            callbacks=new ArrayList<>();callbacks.add(ready);WAITING.put(name,callbacks);}
        WORK.execute(()->{Bitmap result=null;
            try{
                BitmapFactory.Options size=new BitmapFactory.Options();size.inJustDecodeBounds=true;
                try(InputStream in=app.getAssets().open(name)){BitmapFactory.decodeStream(in,null,size);}
                BitmapFactory.Options decode=new BitmapFactory.Options();decode.inSampleSize=1;
                while(Math.max(size.outWidth,size.outHeight)/decode.inSampleSize>1536)decode.inSampleSize*=2;
                try(InputStream in=app.getAssets().open(name)){result=BitmapFactory.decodeStream(in,null,decode);}
                if(result!=null){result.prepareToDraw();CACHE.put(name,result);}
            }catch(IOException|RuntimeException e){android.util.Log.w("CharacterAssets","Companion art unavailable");}
            final Bitmap bitmap=result;UI.post(()->{List<Consumer<Bitmap>> callbacks;
                synchronized(WAITING){callbacks=WAITING.remove(name);}if(callbacks!=null)for(Consumer<Bitmap> callback:callbacks)callback.accept(bitmap);
            });
        });
    }
    private CharacterAssets(){}
}
