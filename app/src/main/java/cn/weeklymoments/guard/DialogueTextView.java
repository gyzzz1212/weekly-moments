package cn.weeklymoments.guard;

import android.content.Context;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;

/** Offline subtitles. Tap once to reveal; lifecycle and reduced-motion safe. */
final class DialogueTextView extends TextView {
    private final Handler clock = new Handler(Looper.getMainLooper());
    private String words; private int cursor;
    private TextView revealHint;
    void bindHint(TextView hint) { revealHint=hint; hint.setOnClickListener(v->complete()); updateHint(); }
    private void updateHint(){if(revealHint!=null)revealHint.setVisibility(cursor<words.length()?VISIBLE:GONE);}
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!isShown()) { complete(); return; }
            if (cursor >= words.length()) { complete(); return; }
            cursor += Character.charCount(words.codePointAt(cursor));
            setText(words.substring(0,cursor));
            if(cursor<words.length()) clock.postDelayed(this, punctuation(words.charAt(cursor-1))?160:32);
            else complete();
        }
    };
    DialogueTextView(Context c, String text, int size) {
        super(c); words=text; setTextSize(size); setTextColor(LifeUi.INK);
        setTypeface(LifeUi.font(c,false)); setIncludeFontPadding(false);
        setLineSpacing(LifeUi.dp(c,5),1.12f); setMinLines(2);
        setText(words); setContentDescription(words); setOnClickListener(v->complete());
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_NONE);
    }
    private boolean punctuation(char c) { return "，。！？；：\n".indexOf(c)>=0; }
    void start() {
        clock.removeCallbacks(tick); cursor=0;
        AccessibilityManager a=(AccessibilityManager)getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if(!ValueAnimator.areAnimatorsEnabled() || (a!=null && a.isTouchExplorationEnabled())) {complete();return;}
        setText(""); updateHint(); clock.postDelayed(tick,180);
    }
    void complete() {clock.removeCallbacks(tick);cursor=words.length();setText(words);updateHint();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(isShown())start();}
    @Override protected void onDetachedFromWindow(){complete();super.onDetachedFromWindow();}
}
