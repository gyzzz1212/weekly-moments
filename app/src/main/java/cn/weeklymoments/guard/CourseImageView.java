package cn.weeklymoments.guard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** In-app reference image: pinch to zoom, drag, double tap to reset. */
final class CourseImageView extends View {
    private final Bitmap image;
    private final Matrix matrix = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector scale;
    private final GestureDetector gestures;
    private float factor, minimum = 1, x, y;
    CourseImageView(Context context, Bitmap image) {
        super(context); this.image = image;
        setBackgroundColor(LifeUi.BG); setContentDescription("课表原图，可双指缩放和拖动，双击还原；识别文字在上一页可查看");
        scale = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                float next = Math.max(minimum, Math.min(6, factor * detector.getScaleFactor()));
                matrix.postScale(next / factor, next / factor, detector.getFocusX(), detector.getFocusY()); factor = next; invalidate(); return true;
            }
        });
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { reset(); return true; }
        });
    }
    private void reset() {
        minimum = Math.min(getWidth() / (float)image.getWidth(), getHeight() / (float)image.getHeight());
        factor = Math.max(.01f, minimum); matrix.setScale(factor, factor);
        matrix.postTranslate((getWidth() - image.getWidth() * factor) / 2, (getHeight() - image.getHeight() * factor) / 2); invalidate();
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { reset(); }
    @Override protected void onDraw(Canvas canvas) { canvas.drawBitmap(image, matrix, paint); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        gestures.onTouchEvent(event); scale.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !scale.isInProgress() && event.getPointerCount() == 1) {
            matrix.postTranslate(event.getX() - x, event.getY() - y); invalidate();
        }
        x = event.getX(); y = event.getY(); return true;
    }
}
