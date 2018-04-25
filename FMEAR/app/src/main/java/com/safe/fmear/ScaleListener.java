package com.safe.fmear;

import android.view.ScaleGestureDetector;

public class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
    private float mScaleFactor;

    public ScaleListener(float mScaleFactor) {
        this.mScaleFactor = mScaleFactor;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        mScaleFactor *= detector.getScaleFactor();
        return true;
    }

    public float getmScaleFactor() {
        return this.mScaleFactor;
    }
}