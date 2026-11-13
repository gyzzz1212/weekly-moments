package cn.weeklymoments.guard;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Resolution-independent cover, shared visual language with the launcher mark. */
final class FocusCoverView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    FocusCoverView(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float w=getWidth(),h=getHeight(),x=w*.5f,y=h*.5f,r=Math.min(w*.29f,h*.41f);
        paint.setShader(new RadialGradient(x,y,r*1.7f,new int[]{0x553C849A,0x00203D65},null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(0x263B6578);c.drawCircle(x,y,r*1.42f,paint);
        paint.setColor(0x5A6E9CAA);c.drawCircle(x,y,r*1.12f,paint);
        RectF orbit=new RectF(x-r,y-r,x+r,y+r);paint.setStrokeWidth(r*.15f);paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setShader(new LinearGradient(x-r,y-r,x+r,y+r,new int[]{0xFFE1FAF5,0xFF9ED9E7,0xFF9A9AC8},null,Shader.TileMode.CLAMP));
        c.drawArc(orbit,140,260,false,paint);paint.setShader(null);
        Path tick=new Path();tick.moveTo(x-r*.38f,y+r*.02f);tick.lineTo(x-r*.05f,y+r*.35f);tick.lineTo(x+r*.55f,y-r*.39f);
        paint.setStrokeWidth(r*.14f);paint.setColor(0xFFE5FAF6);c.drawPath(tick,paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xFFE0FFF7);
        double a=Math.toRadians(40);c.drawCircle(x+(float)Math.cos(a)*r,y+(float)Math.sin(a)*r,r*.14f,paint);
        paint.setColor(0xFF7E9CB0);c.drawCircle(x+r*1.35f,y-r*.83f,2,paint);
    }
}
