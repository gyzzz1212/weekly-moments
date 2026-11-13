package cn.weeklymoments.guard;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.Gravity;
import android.widget.*;
import java.io.*;

/** Local art with restrained procedural idle motion; no camera, tracking or user observation. */
final class CompanionDialogue {
    static void add(Context c,LinearLayout parent,String words,boolean small){
        LinearLayout scene=LifeUi.column(c,false);
        LinearLayout bubble=LifeUi.column(c,true);
        TextView name=LifeUi.text(c,"专注搭档  /  GUIDE",11,true);name.setTextColor(LifeUi.GREEN);LifeUi.add(bubble,name,0);
        DialogueTextView subtitle=new DialogueTextView(c,words,17); LifeUi.add(bubble,subtitle,12);
        TextView hint=LifeUi.text(c,"轻点文字 · 立即显示完整内容",11,false);hint.setTextColor(LifeUi.MUTED);LifeUi.add(bubble,hint,10); subtitle.bindHint(hint);
        if(small){
            scene.setOrientation(LinearLayout.HORIZONTAL);scene.setGravity(Gravity.CENTER_VERTICAL);
            scene.addView(bubble,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout.LayoutParams portrait=new LinearLayout.LayoutParams(LifeUi.dp(c,96),LifeUi.dp(c,164));portrait.leftMargin=LifeUi.dp(c,8);
            scene.addView(new StandingCompanionView(c),portrait);
        }else{
            scene.addView(new CompanionView(c,false,true),new LinearLayout.LayoutParams(-1,LifeUi.dp(c,180)));
            LifeUi.add(scene,bubble,0);
        }
        LifeUi.add(parent,scene,18);
        scene.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){if(ValueAnimator.areAnimatorsEnabled()){v.setAlpha(0);v.setTranslationY(LifeUi.dp(c,10));v.animate().alpha(1).translationY(0).setDuration(240).start();}}
            public void onViewDetachedFromWindow(View v){v.animate().cancel();}
        });
    }

    static final class CompanionView extends View {
        private Bitmap image,expressionFrame;private final boolean small;private final boolean immersive;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final Paint fade=new Paint(Paint.ANTI_ALIAS_FLAG);
        private Shader verticalFade,horizontalFade;
        private final Paint mask=new Paint(Paint.ANTI_ALIAS_FLAG);private final long start=SystemClock.uptimeMillis();
        CompanionView(Context c,boolean small){this(c,small,false);}
        CompanionView(Context c,boolean small,boolean immersive){super(c);this.small=small;this.immersive=immersive;
            CharacterAssets.load(c,small?"character/chibi.png":"character/assistant.png",bitmap->{image=bitmap;invalidate();});
            CharacterAssets.load(c,small?"character/chibi-blink.png":"character/assistant-blink.png",bitmap->{expressionFrame=bitmap;invalidate();});
            setContentDescription("专注搭档插画");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);
            if(w>0&&h>0){verticalFade=new LinearGradient(0,0,0,h,new int[]{LifeUi.BG,0x000B1220,0x000B1220,LifeUi.BG},new float[]{0,.12f,.55f,1},Shader.TileMode.CLAMP);
                horizontalFade=new LinearGradient(0,0,w,0,new int[]{LifeUi.BG,0x000B1220,0x000B1220,LifeUi.BG},new float[]{0,.18f,.85f,1},Shader.TileMode.CLAMP);}}
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(image==null)return;
            boolean motion=ValueAnimator.areAnimatorsEnabled();float scale=immersive?getWidth()/(float)image.getWidth():Math.min(getWidth()/(float)image.getWidth(),getHeight()/(float)image.getHeight());
            c.save();c.translate((getWidth()-image.getWidth()*scale)/2,immersive?-image.getHeight()*scale*.07f:(getHeight()-image.getHeight()*scale)/2);c.scale(scale,scale);
            boolean compact=small;
            c.drawBitmap(image,0,0,p);
            if(motion && expressionFrame!=null){
                long age=Math.max(0,SystemClock.uptimeMillis()-start);
                if(CompanionFrames.blink(age)){
                    if(compact){expression(c,.345f,.378f,.477f,.462f,1f);expression(c,.503f,.315f,.618f,.395f,1f);}
                    else expression(c,.30f,.195f,.56f,.29f,1f);
                }
                float alpha=CompanionFrames.smile(age);
                if(alpha>0){
                    if(compact)expression(c,.47f,.43f,.585f,.49f,alpha);
                    else expression(c,.405f,.285f,.54f,.335f,alpha);
                }
            }
            c.restore();
            if(immersive){
                float w=getWidth(),h=getHeight();
                if(verticalFade!=null){fade.setShader(verticalFade);c.drawRect(0,0,w,h,fade);}
                if(horizontalFade!=null){fade.setShader(horizontalFade);c.drawRect(0,0,w,h,fade);}
            }
            if(motion&&isShown()&&getWindowVisibility()==VISIBLE)postInvalidateDelayed(CompanionFrames.portraitDelay(Math.max(0,SystemClock.uptimeMillis()-start)));
        }
        private void expression(Canvas c,float l,float t,float r,float b,float opacity){
            RectF area=new RectF(image.getWidth()*l,image.getHeight()*t,image.getWidth()*r,image.getHeight()*b);
            c.saveLayer(area,null);c.clipRect(area);
            p.setAlpha(Math.round(255*opacity));c.drawBitmap(expressionFrame,null,new RectF(0,0,image.getWidth(),image.getHeight()),p);p.setAlpha(255);
            int[] fade={0x00FFFFFF,0xFFFFFFFF,0xFFFFFFFF,0x00FFFFFF};float[] stops={0,.09f,.91f,1};
            Shader horizontal=new LinearGradient(area.left,0,area.right,0,fade,stops,Shader.TileMode.CLAMP);
            Shader vertical=new LinearGradient(0,area.top,0,area.bottom,fade,stops,Shader.TileMode.CLAMP);
            mask.setShader(new ComposeShader(horizontal,vertical,PorterDuff.Mode.MULTIPLY));mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            c.drawRect(area,mask);mask.setXfermode(null);mask.setShader(null);c.restore();
        }
        @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)invalidate();}
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();invalidate();}
    }
    private CompanionDialogue(){}
}
