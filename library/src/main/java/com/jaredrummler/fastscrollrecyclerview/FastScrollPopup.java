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

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.util.AttributeSet;

/**
 * The fast scroller popup that shows the section name the list will jump to.
 */
public class FastScrollPopup {

  private static final float FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR = 1.5f;

  private final Rect backgroundBounds = new Rect(); // The absolute bounds of the fast scroller bg
  private final Rect invalidateRect = new Rect();
  private final Rect tmpRect = new Rect();
  private final Rect textBounds = new Rect();

  private FastScrollRecyclerView recyclerView;
  private Animator alphaAnimator;
  private Resources resources;
  private Drawable background;
  private Paint textPaint;
  private String sectionName;
  private int originalBackgroundSize;
  private float alpha;
  private boolean visible;

  public FastScrollPopup(FastScrollRecyclerView rv, AttributeSet attrs) {
    TypedArray ta = rv.getContext().obtainStyledAttributes(attrs, R.styleable.FastScrollRecyclerView);
    recyclerView = rv;
    resources = rv.getResources();
    int bgColor = ta.getColor(R.styleable.FastScrollRecyclerView_fastScrollPopupBackgroundColor, Color.TRANSPARENT);
    int textColor = ta.getColor(R.styleable.FastScrollRecyclerView_fastScrollPopupTextColor, Color.WHITE);
    float textSize = ta.getDimension(R.styleable.FastScrollRecyclerView_fastScrollTextSize, resources.getDimensionPixelSize(R.dimen.fastscroll_popup_text_size));
    originalBackgroundSize = (int) textSize + (int) ta.getDimension(R.styleable.FastScrollRecyclerView_fastScrollPopupPadding, resources.getDimensionPixelSize(R.dimen.fastscroll_popup_default_padding));
    background = resources.getDrawable(R.drawable.fastscroll_popup_bg);
    if (bgColor != Color.TRANSPARENT) {
      background = background.mutate();
      background.setColorFilter(bgColor, PorterDuff.Mode.SRC_IN);
    }
    background.setBounds(0, 0, originalBackgroundSize, originalBackgroundSize);
    textPaint = new Paint();
    textPaint.setColor(textColor);
    textPaint.setAntiAlias(true);
    textPaint.setTextSize(textSize);
    ta.recycle();
  }

  /**
   * Sets the section name.
   */
  protected void setSectionName(String sectionName) {
    if (!sectionName.equals(this.sectionName)) {
      this.sectionName = sectionName;
      textPaint.getTextBounds(sectionName, 0, sectionName.length(), textBounds);
      // Update the width to use measureText since that is more accurate
      textBounds.right = (int) (textBounds.left + textPaint.measureText(sectionName));
    }
  }

  /**
   * Updates the bounds for the fast scroller.
   *
   * @return the invalidation rect for this update.
   */
  protected Rect updateFastScrollerBounds(FastScrollRecyclerView rv, int lastTouchY) {
    invalidateRect.set(backgroundBounds);

    if (isVisible()) {
      // Calculate the dimensions and position of the fast scroller popup
      int edgePadding = rv.getMaxScrollbarWidth();
      int bgPadding = (originalBackgroundSize - textBounds.height()) / 2;
      int bgHeight = originalBackgroundSize;
      int bgWidth = Math.max(originalBackgroundSize, textBounds.width() + (2 * bgPadding));
      if (Utilities.isRtl(resources)) {
        backgroundBounds.left = rv.getBackgroundPadding().left + (2 * rv.getMaxScrollbarWidth());
        backgroundBounds.right = backgroundBounds.left + bgWidth;
      } else {
        backgroundBounds.right = rv.getWidth() - rv.getBackgroundPadding().right - (2 * rv.getMaxScrollbarWidth());
        backgroundBounds.left = backgroundBounds.right - bgWidth;
      }
      backgroundBounds.top = lastTouchY - (int) (FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR * bgHeight);
      backgroundBounds.top =
          Math.max(edgePadding, Math.min(backgroundBounds.top, rv.getHeight() - edgePadding - bgHeight));
      backgroundBounds.bottom = backgroundBounds.top + bgHeight;
    } else {
      backgroundBounds.setEmpty();
    }

    // Combine the old and new fast scroller bounds to create the full invalidate rect
    invalidateRect.union(backgroundBounds);
    return invalidateRect;
  }

  /**
   * Animates the visibility of the fast scroller popup.
   */
  public void animateVisibility(boolean visible) {
    if (this.visible != visible) {
      this.visible = visible;
      if (alphaAnimator != null) {
        alphaAnimator.cancel();
      }
      alphaAnimator = ObjectAnimator.ofFloat(this, "alpha", visible ? 1f : 0f);
      alphaAnimator.setDuration(visible ? 200 : 150);
      alphaAnimator.start();
    }
  }

  // Setter/getter for the popup alpha for animations
  public void setAlpha(float alpha) {
    this.alpha = alpha;
    recyclerView.invalidate(backgroundBounds);
  }

  public float getAlpha() {
    return alpha;
  }

  public void setBackgroundColor(@ColorInt int color) {
    background = background.mutate();
    background.setColorFilter(color, PorterDuff.Mode.SRC_IN);
  }

  public void setTextColor(@ColorInt int color) {
    textPaint.setColor(color);
  }

  public int getHeight() {
    return originalBackgroundSize;
  }

  protected void draw(Canvas c) {
    if (isVisible()) {
      // Draw the fast scroller popup
      int restoreCount = c.save(Canvas.MATRIX_SAVE_FLAG);
      c.translate(backgroundBounds.left, backgroundBounds.top);
      tmpRect.set(backgroundBounds);
      tmpRect.offsetTo(0, 0);
      background.setBounds(tmpRect);
      background.setAlpha((int) (alpha * 255));
      background.draw(c);
      textPaint.setAlpha((int) (alpha * 255));
      c.drawText(sectionName, (backgroundBounds.width() - textBounds.width()) / 2,
          backgroundBounds.height() - (backgroundBounds.height() - textBounds.height()) / 2,
          textPaint);
      c.restoreToCount(restoreCount);
    }
  }

  public boolean isVisible() {
    return (alpha > 0f) && (sectionName != null);
  }

}
