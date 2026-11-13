package cn.weeklymoments.guard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class LifeUi {
    static final int BG = 0xFF0B1220, INK = 0xFFF0F5FC, GREEN = 0xFFB2E6EF, MUTED = 0xFFB5C3D6;
    private static Typeface baseFont;
    static Typeface font(Context c, boolean medium) {
        if(baseFont==null)baseFont=c.getResources().getFont(R.font.focus_sans);
        Typeface base = baseFont;
        if (android.os.Build.VERSION.SDK_INT >= 28) return Typeface.create(base, medium ? 600 : 400, false);
        return Typeface.create(base, medium ? Typeface.BOLD : Typeface.NORMAL);
    }
    static int dp(Context c, int value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    static TextView text(Context c, String value, int size, boolean bold) {
        TextView v = new TextView(c); v.setText(value); v.setTextSize(size); v.setTextColor(INK);
        v.setLineSpacing(dp(c, 4), 1.05f);
        v.setTypeface(font(c, bold));
        v.setIncludeFontPadding(false);
        return v;
    }
    static GradientDrawable shape(Context c, int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radius));
        d.setStroke(dp(c, 1), 0xFF2B3C51); return d;
    }
    static LinearLayout column(Context c, boolean card) {
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL);
        if (card) { v.setPadding(dp(c, 20), dp(c, 20), dp(c, 20), dp(c, 20)); v.setBackground(shape(c, 0xFF162234, 24)); }
        return v;
    }
    static Button button(Context c, String value, boolean primary, Runnable action) {
        Button v = new Button(c); v.setText(value); v.setTextSize(15); v.setAllCaps(false); v.setTypeface(font(c,true));
        v.setTextColor(primary ? BG : GREEN); v.setMinHeight(dp(c, 50));
        v.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        v.setBackgroundTintList(null); v.setStateListAnimator(null);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x287AE0FF),
                shape(c, primary ? GREEN : 0xFF19283B, 16), shape(c, 0xFFFFFFFF, 16)));
        v.setOnClickListener(view -> action.run()); return v;
    }
    static EditText field(Context c, String hint, String value, boolean number) {
        EditText v = new EditText(c); v.setHint(hint); v.setHintTextColor(MUTED); v.setText(value); v.setTextSize(16); v.setTextColor(INK);
        v.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        v.setBackground(shape(c, 0xFF111D2E, 12));
        if (number) v.setInputType(InputType.TYPE_CLASS_NUMBER);
        v.setTypeface(font(c,false)); v.setMaxLines(number ? 1 : 4); return v;
    }
    static void add(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(parent.getContext(), margin); parent.addView(view, p);
    }
    static String clock(int minute) { return String.format(Locale.CHINA, "%02d:%02d", minute / 60, minute % 60); }
    static String clock(long instant) {
        if (instant <= 0) return "未记录";
        return DateTimeFormatter.ofPattern("M月d日 HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(instant));
    }
    static void pickTime(android.app.Activity c, String title, int initial, java.util.function.IntConsumer result) {
        LinearLayout body = column(c, false); body.setPadding(dp(c,24),dp(c,18),dp(c,24),dp(c,12));
        add(body,text(c,"小时                     分钟",13,false),0);
        LinearLayout row = new LinearLayout(c); row.setGravity(Gravity.CENTER);
        android.widget.NumberPicker hours = new android.widget.NumberPicker(c), minutes = new android.widget.NumberPicker(c);
        hours.setMinValue(0); hours.setMaxValue(23); hours.setValue(initial/60);
        minutes.setMinValue(0); minutes.setMaxValue(59); minutes.setValue(initial%60);
        hours.setFormatter(v -> String.format(Locale.CHINA,"%02d",v)); minutes.setFormatter(v -> String.format(Locale.CHINA,"%02d",v));
        hours.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        minutes.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        hours.setContentDescription("小时"); minutes.setContentDescription("分钟");
        row.addView(hours,new LinearLayout.LayoutParams(0,dp(c,160),1));
        row.addView(text(c,":",28,true)); row.addView(minutes,new LinearLayout.LayoutParams(0,dp(c,160),1)); add(body,row,8);
        new android.app.AlertDialog.Builder(c).setTitle(title).setView(body).setNegativeButton("取消",null)
            .setPositiveButton("保存",(d,w)->result.accept(hours.getValue()*60+minutes.getValue())).show();
    }
    private LifeUi() {}
}
