/*
 * Copyright (C) 2016 Jared Rummler <jared.rummler@gmail.com>
 * Copyright (C) 2016 Tim Malseed
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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * A base {@link RecyclerView}, which does the following:
 *
 * <ul>
 * <li> NOT intercept a touch unless the scrolling velocity is below a predefined threshold.
 * <li> Enable fast scroller.
 * </ul>
 */
public class FastScrollRecyclerView extends RecyclerView implements RecyclerView.OnItemTouchListener {

  private static final int SCROLL_DELTA_THRESHOLD_DP = 4;
  private static final int DEFAULT_HIDE_DELAY = 1000;

  private final ScrollPositionState scrollPositionState = new ScrollPositionState();
  private final Rect backgroundPadding = new Rect();
  /*package*/ FastScrollBar fastScrollBar;
  /*package*/ boolean fastScrollAlwaysEnabled;
  private float deltaThreshold;
  private int hideDelay;
  /*package*/ int lastDy; // Keeps the last known scrolling delta/velocity along y-axis.
  private int downX;
  private int downY;
  private int lastY;

  final Runnable hide = new Runnable() {

    @Override public void run() {
      if (!fastScrollBar.isDraggingThumb()) {
        fastScrollBar.animateScrollbar(false);
      }
    }
  };

  public FastScrollRecyclerView(Context context) {
    this(context, null);
  }

