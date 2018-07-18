/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.core.examples.java.common.helpers;

import android.content.Context;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Helper to detect taps using Android GestureDetector, and pass the taps between UI thread and
 * render thread.
 */
public final class TapHelper implements OnTouchListener {
  private final GestureDetector gestureDetector;
  private final BlockingQueue<MotionEvent> queuedSingleTaps = new ArrayBlockingQueue<>(16);

  // Scroll motion detection
  private float mScrollStartX = 0.f;
  private float mScrollStartY = 0.f;
  private float mScrollDeltaX = 0.f;
  private float mScrollDeltaY = 0.f;

  /**
   * Creates the tap helper.
   *
   * @param context the application's context.
   */
  public TapHelper(Context context) {
    gestureDetector =
        new GestureDetector(
            context,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onSingleTapUp(MotionEvent e) {
                // Queue tap if there is space. Tap is lost if queue is full.
                queuedSingleTaps.offer(e);
                return true;
              }

              @Override
              public boolean onDown(MotionEvent e) {

                // A new down motion indicates that it's a new beginning. We should clear all
                // previous taps.
                queuedSingleTaps.clear();

                // Reset scroll variables
                mScrollStartX = 0.f;
                mScrollStartY = 0.f;
                mScrollDeltaX = 0.f;
                mScrollDeltaY = 0.f;

                return true;
              }

              @Override
              public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (e1.getPointerCount() < 2 && e2.getPointerCount() < 2) {

                  // Don't insert a new motion event to the queue since we don't want to create a
                  // new anchor for every scroll action. Instead, we record the scroll delta and
                  // use the delta values to calculate the offset from the existing anchor point.
                  //queuedSingleTaps.offer(e2);

                  // Accumulate all the historical scroll delta values since the last onScroll.
                  final int historySize = e2.getHistorySize();
                  for (int h = 0; h < historySize; h++) {
                    // historical point
                    float hx = e2.getHistoricalX(0, h);
                    float hy = e2.getHistoricalY(0, h);
                    // distance between startX,startY and historical point
                    float dx = (hx - mScrollStartX);
                    float dy = (hy - mScrollStartY);

                    // make historical point the start point for next loop iteration
                    mScrollStartX = hx;
                    mScrollStartY = hy;
                    mScrollDeltaX = dx;
                    mScrollDeltaY = dy;
                  }

                  return true;
                } else {
                  // Any multi-touch should clear the taps.
                  queuedSingleTaps.clear();
                  return false;
                }
              }
            });
  }

  /**
   * Polls for a tap.
   *
   * @return if a tap was queued, a MotionEvent for the tap. Otherwise null if no taps are queued.
   */
  public MotionEvent poll() {
    return queuedSingleTaps.poll();
  }

  public float distanceX() {
    float x = mScrollDeltaX;
    mScrollDeltaX = 0;
    return x;
  }

  public float distanceY() {
    float y = mScrollDeltaY;
    mScrollDeltaY = 0;
    return y;
  }


  @Override
  public boolean onTouch(View view, MotionEvent motionEvent) {
      return gestureDetector.onTouchEvent(motionEvent);
  }
}
