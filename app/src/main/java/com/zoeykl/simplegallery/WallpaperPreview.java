package com.zoeykl.simplegallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Screen-shaped crop preview; the same crop is rendered into the submitted bitmap. */
final class WallpaperPreview extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final GestureDetector drag;
    private final ScaleGestureDetector pinch;
    Bitmap bitmap;
    float centerX = .5f, centerY = .5f, zoom = 1;
    final float aspect;

    WallpaperPreview(Context context, float aspect) {
        super(context);
        this.aspect = aspect;
        setContentDescription(context.getString(R.string.wallpaper_adjust));
        drag = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                if (bitmap != null && !pinch.isInProgress()) {
                    RectF frame = frame();
                    float[] crop = crop();
                    centerX += dx / frame.width() * (crop[2] - crop[0]) / bitmap.getWidth();
                    centerY += dy / frame.height() * (crop[3] - crop[1]) / bitmap.getHeight();
                    clamp(); invalidate();
                }
                return true;
            }
            @Override public boolean onSingleTapUp(MotionEvent e) { performClick(); return true; }
        });
        pinch = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                zoomBy(detector.getScaleFactor()); return true;
            }
        });
    }

    void zoomBy(float factor) { zoom = Math.max(1, Math.min(4, zoom * factor)); clamp(); invalidate(); }
    void reset() { zoom = 1; centerX = centerY = .5f; invalidate(); }
    float[] crop() { return WallpaperCrop.bounds(bitmap.getWidth(), bitmap.getHeight(), aspect, zoom, centerX, centerY); }
    private void clamp() {
        if (bitmap == null) return;
        float[] c = crop();
        centerX = (c[0] + c[2]) / (2 * bitmap.getWidth());
        centerY = (c[1] + c[3]) / (2 * bitmap.getHeight());
    }
    private RectF frame() {
        float w = Math.min(getWidth(), getHeight() * aspect);
        float h = w / aspect;
        return new RectF((getWidth() - w) / 2, (getHeight() - h) / 2,
                (getWidth() + w) / 2, (getHeight() + h) / 2);
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.rgb(24, 24, 24));
        if (bitmap == null) return;
        float[] c = crop();
        RectF target = frame();
        canvas.save();
        canvas.clipRect(target);
        canvas.translate(target.left, target.top);
        canvas.scale(target.width() / (c[2] - c[0]), target.height() / (c[3] - c[1]));
        canvas.translate(-c[0], -c[1]);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        canvas.restore();
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        pinch.onTouchEvent(event); drag.onTouchEvent(event); return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
