package cn.weeklymoments.guard;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/** The production overlay and preview share this borderless immersive scene. */
public final class FocusReminderView extends FrameLayout {
    private final PolicyStore store;
    private final Random random=new Random();
    private final TextView quote,suggestion;
    private int quoteIndex;
    public FocusReminderView(Context c,PolicyStore store,boolean preview,Runnable back,Runnable settings){
        super(c);this.store=store;setBackgroundColor(LifeUi.BG);
        setOnApplyWindowInsetsListener((v,insets)->{
            setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        ScrollView scroll=new ScrollView(c);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout scene=LifeUi.column(c,false);scene.setPadding(0,0,0,LifeUi.dp(c,24));scroll.addView(scene);
        TextView mark=LifeUi.text(c,preview?"FOCUS  /  提醒页预览":"FOCUS  /  专注保护中",11,true);
        mark.setTextColor(LifeUi.GREEN);mark.setLetterSpacing(.1f);mark.setPadding(LifeUi.dp(c,24),LifeUi.dp(c,10),0,0);LifeUi.add(scene,mark,0);
        int heroHeight=Math.max(220,Math.min(340,(int)(c.getResources().getDisplayMetrics().heightPixels/c.getResources().getDisplayMetrics().density*.40f)));
        scene.addView(new CompanionDialogue.CompanionView(c,false,true),new LinearLayout.LayoutParams(-1,LifeUi.dp(c,heroHeight)));
        LinearLayout words=LifeUi.column(c,false);words.setPadding(LifeUi.dp(c,28),0,LifeUi.dp(c,28),0);LifeUi.add(scene,words,0);
        TextView speaker=LifeUi.text(c,"专注搭档",12,true);speaker.setTextColor(LifeUi.GREEN);LifeUi.add(words,speaker,0);
        quote=LifeUi.text(c,"",26,true);quote.setLineSpacing(LifeUi.dp(c,6),1.08f);LifeUi.add(words,quote,12);
        suggestion=LifeUi.text(c,"",15,false);suggestion.setTextColor(LifeUi.MUTED);LifeUi.add(words,suggestion,14);
        Button shuffle=LifeUi.button(c,"换一句提醒  ↗",false,this::shuffle);LifeUi.add(words,shuffle,16);
        TextView timing=LifeUi.text(c,preview?"预览不会消耗额度。":availability(),12,false);timing.setTextColor(LifeUi.MUTED);LifeUi.add(words,timing,24);
        LifeUi.add(words,LifeUi.button(c,"返回专注自己",true,back),12);
        TextView access=LifeUi.text(c,"查看放行时间",12,false);access.setTextColor(LifeUi.GREEN);access.setGravity(Gravity.CENTER);access.setPadding(0,LifeUi.dp(c,16),0,LifeUi.dp(c,12));access.setOnClickListener(v->settings.run());access.setFocusable(true);LifeUi.add(words,access,2);
        quoteIndex=FocusQuotes.nextIndex(store.lastQuoteIndex(),random);bind();
    }
    public void rememberShownQuote(){store.setLastQuoteIndex(quoteIndex);}
    private String availability(){
        if(store.paused())return "本次额度已暂停，可在放行页恢复。";
        if(store.canStart())return "本次可用 15 分钟，不必现在开启。";
        return "下次可用 "+new SimpleDateFormat("M月d日 HH:mm",Locale.CHINA).format(new Date(System.currentTimeMillis()+store.cooldownRemaining()));
    }
    private void bind(){FocusQuotes.Entry e=FocusQuotes.at(quoteIndex);quote.setText(e.words);suggestion.setText(e.action);}
    private void shuffle(){quoteIndex=FocusQuotes.nextIndex(quoteIndex,random);bind();rememberShownQuote();UiMotion.sound(getContext(),true);}
}
