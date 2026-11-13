package cn.weeklymoments.guard;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.json.JSONArray;

/** Original, local diagrams with semantic labels, not screenshots or downloaded pictures. */
final class ConceptDiagramView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String[] labels=new String[3]; private final String kind;
    ConceptDiagramView(Context c,String kind,JSONArray labels){super(c);this.kind=kind;for(int i=0;i<3;i++)this.labels[i]=labels.optString(i);setContentDescription("示意图："+this.labels[0]+" → "+this.labels[1]+" → "+this.labels[2]);}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),(int)dp(170));}
    private void line(Canvas c,float x,float y,float a,float b){p.setColor(0xFF587FAA);p.setStrokeWidth(dp(1));c.drawLine(x,y,a,b,p);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();p.setColor(0xFF0C1B30);c.drawRoundRect(0,0,w,h,dp(16),dp(16),p);
        float[] xs={w*.18f,w*.5f,w*.82f};float y=h*.43f;
        if(kind.equals("attention")){for(int a=0;a<3;a++)for(int b=0;b<3;b++){p.setColor(a==b?0xFF89DDFF:0xFF36587F);p.setAlpha(a==b?255:140);c.drawCircle(w*(.32f+b*.18f),h*(.22f+a*.16f),dp(a==b?10:6),p);}p.setAlpha(255);}
        else if(kind.equals("network")){for(int a=0;a<2;a++)for(int j=0;j<3;j++)for(int k=0;k<3;k++)line(c,xs[a],h*(.25f+j*.16f),xs[a+1],h*(.25f+k*.16f));for(float x:xs)for(int j=0;j<3;j++){p.setColor(0xFF9EDFFF);c.drawCircle(x,h*(.25f+j*.16f),dp(5),p);}}
        else if(kind.equals("vector")){line(c,w*.12f,h*.65f,w*.88f,h*.65f);line(c,w*.12f,h*.65f,w*.12f,h*.14f);float[] px={w*.35f,w*.43f,w*.76f},py={h*.3f,h*.36f,h*.52f};for(int i=0;i<3;i++){p.setColor(i==2?0xFFC6AEFF:0xFF86DDFF);c.drawCircle(px[i],py[i],dp(7),p);}}
        else {for(int i=0;i<3;i++){if(i<2)line(c,xs[i]+dp(25),y,xs[i+1]-dp(25),y);p.setColor(i==1?0xFFB8A3EF:0xFF86DDFF);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));c.drawRoundRect(xs[i]-dp(24),y-dp(22),xs[i]+dp(24),y+dp(22),dp(kind.equals("multimodal")?18:10),dp(10),p);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(17));c.drawText("0"+(i+1),xs[i],y+dp(6),p);}}
        p.setTextSize(dp(11));p.setTextAlign(Paint.Align.CENTER);p.setColor(LifeUi.INK);for(int i=0;i<3;i++)c.drawText(labels[i],xs[i],h*.87f,p);
    }
}
