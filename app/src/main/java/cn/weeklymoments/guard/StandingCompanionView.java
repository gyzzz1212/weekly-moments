package cn.weeklymoments.guard;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.animation.ValueAnimator;
import android.view.View;
import java.io.*;

/** Upright neutral pose, occasional blink and a small below-waist hand response only on tap. */
final class StandingCompanionView extends View {
    private Bitmap atlas;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private long started=SystemClock.uptimeMillis(),respondUntil;
    StandingCompanionView(Context c){
        super(c);
        CharacterAssets.load(c,"character/companion-standing-v2.png",bitmap->{atlas=bitmap;invalidate();});
        setContentDescription("专注搭档，轻点回应");setFocusable(true);
        setOnClickListener(v->{respondUntil=SystemClock.uptimeMillis()+900;invalidate();});
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(atlas==null)return;
        long time=SystemClock.uptimeMillis(),age=Math.max(0,time-started),responseLeft=Math.max(0,respondUntil-time);
        boolean motion=ValueAnimator.areAnimatorsEnabled();int frame=motion?CompanionFrames.standing(age,responseLeft):0;
        int w=atlas.getWidth()/2,h=atlas.getHeight()/2;
        Rect from=new Rect((frame%2)*w,(frame/2)*h,(frame%2+1)*w,(frame/2+1)*h);
        float scale=Math.min(getWidth()/(float)w,getHeight()/(float)h),dw=w*scale,dh=h*scale;
        c.drawBitmap(atlas,from,new RectF((getWidth()-dw)/2,(getHeight()-dh)/2,(getWidth()+dw)/2,(getHeight()+dh)/2),paint);
        if(motion&&isShown()&&getWindowVisibility()==VISIBLE)postInvalidateDelayed(CompanionFrames.standingDelay(age,responseLeft));
    }
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)invalidate();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();started=SystemClock.uptimeMillis();invalidate();}
}
