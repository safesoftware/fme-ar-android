package com.safe.fmear;

import android.view.ScaleGestureDetector;

public class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
    private float mScaleFactor = 1.0f;

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        mScaleFactor *= detector.getScaleFactor();
        return true;
    }

    public float getmScaleFactor() {
        return this.mScaleFactor;
    }

    public void setmScaleFactor(final float scaleFactor) {
        mScaleFactor = scaleFactor;
    }
}