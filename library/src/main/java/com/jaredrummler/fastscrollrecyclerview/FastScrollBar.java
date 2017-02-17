/*
 * Copyright (C) 2016 Jared Rummler <jared.rummler@gmail.com>
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.jaredrummler.fastscrollrecyclerview;

import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.annotation.ColorInt;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

public class FastScrollBar {

  private final static int MAX_TRACK_ALPHA = 30;
  private final static int SCROLL_BAR_VIS_DURATION = 150;

  private final Rect invalidateRect = new Rect();
  private final Rect tmpRect = new Rect();

  /*package*/ final Point thumbOffset = new Point(-1, -1);
  private final Path thumbPath = new Path();

  /*package*/ FastScrollRecyclerView recyclerView;
  private FastScrollPopup fastScrollPopup;

  private AnimatorSet scrollbarAnimator;

  private int thumbInactiveColor;
  private int thumbActiveColor;

  /*package*/ Paint thumbPaint;
  private int thumbMinWidth;
  private int thumbMaxWidth;
  /*package*/ int thumbWidth;
  /*package*/ int thumbHeight;
  private int thumbCurvature;

  private Paint trackPaint;
  private int trackWidth;
  private float lastTouchY;

  // The inset is the buffer around which a point will still register as a click on the scrollbar
  private int touchInset;

  private boolean isDragging;
  private boolean isThumbDetached;
  private boolean canThumbDetach;
  private boolean ignoreDragGesture;
  private boolean showThumbCurvature;

  // This is the offset from the top of the scrollbar when the user first starts touching.
  // To prevent jumping, this offset is applied as the user scrolls.
  private int touchOffset;

  public FastScrollBar(FastScrollRecyclerView rv, AttributeSet attrs) {
    TypedArray ta = rv.getContext().obtainStyledAttributes(attrs, R.styleable.FastScrollRecyclerView);
    Resources res = rv.getResources();
    showThumbCurvature = ta.getBoolean(R.styleable.FastScrollRecyclerView_fastScrollThumbCurvatureEnabled, false);
    thumbInactiveColor = ta.getColor(R.styleable.FastScrollRecyclerView_fastScrollThumbInactiveColor,
        res.getColor(R.color.fastscroll_thumb_inactive_color));
    thumbActiveColor = ta.getColor(R.styleable.FastScrollRecyclerView_fastScrollThumbActiveColor,
        res.getColor(R.color.fastscroll_thumb_active_color));
    int trackColor = ta.getColor(R.styleable.FastScrollRecyclerView_fastScrollTrackColor, Color.BLACK);
    ta.recycle();
    recyclerView = rv;
    fastScrollPopup = new FastScrollPopup(rv, attrs);
    trackPaint = new Paint();
    trackPaint.setColor(trackColor);
    trackPaint.setAlpha(MAX_TRACK_ALPHA);
    thumbPaint = new Paint();
    thumbPaint.setAntiAlias(true);
    thumbPaint.setColor(thumbInactiveColor);
    thumbPaint.setStyle(Paint.Style.FILL);
    thumbWidth = thumbMinWidth = res.getDimensionPixelSize(R.dimen.fastscroll_thumb_min_width);
    thumbMaxWidth = res.getDimensionPixelSize(R.dimen.fastscroll_thumb_max_width);
    thumbHeight = res.getDimensionPixelSize(R.dimen.fastscroll_thumb_height);
    thumbCurvature = showThumbCurvature ? thumbMaxWidth - thumbMinWidth : 0;
    touchInset = res.getDimensionPixelSize(R.dimen.fastscroll_thumb_touch_inset);
    if (rv.isFastScrollAlwaysEnabled()) {
      animateScrollbar(true);
    }
  }

  public void setDetachThumbOnFastScroll() {
    canThumbDetach = true;
  }

  public void reattachThumbToScroll() {
    isThumbDetached = false;
  }

  public void setThumbOffset(int x, int y) {
    if (thumbOffset.x == x && thumbOffset.y == y) {
      return;
    }
    invalidateRect
        .set(thumbOffset.x - thumbCurvature, thumbOffset.y, thumbOffset.x + thumbWidth, thumbOffset.y + thumbHeight);
    thumbOffset.set(x, y);
    updateThumbPath();
    invalidateRect
        .union(thumbOffset.x - thumbCurvature, thumbOffset.y, thumbOffset.x + thumbWidth, thumbOffset.y + thumbHeight);
    recyclerView.invalidate(invalidateRect);
  }

  public Point getThumbOffset() {
    return thumbOffset;
  }

  // Setter/getter for the thumb bar width for animations
  public void setThumbWidth(int width) {
    invalidateRect
        .set(thumbOffset.x - thumbCurvature, thumbOffset.y, thumbOffset.x + thumbWidth, thumbOffset.y + thumbHeight);
    thumbWidth = width;
    updateThumbPath();
    invalidateRect
        .union(thumbOffset.x - thumbCurvature, thumbOffset.y, thumbOffset.x + thumbWidth, thumbOffset.y + thumbHeight);
    recyclerView.invalidate(invalidateRect);
  }

  public int getThumbWidth() {
    return thumbWidth;
  }

  // Setter/getter for the track bar width for animations
  public void setTrackWidth(int width) {
    invalidateRect.set(thumbOffset.x - thumbCurvature, 0, thumbOffset.x + thumbWidth, recyclerView.getHeight());
    trackWidth = width;
    updateThumbPath();
    invalidateRect.union(thumbOffset.x - thumbCurvature, 0, thumbOffset.x + thumbWidth, recyclerView.getHeight());
    recyclerView.invalidate(invalidateRect);
  }

  public int getTrackWidth() {
    return trackWidth;
  }

  public int getThumbHeight() {
    return thumbHeight;
  }

  public int getThumbMaxWidth() {
    return thumbMaxWidth;
  }

  public float getLastTouchY() {
    return lastTouchY;
  }

  public boolean isDraggingThumb() {
    return isDragging;
  }

  public boolean isThumbDetached() {
    return isThumbDetached;
  }

  public void setThumbActiveColor(@ColorInt int color) {
    thumbActiveColor = color;
    thumbPaint.setColor(color);
    recyclerView.invalidate(invalidateRect);
  }

  public void setThumbInactiveColor(@ColorInt int color) {
    thumbInactiveColor = color;
    thumbPaint.setColor(color);
    recyclerView.invalidate(invalidateRect);
  }

  public void setTrackColor(@ColorInt int color) {
    trackPaint.setColor(color);
    recyclerView.invalidate(invalidateRect);
  }

  public void setPopupBackgroundColor(@ColorInt int color) {
    fastScrollPopup.setBackgroundColor(color);
  }

  public void setPopupTextColor(@ColorInt int color) {
    fastScrollPopup.setTextColor(color);
  }

  public FastScrollPopup getFastScrollPopup() {
    return fastScrollPopup;
  }

  /**
   * Handles the touch event and determines whether to show the fast scroller (or updates it if
   * it is already showing).
   */
  protected void handleTouchEvent(MotionEvent ev, int downX, int downY, int lastY) {
    ViewConfiguration config = ViewConfiguration.get(recyclerView.getContext());

    int action = ev.getAction();
    int y = (int) ev.getY();
    switch (action) {
      case MotionEvent.ACTION_DOWN:
        if (isNearThumb(downX, downY)) {
          touchOffset = downY - thumbOffset.y;
        }
        break;
      case MotionEvent.ACTION_MOVE:
        // Check if we should start scrolling, but ignore this fastscroll gesture if we have
        // exceeded some fixed movement
        ignoreDragGesture |= Math.abs(y - downY) > config.getScaledPagingTouchSlop();
        if (!isDragging && !ignoreDragGesture && isNearThumb(downX, lastY) &&
            Math.abs(y - downY) > config.getScaledTouchSlop()) {
          recyclerView.getParent().requestDisallowInterceptTouchEvent(true);
          isDragging = true;
          if (canThumbDetach) {
            isThumbDetached = true;
          }
          touchOffset += (lastY - downY);
          fastScrollPopup.animateVisibility(true);
          animateScrollbar(true);
        }
        if (isDragging) {
          // Update the fastscroller section name at this touch position
          int top = recyclerView.getBackgroundPadding().top;
          int bottom = recyclerView.getHeight() - recyclerView.getBackgroundPadding().bottom - thumbHeight;
          float boundedY = (float) Math.max(top, Math.min(bottom, y - touchOffset));
          String sectionName = recyclerView.scrollToPositionAtProgress((boundedY - top) / (bottom - top));
          fastScrollPopup.setSectionName(sectionName);
          fastScrollPopup.animateVisibility(!sectionName.isEmpty());
          recyclerView.invalidate(fastScrollPopup.updateFastScrollerBounds(recyclerView, lastY));
          lastTouchY = boundedY;
        }
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        touchOffset = 0;
        lastTouchY = 0;
        ignoreDragGesture = false;
        if (isDragging) {
          isDragging = false;
          fastScrollPopup.animateVisibility(false);
          recyclerView.hideScrollBar();
        }
        break;
    }
  }

  protected void draw(Canvas canvas) {
    if (thumbOffset.x < 0 || thumbOffset.y < 0) {
      return;
    }

    // Draw the scroll bar track and thumb
    if (trackPaint.getAlpha() > 0) {
      canvas.drawRect(thumbOffset.x, 0, thumbOffset.x + thumbWidth, recyclerView.getHeight(), trackPaint);
    }
    canvas.drawPath(thumbPath, thumbPaint);

    // Draw the popup
    fastScrollPopup.draw(canvas);
  }

  /**
   * Animates the width and color of the scrollbar.
   */
  protected void animateScrollbar(boolean isScrolling) {
    if (scrollbarAnimator != null) {
      scrollbarAnimator.cancel();
    }

    scrollbarAnimator = new AnimatorSet();
    ObjectAnimator trackWidthAnim = ObjectAnimator.ofInt(this, "trackWidth",
        isScrolling ? thumbMaxWidth : thumbMinWidth);
    ObjectAnimator thumbWidthAnim = ObjectAnimator.ofInt(this, "thumbWidth",
        isScrolling ? thumbMaxWidth : thumbMinWidth);
    scrollbarAnimator.playTogether(trackWidthAnim, thumbWidthAnim);
    if (thumbActiveColor != thumbInactiveColor) {
      ValueAnimator colorAnimation = ValueAnimator
          .ofObject(new ArgbEvaluator(), thumbPaint.getColor(), isScrolling ? thumbActiveColor : thumbInactiveColor);
      colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

        @Override
        public void onAnimationUpdate(ValueAnimator animator) {
          thumbPaint.setColor((Integer) animator.getAnimatedValue());
          recyclerView
              .invalidate(thumbOffset.x, thumbOffset.y, thumbOffset.x + thumbWidth, thumbOffset.y + thumbHeight);
        }
      });
      scrollbarAnimator.play(colorAnimation);
    }
    scrollbarAnimator.setDuration(SCROLL_BAR_VIS_DURATION);
    scrollbarAnimator.start();
  }

  /**
   * Updates the path for the thumb drawable.
   */
  private void updateThumbPath() {
    thumbCurvature = showThumbCurvature ? thumbMaxWidth - thumbWidth : 0;
    thumbPath.reset();
    thumbPath.moveTo(thumbOffset.x + thumbWidth, thumbOffset.y);                   // tr
    thumbPath.lineTo(thumbOffset.x + thumbWidth, thumbOffset.y + thumbHeight);     // br
    thumbPath.lineTo(thumbOffset.x, thumbOffset.y + thumbHeight);                  // bl
    thumbPath.cubicTo(thumbOffset.x, thumbOffset.y + thumbHeight,
        thumbOffset.x - thumbCurvature,
        thumbOffset.y + thumbHeight / 2,
        thumbOffset.x, thumbOffset.y);                                             // bl2tl
    thumbPath.close();
  }

  /**
   * Returns whether the specified points are near the scroll bar bounds.
   */
  private boolean isNearThumb(int x, int y) {
    tmpRect.set(thumbOffset.x, thumbOffset.y, thumbOffset.x + thumbWidth,
        thumbOffset.y + thumbHeight);
    tmpRect.inset(touchInset, touchInset);
    return tmpRect.contains(x, y);
  }

}
