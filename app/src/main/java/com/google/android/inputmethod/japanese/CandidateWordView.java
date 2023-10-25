// Copyright 2010-2018, Google Inc.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package org.mozc.android.inputmethod.japanese;

import org.mozc.android.inputmethod.japanese.accessibility.AccessibilityUtil;
import org.mozc.android.inputmethod.japanese.accessibility.CandidateWindowAccessibilityDelegate;
import org.mozc.android.inputmethod.japanese.emoji.EmojiProviderType;
import org.mozc.android.inputmethod.japanese.keyboard.BackgroundDrawableFactory;
import org.mozc.android.inputmethod.japanese.keyboard.BackgroundDrawableFactory.DrawableType;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidates.CandidateList;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidates.CandidateWord;
import org.mozc.android.inputmethod.japanese.ui.CandidateLayout;
import org.mozc.android.inputmethod.japanese.ui.CandidateLayout.Row;
import org.mozc.android.inputmethod.japanese.ui.CandidateLayout.Span;
import org.mozc.android.inputmethod.japanese.ui.CandidateLayoutRenderer;
import org.mozc.android.inputmethod.japanese.ui.CandidateLayouter;
import org.mozc.android.inputmethod.japanese.ui.SnapScroller;
import org.mozc.android.inputmethod.japanese.view.CarrierEmojiRenderHelper;
import org.mozc.android.inputmethod.japanese.view.Skin;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import androidx.core.view.ViewCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EdgeEffect;

import javax.annotation.Nullable;

/**
 * A view for candidate words.
 *
 */
// TODO(matsuzakit): Optional is introduced partially. Complete introduction.
abstract class CandidateWordView extends View implements MemoryManageable {

  /**
   * Handles gestures to scroll candidate list and choose a candidate.
   */
  class CandidateWordGestureDetector {

    class CandidateWordViewGestureListener extends SimpleOnGestureListener {

      @Override
      public boolean onFling(
          MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
        float velocity = orientationTrait.projectVector(velocityX, velocityY);
        // As fling is started, current action is not tapping.
        // Reset pressing state so that candidate selection is not triggered at touch up event.
        reset();
        // Fling makes scrolling.
        scroller.fling(-(int) velocity);
        invalidate();
        return true;
      }

      @Override
      public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        float distance = orientationTrait.projectVector(distanceX, distanceY);
        int oldScrollPosition = scroller.getScrollPosition();
        int oldMaxScrollPosition = scroller.getMaxScrollPosition();
        scroller.scrollBy((int) distance);
        orientationTrait.scrollTo(CandidateWordView.this, scroller.getScrollPosition());
        // As scroll is started, current action is not tapping.
        // Reset pressing state so that candidate selection is not triggered at touch up event.
        reset();

        // Edge effect. Now, in production, we only support vertical scroll.
        if (oldScrollPosition + distance < 0) {
          topEdgeEffect.onPull(distance / getHeight());
          if (!bottomEdgeEffect.isFinished()) {
            bottomEdgeEffect.onRelease();
          }
        } else if (oldScrollPosition + distance > oldMaxScrollPosition) {
          bottomEdgeEffect.onPull(distance / getHeight());
          if (!topEdgeEffect.isFinished()) {
            topEdgeEffect.onRelease();
          }
        }

        invalidate();
        return true;
      }
    }

    // GestureDetector cannot handle all complex gestures which we need.
    // But we use GestureDetector for some gesture recognition
    // because implementing whole gesture detection logic by ourselves is a bit tedious.
    private final GestureDetector gestureDetector;

    /**
     * Points to an instance of currently pressed candidate word. Or {@code null} if any
     * candidates aren't pressed.
     */
    @Nullable
    private CandidateWord pressedCandidate;
    private final RectF candidateRect = new RectF();
    private Optional<Integer> pressedRowIndex = Optional.absent();

    public CandidateWordGestureDetector(Context context) {
      gestureDetector = new GestureDetector(context, new CandidateWordViewGestureListener());
    }

    private void pressCandidate(int rowIndex, Span span) {
      Row row = calculatedLayout.getRowList().get(rowIndex);
      pressedRowIndex = Optional.of(rowIndex);
      pressedCandidate = span.getCandidateWord().orNull();
      // TODO(yamaguchi):maybe better to make this rect larger by several pixels to avoid that
      // users fail to select a candidate by unconscious small movement of tap point.
      // (i.e. give hysterisis for noise reduction)
      // Needs UX study.
      candidateRect.set(span.getLeft(), row.getTop(),
                        span.getRight(), row.getTop() + row.getHeight());
    }

