package cn.weeklymoments.guard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public final class ReminderPreviewActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(LifeUi.BG);
        getWindow().setNavigationBarColor(LifeUi.BG);
        FocusReminderView view = new FocusReminderView(this, PolicyStore.get(this), true,
                this::finish,
                () -> startActivity(new Intent(this,MainActivity.class).putExtra("section","moments")));
        setContentView(view);
        view.rememberShownQuote();
    }
}
