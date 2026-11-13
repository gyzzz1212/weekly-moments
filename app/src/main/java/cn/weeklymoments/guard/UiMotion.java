package cn.weeklymoments.guard;

import android.content.Context;
import android.media.*;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.animation.ValueAnimator;
import java.io.IOException;

final class UiMotion {
    private static SoundPool sounds;
    private static int open, close, pending;
    private static boolean openReady, closeReady;
    private static long pendingUntil;
    private static boolean pendingPreview;
    static void prepare(Context c){
        if(sounds!=null)return;
        // Media volume is user-adjustable; keep silent/vibrate and call suppression below.
        sounds=new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        Context app=c.getApplicationContext();
        sounds.setOnLoadCompleteListener((pool,id,status)->{
            if(status!=0)return;
            if(id==open)openReady=true;if(id==close)closeReady=true;
            if(id==pending&&SystemClock.uptimeMillis()<pendingUntil){pending=0;play(app,id,pendingPreview);}
        });
        try(android.content.res.AssetFileDescriptor a=c.getAssets().openFd("audio/paper_open.wav");
            android.content.res.AssetFileDescriptor b=c.getAssets().openFd("audio/paper_close.wav")){
            open=sounds.load(a,1);close=sounds.load(b,1);
        }catch(IOException e){android.util.Log.w("UiMotion","Interface audio unavailable",e);}
    }
    private static void play(Context c,int id,boolean preview){
        AudioManager audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        if((!preview && !LifeStore.get(c).data().routine.soundEnabled) || audio==null || audio.getMode()!=AudioManager.MODE_NORMAL
            || audio.getRingerMode()!=AudioManager.RINGER_MODE_NORMAL)return;
        if(id!=0)sounds.play(id,.72f,.72f,1,0,1f);
    }
    static void preview(Context c){sound(c,true,true);}
    static void sound(Context c,boolean opening){sound(c,opening,false);}
    private static void sound(Context c,boolean opening,boolean preview){prepare(c);int id=opening?open:close;
        if(opening?openReady:closeReady)play(c,id,preview);
        else {pending=id;pendingPreview=preview;pendingUntil=SystemClock.uptimeMillis()+800;}
    }
    static void subtitles(View v){
        if(v instanceof DialogueTextView)((DialogueTextView)v).start();
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)subtitles(g.getChildAt(i));}
    }
    static void finishSubtitles(View v){
        if(v instanceof DialogueTextView)((DialogueTextView)v).complete();
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)finishSubtitles(g.getChildAt(i));}
    }
    static void toggle(View target){
        boolean opening=target.getVisibility()!=View.VISIBLE;sound(target.getContext(),opening);target.animate().cancel();
        if(opening){target.setVisibility(View.VISIBLE);subtitles(target);}
        else finishSubtitles(target);
        if(!ValueAnimator.areAnimatorsEnabled()){target.setVisibility(opening?View.VISIBLE:View.GONE);return;}
        if(opening){target.setAlpha(0);target.setTranslationY(LifeUi.dp(target.getContext(),10));
            target.animate().alpha(1).translationY(0).setDuration(240).start();}
        else target.animate().alpha(0).setDuration(140).withEndAction(()->{target.setVisibility(View.GONE);target.setAlpha(1);}).start();
    }
    private UiMotion(){}
}