    void reset() {
      pressedCandidate = null;
      pressedRowIndex = Optional.absent();
      // NOTE: candidateRect doesn't need reset.
    }

    CandidateWord getPressedCandidate() {
      return pressedCandidate;
    }

    /**
     * Checks if a down event is fired inside a candidate rectangle.
     * If so, begin pressing it.
     *
     * It is assumed that rows are stored in up-to-down order,
     * and spans are in left-to-right order.
     *
     * @param scrolledX X coordinate of down event point including scroll offset
     * @param scrolledY Y coordinate of down event point including scroll offset
     * @return true if the down event is fired inside a candidate rectangle.
     */
    private boolean findCandidateAndPress(float scrolledX, float scrolledY) {
      if (calculatedLayout == null) {
        return false;
      }
      for (int rowIndex = 0; rowIndex < calculatedLayout.getRowList().size(); ++rowIndex) {
        Row row = calculatedLayout.getRowList().get(rowIndex);
        if (scrolledY < row.getTop()) {
          break;
        }
        if (scrolledY >= row.getTop() + row.getHeight()) {
          continue;
        }
        for (Span span : row.getSpanList()) {
          if (scrolledX < span.getLeft()) {
            break;
          }
          if (scrolledX >= span.getRight()) {
            continue;
          }
          pressCandidate(rowIndex, span);
          invalidate();
          return true;
        }
        return false;
      }
      return false;
    }

