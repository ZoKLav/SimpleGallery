package com.zoeykl.simplegallery;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import com.zoeykl.simplegallery.HighlightView;
import java.util.ArrayList;
import java.util.Iterator;

/* compiled from: CropImage.java */

class CropImageView extends ImageViewTouchBase {
    ArrayList<HighlightView> mHighlightViews;
    float mLastX;
    float mLastY;
    int mMotionEdge;
    HighlightView mMotionHighlightView;

    @Override
    protected void onLayout(boolean z, int i, int i2, int i3, int i4) {
        super.onLayout(z, i, i2, i3, i4);
        if (this.mBitmapDisplayed.getBitmap() != null) {
            Iterator<HighlightView> it = this.mHighlightViews.iterator();
            while (it.hasNext()) {
                HighlightView next = it.next();
                next.mMatrix.set(getImageMatrix());
                next.invalidate();
                if (next.mIsFocused) {
                    centerBasedOnHighlightView(next);
                }
            }
        }
    }

    public CropImageView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.mHighlightViews = new ArrayList<>();
        this.mMotionHighlightView = null;
    }

    @Override
    protected void zoomTo(float f, float f2, float f3) {
        super.zoomTo(f, f2, f3);
        Iterator<HighlightView> it = this.mHighlightViews.iterator();
        while (it.hasNext()) {
            HighlightView next = it.next();
            next.mMatrix.set(getImageMatrix());
            next.invalidate();
        }
    }

    @Override
    protected void zoomIn() {
        super.zoomIn();
        Iterator<HighlightView> it = this.mHighlightViews.iterator();
        while (it.hasNext()) {
            HighlightView next = it.next();
            next.mMatrix.set(getImageMatrix());
            next.invalidate();
        }
    }

    @Override
    protected void zoomOut() {
        super.zoomOut();
        Iterator<HighlightView> it = this.mHighlightViews.iterator();
        while (it.hasNext()) {
            HighlightView next = it.next();
            next.mMatrix.set(getImageMatrix());
            next.invalidate();
        }
    }

    @Override
    protected void postTranslate(float f, float f2) {
        super.postTranslate(f, f2);
        for (int i = 0; i < this.mHighlightViews.size(); i++) {
            HighlightView highlightView = this.mHighlightViews.get(i);
            highlightView.mMatrix.postTranslate(f, f2);
            highlightView.invalidate();
        }
    }

    private void recomputeFocus(MotionEvent motionEvent) {
        int i = 0;
        for (int i2 = 0; i2 < this.mHighlightViews.size(); i2++) {
            HighlightView highlightView = this.mHighlightViews.get(i2);
            highlightView.setFocus(false);
            highlightView.invalidate();
        }
        while (true) {
            if (i >= this.mHighlightViews.size()) {
                break;
            }
            HighlightView highlightView2 = this.mHighlightViews.get(i);
            if (highlightView2.getHit(motionEvent.getX(), motionEvent.getY()) == 1) {
                i++;
            } else if (!highlightView2.hasFocus()) {
                highlightView2.setFocus(true);
                highlightView2.invalidate();
            }
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        CropImage cropImage = (CropImage) getContext();
        int i = 0;
        if (cropImage.mSaving) {
            return false;
        }
        switch (motionEvent.getAction()) {
            case 0:
                if (!cropImage.mWaitingToPick) {
                    while (true) {
                        if (i < this.mHighlightViews.size()) {
                            HighlightView highlightView = this.mHighlightViews.get(i);
                            int hit = highlightView.getHit(motionEvent.getX(), motionEvent.getY());
                            if (hit == 1) {
                                i++;
                            } else {
                                this.mMotionEdge = hit;
                                this.mMotionHighlightView = highlightView;
                                this.mLastX = motionEvent.getX();
                                this.mLastY = motionEvent.getY();
                                this.mMotionHighlightView.setMode(hit == 32 ? HighlightView.ModifyMode.Move : HighlightView.ModifyMode.Grow);
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                } else {
                    recomputeFocus(motionEvent);
                    break;
                }
            case 1:
                if (cropImage.mWaitingToPick) {
                    for (int i2 = 0; i2 < this.mHighlightViews.size(); i2++) {
                        HighlightView highlightView2 = this.mHighlightViews.get(i2);
                        if (highlightView2.hasFocus()) {
                            cropImage.mCrop = highlightView2;
                            for (int i3 = 0; i3 < this.mHighlightViews.size(); i3++) {
                                if (i3 != i2) {
                                    this.mHighlightViews.get(i3).setHidden(true);
                                }
                            }
                            centerBasedOnHighlightView(highlightView2);
                            ((CropImage) getContext()).mWaitingToPick = false;
                            return true;
                        }
                    }
                } else if (this.mMotionHighlightView != null) {
                    centerBasedOnHighlightView(this.mMotionHighlightView);
                    this.mMotionHighlightView.setMode(HighlightView.ModifyMode.None);
                }
                this.mMotionHighlightView = null;
                break;
            case 2:
                if (cropImage.mWaitingToPick) {
                    recomputeFocus(motionEvent);
                    break;
                } else if (this.mMotionHighlightView != null) {
                    this.mMotionHighlightView.handleMotion(this.mMotionEdge, motionEvent.getX() - this.mLastX, motionEvent.getY() - this.mLastY);
                    this.mLastX = motionEvent.getX();
                    this.mLastY = motionEvent.getY();
                    ensureVisible(this.mMotionHighlightView);
                    break;
                }
                break;
        }
        switch (motionEvent.getAction()) {
            case 1:
                center(true, true);
                return true;
            case 2:
                if (getScale() == 1.0f) {
                    center(true, true);
                }
                return true;
            default:
                return true;
        }
    }

    private void ensureVisible(HighlightView highlightView) {
        Rect rect = highlightView.mDrawRect;
        int iMax = Math.max(0, getLeft() - rect.left);
        int iMin = Math.min(0, getRight() - rect.right);
        int iMax2 = Math.max(0, getTop() - rect.top);
        int iMin2 = Math.min(0, getBottom() - rect.bottom);
        if (iMax == 0) {
            iMax = iMin;
        }
        if (iMax2 == 0) {
            iMax2 = iMin2;
        }
        if (iMax != 0 || iMax2 != 0) {
            panBy(iMax, iMax2);
        }
    }

    private void centerBasedOnHighlightView(HighlightView highlightView) {
        Rect rect = highlightView.mDrawRect;
        float fMax = Math.max(1.0f, Math.min((getWidth() / rect.width()) * 0.6f, (getHeight() / rect.height()) * 0.6f) * getScale());
        if (Math.abs(fMax - getScale()) / fMax > 0.1d) {
            float[] fArr = {highlightView.mCropRect.centerX(), highlightView.mCropRect.centerY()};
            getImageMatrix().mapPoints(fArr);
            zoomTo(fMax, fArr[0], fArr[1], 300.0f);
        }
        ensureVisible(highlightView);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < this.mHighlightViews.size(); i++) {
            this.mHighlightViews.get(i).draw(canvas);
        }
    }

    public void add(HighlightView highlightView) {
        this.mHighlightViews.add(highlightView);
        invalidate();
    }
}