  public FastScrollRecyclerView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public FastScrollRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.FastScrollRecyclerView);
    fastScrollAlwaysEnabled = ta.getBoolean(R.styleable.FastScrollRecyclerView_fastScrollAlwaysEnabled, false);
    hideDelay = ta.getInt(R.styleable.FastScrollRecyclerView_fastScrollHideDelay, DEFAULT_HIDE_DELAY);
    ta.recycle();
    deltaThreshold = getResources().getDisplayMetrics().density * SCROLL_DELTA_THRESHOLD_DP;
    fastScrollBar = new FastScrollBar(this, attrs);
    fastScrollBar.setDetachThumbOnFastScroll();
    addOnScrollListener(new OnScrollListener() {

      @Override public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
        if (fastScrollAlwaysEnabled) return;
        switch (newState) {
          case SCROLL_STATE_DRAGGING:
            removeCallbacks(hide);
            fastScrollBar.animateScrollbar(true);
            break;
          case SCROLL_STATE_IDLE:
            hideScrollBar();
            break;
        }
      }

      @Override public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        lastDy = dy;
        onUpdateScrollbar(dy);
      }
    });
  }

  public void reset() {
    fastScrollBar.reattachThumbToScroll();
  }

  @Override protected void onFinishInflate() {
    super.onFinishInflate();
    addOnItemTouchListener(this);
  }

  /**
   * We intercept the touch handling only to support fast scrolling when initiated from the
   * scroll bar.  Otherwise, we fall back to the default RecyclerView touch handling.
   */
  @Override public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent ev) {
    return handleTouchEvent(ev);
  }

  @Override public void onTouchEvent(RecyclerView rv, MotionEvent ev) {
    handleTouchEvent(ev);
  }

  /**
   * Handles the touch event and determines whether to show the fast scroller (or updates it if
   * it is already showing).
   */
  private boolean handleTouchEvent(MotionEvent ev) {
    int action = ev.getAction();
    int x = (int) ev.getX();
    int y = (int) ev.getY();
    switch (action) {
      case MotionEvent.ACTION_DOWN:
        // Keep track of the down positions
        downX = x;
        downY = lastY = y;
        if (shouldStopScroll(ev)) {
          stopScroll();
        }
        fastScrollBar.handleTouchEvent(ev, downX, downY, lastY);
        break;
      case MotionEvent.ACTION_MOVE:
        lastY = y;
        fastScrollBar.handleTouchEvent(ev, downX, downY, lastY);
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        onFastScrollCompleted();
        fastScrollBar.handleTouchEvent(ev, downX, downY, lastY);
        break;
    }
    return fastScrollBar.isDraggingThumb();
  }

  @Override public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
    // DO NOT REMOVE, NEEDED IMPLEMENTATION FOR M BUILDS
  }

  /**
   * Returns whether this {@link MotionEvent} should trigger the scroll to be stopped.
   */
  protected boolean shouldStopScroll(MotionEvent ev) {
    if (ev.getAction() == MotionEvent.ACTION_DOWN) {
      if ((Math.abs(lastDy) < deltaThreshold && getScrollState() != RecyclerView.SCROLL_STATE_IDLE)) {
        // now the touch events are being passed to the {@link WidgetCell} until the
        // touch sequence goes over the touch slop.
        return true;
      }
    }
    return false;
  }

  public void updateBackgroundPadding(Rect padding) {
    backgroundPadding.set(padding);
  }

  public Rect getBackgroundPadding() {
    return backgroundPadding;
  }

  /**
   * Returns the scroll bar width when the user is scrolling.
   */
  public int getMaxScrollbarWidth() {
    return fastScrollBar.getThumbMaxWidth();
  }

  /**
   * Returns the available scroll height:
   * AvailableScrollHeight = Total height of the all items - last page height
   *
   * This assumes that all rows are the same height.
   */
  protected int getAvailableScrollHeight(int rowCount, int rowHeight) {
    int visibleHeight = getHeight() - backgroundPadding.top - backgroundPadding.bottom;
    int scrollHeight = getPaddingTop() + rowCount * rowHeight + getPaddingBottom();
    return scrollHeight - visibleHeight;
  }

  /**
   * Returns the available scroll bar height:
   * AvailableScrollBarHeight = Total height of the visible view - thumb height
   */
  protected int getAvailableScrollBarHeight() {
    int visibleHeight = getHeight() - backgroundPadding.top - backgroundPadding.bottom;
    return visibleHeight - fastScrollBar.getThumbHeight();
  }

  public boolean isFastScrollAlwaysEnabled() {
    return fastScrollAlwaysEnabled;
  }

  protected void hideScrollBar() {
    if (!fastScrollAlwaysEnabled) {
      removeCallbacks(hide);
      postDelayed(hide, hideDelay);
    }
  }

  public void setThumbActiveColor(@ColorInt int color) {
    fastScrollBar.setThumbActiveColor(color);
  }

  public void setTrackInactiveColor(@ColorInt int color) {
    fastScrollBar.setThumbInactiveColor(color);
  }

  public void setPopupBackgroundColor(@ColorInt int color) {
    fastScrollBar.setPopupBackgroundColor(color);
  }

  public void setPopupTextColor(@ColorInt int color) {
    fastScrollBar.setPopupTextColor(color);
  }

  public FastScrollBar getFastScrollBar() {
    return fastScrollBar;
  }

  @Override
  public void draw(Canvas canvas) {
    super.draw(canvas);

    // Draw the ScrollBar AFTER the ItemDecorations are drawn over
    onUpdateScrollbar(0);
    fastScrollBar.draw(canvas);
  }

  /**
   * Updates the scrollbar thumb offset to match the visible scroll of the recycler view.  It does
   * this by mapping the available scroll area of the recycler view to the available space for the
   * scroll bar.
   *
   * @param scrollPosState
   *     the current scroll position
   * @param rowCount
   *     the number of rows, used to calculate the total scroll height (assumes that
   *     all rows are the same height)
   */
  protected void synchronizeScrollBarThumbOffsetToViewScroll(ScrollPositionState scrollPosState, int rowCount) {
    // Only show the scrollbar if there is height to be scrolled
    int availableScrollBarHeight = getAvailableScrollBarHeight();
    int availableScrollHeight = getAvailableScrollHeight(rowCount, scrollPosState.rowHeight);
    if (availableScrollHeight <= 0) {
      fastScrollBar.setThumbOffset(-1, -1);
      return;
    }

    // Calculate the current scroll position, the scrollY of the recycler view accounts for the
    // view padding, while the scrollBarY is drawn right up to the background padding (ignoring
    // padding)
    int scrollY = getPaddingTop() +
        Math.round(((scrollPosState.rowIndex - scrollPosState.rowTopOffset) * scrollPosState.rowHeight));
    int scrollBarY =
        backgroundPadding.top + (int) (((float) scrollY / availableScrollHeight) * availableScrollBarHeight);

    // Calculate the position and size of the scroll bar
    int scrollBarX;
    if (Utilities.isRtl(getResources())) {
      scrollBarX = backgroundPadding.left;
    } else {
      scrollBarX = getWidth() - backgroundPadding.right - fastScrollBar.getThumbWidth();
    }
    fastScrollBar.setThumbOffset(scrollBarX, scrollBarY);
  }

  /**
   * <p>Maps the touch (from 0..1) to the adapter position that should be visible.</p>
   *
   * <p>Override in each subclass of this base class.</p>
   */
  public String scrollToPositionAtProgress(float touchFraction) {
    int itemCount = getAdapter().getItemCount();
    if (itemCount == 0) {
      return "";
    }
    int spanCount = 1;
    int rowCount = itemCount;
    if (getLayoutManager() instanceof GridLayoutManager) {
      spanCount = ((GridLayoutManager) getLayoutManager()).getSpanCount();
      rowCount = (int) Math.ceil((double) rowCount / spanCount);
    }

    // Stop the scroller if it is scrolling
    stopScroll();

    getCurScrollState(scrollPositionState);

    float itemPos = itemCount * touchFraction;

    int availableScrollHeight = getAvailableScrollHeight(rowCount, scrollPositionState.rowHeight);

    //The exact position of our desired item
    int exactItemPos = (int) (availableScrollHeight * touchFraction);

    //Scroll to the desired item. The offset used here is kind of hard to explain.
    //If the position we wish to scroll to is, say, position 10.5, we scroll to position 10,
    //and then offset by 0.5 * rowHeight. This is how we achieve smooth scrolling.
    LinearLayoutManager layoutManager = ((LinearLayoutManager) getLayoutManager());
    layoutManager.scrollToPositionWithOffset(spanCount * exactItemPos / scrollPositionState.rowHeight,
        -(exactItemPos % scrollPositionState.rowHeight));

    if (!(getAdapter() instanceof SectionedAdapter)) {
      return "";
    }

    int posInt = (int) ((touchFraction == 1) ? itemPos - 1 : itemPos);

    SectionedAdapter sectionedAdapter = (SectionedAdapter) getAdapter();
    return sectionedAdapter.getSectionName(posInt);
  }

  /**
   * <p>Updates the bounds for the scrollbar.</p>
   *
   * <p>Override in each subclass of this base class.</p>
   */
  public void onUpdateScrollbar(int dy) {
    int rowCount = getAdapter().getItemCount();
    if (getLayoutManager() instanceof GridLayoutManager) {
      int spanCount = ((GridLayoutManager) getLayoutManager()).getSpanCount();
      rowCount = (int) Math.ceil((double) rowCount / spanCount);
    }
    // Skip early if, there are no items.
    if (rowCount == 0) {
      fastScrollBar.setThumbOffset(-1, -1);
      return;
    }

    // Skip early if, there no child laid out in the container.
    getCurScrollState(scrollPositionState);
    if (scrollPositionState.rowIndex < 0) {
      fastScrollBar.setThumbOffset(-1, -1);
      return;
    }

    synchronizeScrollBarThumbOffsetToViewScroll(scrollPositionState, rowCount);
  }

  /**
   * <p>Override in each subclass of this base class.</p>
   */
  public void onFastScrollCompleted() {
  }

  /**
   * Returns information about the item that the recycler view is currently scrolled to.
   */
  protected void getCurScrollState(ScrollPositionState stateOut) {
    stateOut.rowIndex = -1;
    stateOut.rowTopOffset = -1;
    stateOut.rowHeight = -1;

    // Return early if there are no items
    int rowCount = getAdapter().getItemCount();
    if (rowCount == 0) {
      return;
    }

    View child = getChildAt(0);
    if (child == null) {
      return;
    }

    stateOut.rowIndex = getChildPosition(child);
    if (getLayoutManager() instanceof GridLayoutManager) {
      stateOut.rowIndex = stateOut.rowIndex / ((GridLayoutManager) getLayoutManager()).getSpanCount();
    }
    stateOut.rowTopOffset = getLayoutManager().getDecoratedTop(child) / (float) child.getHeight();
    stateOut.rowHeight = calculateRowHeight(child.getHeight());
  }

  /**
   * Calculates the row height based on the average of the visible children, to handle scrolling
   * through children with different heights gracefully
   */
  protected int calculateRowHeight(int fallbackHeight) {
    LayoutManager layoutManager = getLayoutManager();

    if (layoutManager instanceof LinearLayoutManager) {
      final int firstVisiblePosition = ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
      final int lastVisiblePosition = ((LinearLayoutManager) layoutManager).findLastVisibleItemPosition();

      if (lastVisiblePosition > firstVisiblePosition) {
        final int height = getHeight();
        final int paddingTop = getPaddingTop();
        final int paddingBottom = getPaddingBottom();

        // How many rows are visible, like 10.5f for 10 rows completely and one halfway visible
        float visibleRows = 0f;

        for (int position = firstVisiblePosition; position <= lastVisiblePosition; position++) {
          ViewHolder viewHolder = findViewHolderForLayoutPosition(position);
          if (viewHolder == null || viewHolder.itemView == null) {
            continue;
          }

          final View itemView = viewHolder.itemView;
          final int itemHeight = itemView.getHeight();
          if (itemHeight == 0) {
            continue;
          }

          // Finds how much of the itemView is actually visible.
          // This allows smooth changes of the scrollbar thumb height
          final int visibleHeight = itemHeight
              - Math.max(0, paddingBottom - layoutManager.getDecoratedTop(itemView)) // How much is cut at the top
              - Math.max(0, paddingBottom + layoutManager.getDecoratedBottom(itemView) - height); // How much is cut at the bottom

          visibleRows += visibleHeight / (float) itemHeight;
        }

        return Math.round((height - (paddingTop + paddingBottom)) / visibleRows);
      }
    }

    return fallbackHeight;
  }

  /**
   * Iterface to implement in your {@link RecyclerView.Adapter} to show a popup next to the scroller
   */
  public interface SectionedAdapter {

    /**
     * @param position
     *     the item position
     * @return the section name for this item
     */
    @NonNull String getSectionName(int position);
  }

  /**
   * The current scroll state of the recycler view.  We use this in onUpdateScrollbar()
   * and scrollToPositionAtProgress() to determine the scroll position of the recycler view so
   * that we can calculate what the scroll bar looks like, and where to jump to from the fast
   * scroller.
   */
  public static class ScrollPositionState {

    // The index of the first visible row
    public int rowIndex;
    // The offset of the first visible row, in percentage of the height
    public float rowTopOffset;
    // The height of a given row (they are currently all the same height)
    public int rowHeight;
  }

}