    boolean onTouchEvent(MotionEvent event) {
      // Before delegation to gesture detector, handle ACTION_UP event
      // in order to release edge effect.
      if (event.getAction() == MotionEvent.ACTION_UP) {
        topEdgeEffect.onRelease();
        bottomEdgeEffect.onRelease();
        invalidate();
      }

      if (gestureDetector.onTouchEvent(event)) {
        return true;
      }

      float scrolledX = event.getX() + getScrollX();
      float scrolledY = event.getY() + getScrollY();
      switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
          findCandidateAndPress(scrolledX, scrolledY);
          scroller.stopScrolling();
          if (!topEdgeEffect.isFinished()) {
            topEdgeEffect.onRelease();
            invalidate();
          }
          if (!bottomEdgeEffect.isFinished()) {
            bottomEdgeEffect.onRelease();
            invalidate();
          }
          return true;
        case MotionEvent.ACTION_MOVE:
          if (pressedCandidate != null) {
            // Turn off highlighting if contact point gets out of the candidate.
            if (!candidateRect.contains(scrolledX, scrolledY)) {
              reset();
              invalidate();
            }
          }
          return true;
        case MotionEvent.ACTION_CANCEL:
          if (pressedCandidate != null) {
            reset();
            invalidate();
          }
          return true;
        case MotionEvent.ACTION_UP:
          if (pressedCandidate != null) {
            if (candidateRect.contains(scrolledX, scrolledY) && candidateSelectListener != null) {
              candidateSelectListener.onCandidateSelected(pressedCandidate, pressedRowIndex);
            }
            reset();
            invalidate();
          }
          return true;
      }
      return false;
    }
  }

  /**
   * Polymorphic behavior based on scroll orientation.
   */
  // TODO(hidehiko): rename OrientationTrait to OrientationTraits.
  interface OrientationTrait {
    /** @return scroll position of which direction corresponds to the orientation. */
    int getScrollPosition(View view);

    /** @return the projected value. */
    float projectVector(float x, float y);

    /** Scrolls to {@code position}. {@code position} is applied to corresponding axis. */
    void scrollTo(View view, int position);

    /** @return left or top position based on the orientation. */
    float getCandidatePosition(Row row, Span span);

    /** @return width or height based on the orientation. */
    float getCandidateLength(Row row, Span span);

    /** @return view's width or height based on the orientation. */
    int getViewLength(View view);

    /** @return the page size of the layout for the scroll orientation. */
    int getPageSize(CandidateLayouter layouter);

    /** @return the content size for the scroll orientation of the layout. 0 for absent. */
    float getContentSize(Optional<CandidateLayout> layout);
  }

  enum Orientation implements OrientationTrait {
    HORIZONTAL {
      @Override
      public int getScrollPosition(View view) {
        return view.getScrollX();
      }
      @Override
      public void scrollTo(View view, int position) {
        view.scrollTo(position, 0);
      }
      @Override
      public float getCandidatePosition(Row row, Span span) {
        return span.getLeft();
      }
      @Override
      public float getCandidateLength(Row row, Span span) {
        return span.getWidth();
      }
      @Override
      public int getViewLength(View view) {
        return view.getWidth();
      }
      @Override
      public float projectVector(float x, float y) {
        return x;
      }
      @Override
      public int getPageSize(CandidateLayouter layouter) {
        return Preconditions.checkNotNull(layouter).getPageWidth();
      }
      @Override
      public float getContentSize(Optional<CandidateLayout> layout) {
        return layout.isPresent() ? layout.get().getContentWidth() : 0;
      }
    },
    VERTICAL {
      @Override
      public int getScrollPosition(View view) {
        return view.getScrollY();
      }
      @Override
      public void scrollTo(View view, int position) {
        view.scrollTo(0, position);
      }
      @Override
      public float getCandidatePosition(Row row, Span span) {
        return row.getTop();
      }
      @Override
      public float getCandidateLength(Row row, Span span) {
        return row.getHeight();
      }
      @Override
      public int getViewLength(View view) {
        return view.getHeight();
          }
      @Override
      public float projectVector(float x, float y) {
        return y;
      }
      @Override
      public int getPageSize(CandidateLayouter layouter) {
        return Preconditions.checkNotNull(layouter).getPageHeight();
      }
      @Override
      public float getContentSize(Optional<CandidateLayout> layout) {
        return layout.isPresent() ? layout.get().getContentHeight() : 0;
      }
    };
  }

  private CandidateSelectListener candidateSelectListener;

  // Finally, we only need vertical scrolling.
  // TODO(hidehiko): Remove horizontal scrolling related codes.
  private final EdgeEffect topEdgeEffect = new EdgeEffect(getContext());
  private final EdgeEffect bottomEdgeEffect = new EdgeEffect(getContext());

  // The Scroller which manages the status of scrolling the view.
  // Default behavior of ScrollView does not suffice our UX design
  // so we introduced this Scroller.
  // TODO(matsuzakit): The parameter is TBD (needs UX study?).
  protected final SnapScroller scroller = new SnapScroller();
  // The CandidateLayouter which calculates the layout of candidate words.
  // This fields is not final but must be set in initialization in the subclasses.
  @VisibleForTesting CandidateLayouter layouter;
  // The calculated layout, created by this.layouter.
  protected CandidateLayout calculatedLayout;
  // The CandidateList which is currently shown on the view.
  protected CandidateList currentCandidateList;
  // The Y position where the last touch event occurs.
  float lastEventPosition;

  // No padding by default.
  private int horizontalPadding = 0;

  protected final CarrierEmojiRenderHelper carrierEmojiRenderHelper =
      new CarrierEmojiRenderHelper(this);
  protected final CandidateLayoutRenderer candidateLayoutRenderer =
      new CandidateLayoutRenderer();

  CandidateWordGestureDetector candidateWordGestureDetector =
      new CandidateWordGestureDetector(getContext());

  // Scroll orientation.
  private final OrientationTrait orientationTrait;

  protected final BackgroundDrawableFactory backgroundDrawableFactory =
      new BackgroundDrawableFactory(getResources());
  private DrawableType backgroundDrawableType = null;

  private final CandidateWindowAccessibilityDelegate accessibilityDelegate;

  CandidateWordView(Context context, OrientationTrait orientationFeature) {
    super(context);
    this.orientationTrait = orientationFeature;
  }

  CandidateWordView(Context context, AttributeSet attributeSet,
                    OrientationTrait orientationTrait) {
    super(context, attributeSet);
    this.orientationTrait = orientationTrait;
  }

  CandidateWordView(Context context, AttributeSet attributeSet, int defaultStyle,
                    OrientationTrait orientationTrait) {
    super(context, attributeSet, defaultStyle);
    this.orientationTrait = orientationTrait;
  }

  {
    accessibilityDelegate = new CandidateWindowAccessibilityDelegate(this);
    ViewCompat.setAccessibilityDelegate(this, accessibilityDelegate);
  }

  void reset() {
    calculatedLayout = null;
    currentCandidateList = null;
    candidateWordGestureDetector.reset();
  }

  void setCandidateSelectListener(CandidateSelectListener candidateSelectListener) {
    this.candidateSelectListener = candidateSelectListener;
  }

  CandidateLayouter getCandidateLayouter() {
    return layouter;
  }

  protected void setHorizontalPadding(int horizontalPadding) {
    this.horizontalPadding = horizontalPadding;
    updateLayouter();
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    int width = Math.max(right - left - horizontalPadding * 2, 0);
    int height = bottom - top;
    if (layouter != null && layouter.setViewSize(width, height)) {
      updateCalculatedLayout();
    }
    updateScroller();
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return candidateWordGestureDetector.onTouchEvent(event);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    carrierEmojiRenderHelper.onAttachedToWindow();
  }

  @Override
  protected void onDetachedFromWindow() {
    carrierEmojiRenderHelper.onDetachedFromWindow();
    super.onDetachedFromWindow();
  }

  public void setEmojiProviderType(EmojiProviderType providerType) {
    Preconditions.checkNotNull(providerType);
    carrierEmojiRenderHelper.setEmojiProviderType(providerType);
  }

  @Override
  public void draw(Canvas canvas) {
    super.draw(canvas);

    // Render edge effect.
    boolean postInvalidateIsNeeded = false;
    if (!topEdgeEffect.isFinished()) {
      int saveCount = canvas.save();
      try {
        canvas.translate(0, Math.min(0, getScrollY()));
        topEdgeEffect.setSize(getWidth(), getHeight());
        if (topEdgeEffect.draw(canvas)) {
          postInvalidateIsNeeded = true;
        }
      } finally {
        canvas.restoreToCount(saveCount);
      }
    }

    if (!bottomEdgeEffect.isFinished()) {
      int saveCount = canvas.save();
      try {
        int width = getWidth();
        int height = getHeight();
        canvas.translate(-width, getScrollY() + height);
        canvas.rotate(180, width, 0);
        bottomEdgeEffect.setSize(width, height);
        if (bottomEdgeEffect.draw(canvas)) {
          postInvalidateIsNeeded = true;
        }
      } finally {
        canvas.restoreToCount(saveCount);
      }
    }

    if (postInvalidateIsNeeded) {
      ViewCompat.postInvalidateOnAnimation(this);
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (calculatedLayout == null || currentCandidateList == null) {
      // No layout is available.
      return;
    }

    // Paint the candidates.
    int saveCount = canvas.save();
    try {
      canvas.translate(horizontalPadding, 0);
      CandidateWord pressedCandidate = candidateWordGestureDetector.getPressedCandidate();
      int pressedCandidateIndex = (pressedCandidate != null && pressedCandidate.hasIndex())
          ? pressedCandidate.getIndex() : -1;
      candidateLayoutRenderer.drawCandidateLayout(
          canvas, calculatedLayout, pressedCandidateIndex, carrierEmojiRenderHelper);
    } finally {
      canvas.restoreToCount(saveCount);
    }
  }

  @Override
  public final void computeScroll() {
    if (scroller.isScrolling()) {
      // If still scrolling, update the scroll position and invalidate the window.
      Optional<Float> optionalVelocity = scroller.computeScrollOffset();
      orientationTrait.scrollTo(this, scroller.getScrollPosition());
      if (optionalVelocity.isPresent()) {
        Float velocity = optionalVelocity.get();
        // The end of scrolling. Check edge effect.
        if (velocity < 0) {
          topEdgeEffect.onAbsorb(velocity.intValue());
          if (!bottomEdgeEffect.isFinished()) {
            bottomEdgeEffect.onRelease();
            invalidate();
          }
        } else if (velocity > 0) {
          bottomEdgeEffect.onAbsorb(velocity.intValue());
          if (!topEdgeEffect.isFinished()) {
            topEdgeEffect.onRelease();
            invalidate();
          }
        }
      }

      // This invalidation makes next scrolling.
      ViewCompat.postInvalidateOnAnimation(this);
    }
    super.computeScroll();
  }

  @VisibleForTesting int getUpdatedScrollPosition(Row row, Span span) {
    int scrollPosition = orientationTrait.getScrollPosition(this);
    float candidatePosition = orientationTrait.getCandidatePosition(row, span);
    float candidateLength = orientationTrait.getCandidateLength(row, span);
    int viewLength = orientationTrait.getViewLength(this);
    if (candidatePosition < scrollPosition ||
        candidatePosition + candidateLength > scrollPosition + viewLength) {
      return (int) candidatePosition;
    } else {
      return scrollPosition;
    }
  }

  /**
   * If focused candidate is invisible (including partial invisible),
   * update scroll position to see the candidate.
   */
  protected void updateScrollPositionBasedOnFocusedIndex() {
    int scrollPosition = 0;
    if (calculatedLayout != null && currentCandidateList != null) {
      int focusedIndex = currentCandidateList.getFocusedIndex();
      row_loop: for (Row row : calculatedLayout.getRowList()) {
        for (Span span : row.getSpanList()) {
          if (!span.getCandidateWord().isPresent()) {
            continue;
          }
          if (span.getCandidateWord().get().getIndex() == focusedIndex) {
            scrollPosition = getUpdatedScrollPosition(row, span);
            break row_loop;
          }
        }
      }
    }

    setScrollPosition(scrollPosition);
  }

  void setScrollPosition(int position) {
    scroller.scrollTo(position);
    orientationTrait.scrollTo(this, scroller.getScrollPosition());
    invalidate();
  }

  void update(CandidateList candidateList) {
    CandidateList previousCandidateList = currentCandidateList;
    currentCandidateList = candidateList;
    Optional<CandidateList> optionalCandidateList = Optional.fromNullable(candidateList);
    candidateLayoutRenderer.setCandidateList(optionalCandidateList);
    carrierEmojiRenderHelper.setCandidateList(optionalCandidateList);
    if (layouter != null && !equals(candidateList, previousCandidateList)) {
      updateCalculatedLayout();
    }
    updateScroller();
    invalidate();
  }

  private static boolean equals(CandidateList list1, CandidateList list2) {
    if (list1 == list2) {
      return true;
    }
    if (list1 == null || list2 == null) {
      return false;
    }

    return list1.getCandidatesList().equals(list2.getCandidatesList());
  }

  /**
   * Updates the layouter, and also updates the calculatedLayout based on the updated layouter.
   *
   * TODO(hidehiko): This method is remaining here to reduce a CL size smaller
   * in order to make refactoring step by step. This will be cleaned when CandidateWordView
   * is refactored.
   */
  protected final void updateLayouter() {
    updateCalculatedLayout();
    updateScroller();
  }

  /**
   * Updates the calculatedLayout if possible.
   */
  private void updateCalculatedLayout() {
    if (currentCandidateList == null || layouter == null) {
      calculatedLayout = null;
    } else {
      calculatedLayout = layouter.layout(currentCandidateList).orNull();
    }
    Optional<CandidateLayout> candidateLayout = Optional.fromNullable(calculatedLayout);
    accessibilityDelegate.setCandidateLayout(
        candidateLayout,
        (int) orientationTrait.getContentSize(candidateLayout),
        orientationTrait.getViewLength(this));
  }

  private void updateScroller() {
    if (calculatedLayout == null || layouter == null) {
      scroller.setPageSize(0);
      scroller.setContentSize(0);
    } else {
      int pageSize = orientationTrait.getPageSize(layouter);
      int contentSize =
          (int) orientationTrait.getContentSize(Optional.fromNullable(calculatedLayout));
      if (pageSize != 0) {
        // Ceil to align pages.
        contentSize = (contentSize + pageSize - 1) / pageSize * pageSize;
      }
      scroller.setPageSize(pageSize);
      scroller.setContentSize(contentSize);
    }
    scroller.setViewSize(orientationTrait.getViewLength(this));
  }

  public CandidateList getCandidateList() {
    return currentCandidateList;
  }

  protected void setSpanBackgroundDrawableType(DrawableType drawableType) {
    backgroundDrawableType = drawableType;
    resetSpanBackground();
  }

  private void resetSpanBackground() {
    Drawable drawable = (backgroundDrawableType != null)
        ? backgroundDrawableFactory.getDrawable(backgroundDrawableType) : null;
    candidateLayoutRenderer.setSpanBackgroundDrawable(Optional.fromNullable(drawable));
  }

  /**
   * Returns a Drawable which should be set as the view's background.
   */
  protected abstract Drawable getViewBackgroundDrawable(Skin skin);

  @SuppressWarnings("deprecation")
  void setSkin(Skin skin) {
    backgroundDrawableFactory.setSkin(Preconditions.checkNotNull(skin));
    resetSpanBackground();
    candidateLayoutRenderer.setSkin(skin);
    setBackgroundDrawable(
        getViewBackgroundDrawable(skin).getConstantState().newDrawable());
  }

  @Override
  public void trimMemory() {
    calculatedLayout = null;
    accessibilityDelegate.setCandidateLayout(Optional.<CandidateLayout>absent(), 0, 0);
    currentCandidateList = null;
  }

  @Override
  protected boolean dispatchHoverEvent(MotionEvent event) {
    if (AccessibilityUtil.isTouchExplorationEnabled(getContext())) {
      return accessibilityDelegate.dispatchHoverEvent(event);
    }
    return false;
  }

  @Override
  protected void onVisibilityChanged(View changedView, int visibility) {
    super.onVisibilityChanged(changedView, visibility);
    // If this view gets invisible, reset the internal state of the gesture detector.
    // Otherwise UP event, which is sent after this view being invisible, will cause
    // unexpected onCandidateSelected callback.
    if (visibility != View.VISIBLE) {
      candidateWordGestureDetector.reset();
    }
  }
}
