package com.safe.fmear;

import com.almeros.android.multitouch.RotateGestureDetector;

public class RotationListener extends RotateGestureDetector.SimpleOnRotateGestureListener {
    private float mRotationDegrees = 0.0f;

    @Override
    public boolean onRotate(RotateGestureDetector detector) {
        mRotationDegrees -= detector.getRotationDegreesDelta();
        return true;
    }

    public float getRotationDegrees() {
        return this.mRotationDegrees;
    }
}
